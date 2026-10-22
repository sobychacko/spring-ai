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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RoundRobinPartitioner;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * What happens when things go wrong: a task that fails mid-request, requests the task
 * can't read, client settings and topics that don't fit, and a start that fails. Failures
 * are injected through the repository's package-private test hooks.
 */
class KafkaSessionRepositoryFailureTests {

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
	void ownerFailingBeforeCommitIsRolledBackAndTheTakeoverAnswersOnce() {
		String prefix = "failover-" + UUID.randomUUID();
		String sessionId = UUID.randomUUID().toString();
		AtomicBoolean armed = new AtomicBoolean();
		// Fails whichever instance owns the session, once, after the reply is forwarded
		// and before the transaction commits
		Consumer<@Nullable String> failOnce = key -> {
			if (sessionId.equals(key) && armed.compareAndSet(true, false)) {
				throw new IllegalStateException("Injected failure between forward and commit");
			}
		};
		try (KafkaSessionRepository first = start(prefix, "first", failOnce);
				KafkaSessionRepository second = start(prefix, "second", failOnce)) {
			first.save(Session.builder().id(sessionId).build());
			armed.set(true);

			boolean appended = first.appendEvents(sessionId,
					List.of(KafkaSessionTestSupport.userEvent(sessionId, "one"),
							KafkaSessionTestSupport.userEvent(sessionId, "two"),
							KafkaSessionTestSupport.userEvent(sessionId, "three")),
					0);

			// The failure happened, and the attempt it aborted left nothing behind: had
			// its writes survived, the retry would have found version 1 and said false
			assertThat(armed).isFalse();
			assertThat(appended).isTrue();
			// The aborted attempt's reply reached the broker but no caller: committed
			// readers see the save and the batch, uncommitted ones the aborted reply too
			String replies = prefix + ".replies";
			assertThat(repliesFor(replies, sessionId, "read_committed")).isEqualTo(2);
			assertThat(repliesFor(replies, sessionId, "read_uncommitted")).isEqualTo(3);
			// The instance that failed says so; the other one carries on
			assertThat(first.isHealthy() ^ second.isHealthy()).isTrue();
			assertThat(second.getEventVersion(sessionId)).isEqualTo(1);
			assertThat(second.findEvents(sessionId, EventFilter.all())).extracting(e -> e.getMessage().getText())
				.containsExactly("one", "two", "three");
		}
	}

	@Test
	void instanceWhoseEngineStoppedReportsUnhealthy() {
		String prefix = "stopped-" + UUID.randomUUID();
		AtomicBoolean armed = new AtomicBoolean();
		KafkaSessionRepository repository = KafkaSessionTestSupport
			.repository(broker, prefix, this.stateDirectory.resolve("only"))
			.operationTimeout(Duration.ofSeconds(5))
			.afterForward(key -> {
				if ("s".equals(key) && armed.compareAndSet(true, false)) {
					throw new IllegalStateException("Injected failure");
				}
			})
			.build();
		try (repository) {
			repository.start();
			repository.save(Session.builder().id("s").build());
			assertThat(repository.isHealthy()).isTrue();
			armed.set(true);

			// One instance only: nobody takes the partition over, so the call is not
			// answered, and the instance says it needs a restart
			assertThatIllegalStateException()
				.isThrownBy(() -> repository.appendEvent(KafkaSessionTestSupport.userEvent("s", "lost")))
				.withMessageContaining("outcome is unknown");
			assertThat(repository.isHealthy()).isFalse();
		}
	}

	@Test
	void unreadableRequestsAreSkippedAndTheStoreKeepsServing() {
		String prefix = "unreadable-" + UUID.randomUUID();
		try (KafkaSessionRepository repository = start(prefix, "only")) {
			repository.save(Session.builder().id("s").build());
			// Not JSON, the JSON null, and valid JSON that lacks what the operation needs
			List<String> unreadable = List.of("not a request", "null", "{\"op\":\"SAVE\"}",
					"{\"op\":\"FIND_EVENTS\",\"sessionId\":\"s\"}", "{\"op\":\"APPEND\",\"event\":{\"id\":\"e\"}}",
					"{\"op\":\"NO_SUCH_OPERATION\"}");
			try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(
					Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()),
					new StringSerializer(), new ByteArraySerializer())) {
				for (String request : unreadable) {
					producer.send(new ProducerRecord<>(prefix + ".requests", KafkaSessionTestSupport.partitionOf("s"),
							"s", request.getBytes(StandardCharsets.UTF_8)));
				}
			}

			// Queued behind them on the same partition: answered
			// only if the task skipped every one
			repository.appendEvent(KafkaSessionTestSupport.userEvent("s", "after"));

