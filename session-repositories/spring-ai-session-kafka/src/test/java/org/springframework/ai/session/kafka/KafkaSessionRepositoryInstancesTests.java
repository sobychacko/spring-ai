/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.session.kafka;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.GroupListing;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Several repository instances on one set of topics: shared sessions, races between
 * instances, calls that span partitions, restarts and startup.
 */
class KafkaSessionRepositoryInstancesTests {

	@TempDir
	Path stateDirectory;

	private static EmbeddedKafkaKraftBroker broker;

	@BeforeAll
	static void startBroker() {
		broker = KafkaSessionTestSupport.startBroker();
	}

	@AfterAll
	static void stopBroker() {
		if (broker != null) {
			broker.destroy();
		}
	}

	@Test
	void instancesShareSessions() {
		String prefix = "shared-" + UUID.randomUUID();
		try (KafkaSessionRepository first = start(prefix, "first");
				KafkaSessionRepository second = start(prefix, "second")) {
			first.save(Session.builder().id("s-1").build());
			second.appendEvent(KafkaSessionTestSupport.userEvent("s-1", "from second"));
			first.appendEvent(KafkaSessionTestSupport.userEvent("s-1", "from first"));

			assertThat(texts(first, "s-1")).containsExactly("from second", "from first");
			assertThat(texts(second, "s-1")).containsExactly("from second", "from first");

			second.delete("s-1");
			assertThat(first.findById("s-1")).isNull();
		}
	}

	@Test
	void createRaceAcrossInstancesHasOneWinner() throws Exception {
		String prefix = "race-" + UUID.randomUUID();
		try (KafkaSessionRepository first = start(prefix, "first");
				KafkaSessionRepository second = start(prefix, "second")) {
			String id = UUID.randomUUID().toString();
			ExecutorService executor = Executors.newFixedThreadPool(8);
			try {
				CountDownLatch go = new CountDownLatch(1);
				List<Future<Boolean>> results = new ArrayList<>();
				for (int i = 0; i < 8; i++) {
					KafkaSessionRepository repository = (i % 2 == 0) ? first : second;
					results.add(executor.submit(() -> {
						go.await();
						return repository.saveIfAbsent(Session.builder().id(id).build());
					}));
				}
				go.countDown();
				int winners = 0;
				for (Future<Boolean> result : results) {
					winners += result.get(60, TimeUnit.SECONDS) ? 1 : 0;
				}
				assertThat(winners).isEqualTo(1);
			}
			finally {
				executor.shutdownNow();
			}
		}
	}

	@Test
	void freshInstanceRestoresStoresFromChangelogs() {
		String prefix = "restore-" + UUID.randomUUID();
		try (KafkaSessionRepository original = start(prefix, "original")) {
			original.save(Session.builder().id("kept").build());
			original.appendEvent(KafkaSessionTestSupport.userEvent("kept", "one"));
			original.appendEvent(KafkaSessionTestSupport.userEvent("kept", "two"));
			original.save(Session.builder().id("gone").build());
			original.appendEvent(KafkaSessionTestSupport.userEvent("gone", "deleted"));
			original.delete("gone");
		}

		// A new local state directory: everything comes back from the changelogs
		try (KafkaSessionRepository restored = start(prefix, "restored")) {
			assertThat(texts(restored, "kept")).containsExactly("one", "two");
			assertThat(restored.getEventVersion("kept")).isEqualTo(2);
			assertThat(restored.findById("gone")).isNull();
			assertThat(restored.findEvents("gone", EventFilter.all())).isEmpty();
		}
	}

	@Test
	void versionCheckRaceAcrossInstancesHasOneWinner() throws Exception {
		// Two compactions of one session, one per instance, at the same version: what
		// DefaultSessionService.compact relies on to never land both
		String prefix = "version-" + UUID.randomUUID();
		try (KafkaSessionRepository first = start(prefix, "first");
				KafkaSessionRepository second = start(prefix, "second")) {
			first.save(Session.builder().id("s").build());
			ExecutorService executor = Executors.newFixedThreadPool(2);
			try {
				int rounds = 10;
				for (int round = 0; round < rounds; round++) {
					long version = first.getEventVersion("s");
					CountDownLatch go = new CountDownLatch(1);
					Future<Boolean> fromFirst = executor.submit(() -> {
						go.await();
						return first.appendEvents("s", List.of(KafkaSessionTestSupport.userEvent("s", "first"),
								KafkaSessionTestSupport.userEvent("s", "first")), version);
					});
					Future<Boolean> fromSecond = executor.submit(() -> {
						go.await();
						return second.appendEvents("s", List.of(KafkaSessionTestSupport.userEvent("s", "second"),
								KafkaSessionTestSupport.userEvent("s", "second")), version);
					});
					go.countDown();
					assertThat(fromFirst.get(60, TimeUnit.SECONDS) ^ fromSecond.get(60, TimeUnit.SECONDS))
						.as("exactly one batch of round %d wins", round)
						.isTrue();
				}
				assertThat(first.getEventVersion("s")).isEqualTo(rounds);
				assertThat(second.findEvents("s", EventFilter.all())).hasSize(rounds * 2);
			}
			finally {
				executor.shutdownNow();
			}
		}
	}