			assertThat(repository.findEvents("s", EventFilter.all())).extracting(e -> e.getMessage().getText())
				.containsExactly("after");
			assertThat(repository.isHealthy()).isTrue();
		}
	}

	@Test
	void producerPartitionerSettingsCannotSplitASession() {
		String prefix = "partitioner-" + UUID.randomUUID();
		// A round-robin partitioner would spread one session's calls over every task
		KafkaSessionRepository repository = KafkaSessionTestSupport
			.repository(broker, prefix, this.stateDirectory.resolve("only"))
			.kafkaProperties(Map.of(ProducerConfig.PARTITIONER_CLASS_CONFIG, RoundRobinPartitioner.class.getName(),
					ProducerConfig.PARTITIONER_IGNORE_KEYS_CONFIG, true))
			.build();
		try (repository) {
			repository.start();
			assertThat(repository.saveIfAbsent(Session.builder().id("s").build())).isTrue();
			for (int i = 0; i < KafkaSessionTestSupport.PARTITIONS * 2; i++) {
				assertThat(repository.saveIfAbsent(Session.builder().id("s").build())).isFalse();
				repository.appendEvent(KafkaSessionTestSupport.userEvent("s", "message " + i));
				assertThat(repository.getEventVersion("s")).isEqualTo(i + 1);
			}
		}
	}

	@Test
	void failedStartLeavesNothingRunning() {
		String prefix = "failed-start-" + UUID.randomUUID();
		// Fail the start once the reply listener and Streams are both running
		KafkaSessionRepository repository = KafkaSessionTestSupport
			.repository(broker, prefix, this.stateDirectory.resolve("failing"))
			.afterComponentsStarted(() -> {
				throw new IllegalStateException("Injected start failure");
			})
			.build();
		try (repository) {
			assertThatIllegalStateException().isThrownBy(repository::start).withMessage("Injected start failure");
			assertThat(repository.isRunning()).isFalse();
			assertThat(repository.anyComponentRunning()).isFalse();
		}
		// Nothing holds the partitions: a new instance gets them all and answers
		try (KafkaSessionRepository next = start(prefix, "next")) {
			next.save(Session.builder().id("s").build());
			assertThat(next.findById("s")).isNotNull();
		}
	}

	@Test
	void existingChangelogThatDeletesStopsStartup() throws Exception {
		String prefix = "changelog-policy-" + UUID.randomUUID();
		String changelog = prefix + "-" + SessionProcessor.EVENTS + "-changelog";
		try (Admin admin = Admin
			.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
			admin.createTopics(List.of(new NewTopic(changelog, KafkaSessionTestSupport.PARTITIONS, (short) 1)
				// Right in every way but the cleanup policy
				.configs(Map.of(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE,
						TopicConfig.MAX_MESSAGE_BYTES_CONFIG, Integer.toString(8 * 1024 * 1024)))))
				.all()
				.get(30, TimeUnit.SECONDS);
		}
		KafkaSessionRepository repository = KafkaSessionTestSupport
			.repository(broker, prefix, this.stateDirectory.resolve("only"))
			.build();
		try (repository) {
			assertThatIllegalStateException().isThrownBy(repository::start)
				.withMessageContaining(changelog)
				.withMessageContaining("cleanup.policy=delete");
			assertThat(repository.anyComponentRunning()).isFalse();
		}
	}

	@Test
	void existingTopicWithASmallerRecordLimitStopsStartup() throws Exception {
		String prefix = "record-limit-" + UUID.randomUUID();
		try (Admin admin = Admin
			.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
			admin.createTopics(List.of(new NewTopic(prefix + ".replies", KafkaSessionTestSupport.PARTITIONS, (short) 1)
				.configs(Map.of(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, "65536")))).all().get(30, TimeUnit.SECONDS);
		}
		KafkaSessionRepository repository = KafkaSessionTestSupport
			.repository(broker, prefix, this.stateDirectory.resolve("only"))
			.build();
		try (repository) {
			assertThatIllegalStateException().isThrownBy(repository::start)
				.withMessageContaining(prefix + ".replies")
				.withMessageContaining("65536");
		}
	}

	@Test
	void topicsWithAnotherPartitionCountStopStartup() {
		String prefix = "partitions-" + UUID.randomUUID();
		try (KafkaSessionRepository existing = start(prefix, "existing")) {
			try (KafkaSessionRepository other = KafkaSessionTestSupport
				.repository(broker, prefix, this.stateDirectory.resolve("other"))
				.partitions(KafkaSessionTestSupport.PARTITIONS + 2)
				.build()) {
				assertThatIllegalStateException().isThrownBy(other::start)
					.withMessageContaining("has " + KafkaSessionTestSupport.PARTITIONS + " partitions");
			}
		}
	}

	private KafkaSessionRepository start(String prefix, String instance) {
		return start(prefix, instance, key -> {
		});
	}

	private KafkaSessionRepository start(String prefix, String instance, Consumer<@Nullable String> afterForward) {
		KafkaSessionRepository repository = KafkaSessionTestSupport
			.repository(broker, prefix, this.stateDirectory.resolve(instance))
			// Notice a failed instance within the call timeout
			.kafkaProperties(Map.of("session.timeout.ms", 6000, "heartbeat.interval.ms", 1000))
			.afterForward(afterForward)
			.build();
		repository.start();
		return repository;
	}

	private static long repliesFor(String topic, String sessionId, String isolationLevel) {
		return KafkaSessionTestSupport.readAll(broker, topic, isolationLevel)
			.stream()
			.filter(record -> sessionId.equals(record.key()))
			.count();
	}

}