	@Test
	void callsAcrossPartitionsCombineEveryInstancesSessions() {
		String prefix = "across-" + UUID.randomUUID();
		try (KafkaSessionRepository first = start(prefix, "first");
				KafkaSessionRepository second = start(prefix, "second")) {
			String principal = "user-" + UUID.randomUUID();
			Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
			List<String> ids = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				String id = UUID.randomUUID().toString();
				ids.add(id);
				// Half of them expired; every second one saved through the other instance
				(i % 2 == 0 ? first : second).save(Session.builder()
					.id(id)
					.createdAt(t0.plusSeconds(i))
					.expiresAt((i < 4) ? Instant.now().minusSeconds(60) : null)
					.metadata(SessionAttributes.PRINCIPAL_NAME, principal)
					.build());
			}
			// The sessions sit on partitions that both instances own
			assertThat(ids.stream().map(KafkaSessionTestSupport::partitionOf).distinct().count()).isGreaterThan(1);

			assertThat(second.findByMetadata(SessionAttributes.PRINCIPAL_NAME, principal)).extracting(Session::id)
				.containsExactlyElementsOf(ids);
			assertThat(first.deleteExpiredSessions(Instant.now())).isEqualTo(4);
			assertThat(second.findByMetadata(SessionAttributes.PRINCIPAL_NAME, principal)).extracting(Session::id)
				.containsExactlyElementsOf(ids.subList(4, 8));
		}
	}

	@Test
	void restartOnTheSameDiskKeepsTheSessions() {
		String prefix = "same-disk-" + UUID.randomUUID();
		try (KafkaSessionRepository original = start(prefix, "instance")) {
			original.save(Session.builder().id("s").build());
			original.appendEvent(KafkaSessionTestSupport.userEvent("s", "before restart"));
		}
		// Same state directory: the stores open from disk and catch up from the changelog
		try (KafkaSessionRepository restarted = start(prefix, "instance")) {
			restarted.appendEvent(KafkaSessionTestSupport.userEvent("s", "after restart"));
			assertThat(texts(restarted, "s")).containsExactly("before restart", "after restart");
			assertThat(restarted.getEventVersion("s")).isEqualTo(2);
		}
	}

	@Test
	void instancesStartingTogetherCreateTheTopicsOnce() throws Exception {
		String prefix = "together-" + UUID.randomUUID();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<KafkaSessionRepository> first = executor.submit(() -> start(prefix, "first"));
			Future<KafkaSessionRepository> second = executor.submit(() -> start(prefix, "second"));
			try (KafkaSessionRepository a = first.get(60, TimeUnit.SECONDS);
					KafkaSessionRepository b = second.get(60, TimeUnit.SECONDS)) {
				a.save(Session.builder().id("s").build());
				b.appendEvent(KafkaSessionTestSupport.userEvent("s", "shared"));
				assertThat(texts(a, "s")).containsExactly("shared");
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void replyListenersLeaveNoConsumerGroupBehind() throws Exception {
		String prefix = "groups-" + UUID.randomUUID();
		try (KafkaSessionRepository repository = start(prefix, "only")) {
			repository.save(Session.builder().id("s").build());
		}
		try (Admin admin = Admin
			.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
			// Only the Streams application's group: the reply listener assigns its
			// partitions and commits nothing
			assertThat(admin.listGroups().all().get(30, TimeUnit.SECONDS)).extracting(GroupListing::groupId)
				.filteredOn(id -> id.startsWith(prefix))
				.containsExactly(prefix);
		}
	}

	private KafkaSessionRepository start(String prefix, String instance) {
		KafkaSessionRepository repository = KafkaSessionTestSupport
			.repository(broker, prefix, this.stateDirectory.resolve(instance))
			.build();
		repository.start();
		return repository;
	}

	private static List<String> texts(KafkaSessionRepository repository, String sessionId) {
		return repository.findEvents(sessionId, EventFilter.all())
			.stream()
			.map(event -> event.getMessage().getText())
			.toList();
	}

}
