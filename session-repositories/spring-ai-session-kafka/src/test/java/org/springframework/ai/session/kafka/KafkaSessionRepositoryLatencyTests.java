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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.support.SessionEventCodec;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/**
 * Measures what a call costs on an embedded broker: single calls, an advisor-like turn
 * through {@link DefaultSessionService}, concurrent throughput, and the size of a full
 * history reply. Prints the numbers; asserts nothing. Run with
 * {@code -Dkafka.session.latency=true}.
 */
@EnabledIfSystemProperty(named = "kafka.session.latency", matches = "true")
class KafkaSessionRepositoryLatencyTests {

	@TempDir
	static Path stateDirectory;

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
	void callLatencyAtTwoCommitIntervals() throws Exception {
		for (Duration interval : List.of(Duration.ofMillis(20), Duration.ofMillis(100))) {
			try (KafkaSessionRepository repository = start("latency-" + interval.toMillis(), interval)) {
				measure(repository, interval);
			}
		}
	}

	@Test
	void fullHistoryReplySize() {
		try (KafkaSessionRepository repository = start("size", Duration.ofMillis(20))) {
			SessionEventCodec codec = new SessionEventCodec();
			JsonMapper mapper = JsonMapper.builder().build();
			String text = "x".repeat(1000);
			for (int events : List.of(1_000, 5_000)) {
				String id = UUID.randomUUID().toString();
				repository.save(Session.builder().id(id).build());
				for (int i = 0; i < events; i += 100) {
					List<SessionEvent> batch = new ArrayList<>();
					for (int j = 0; j < 100; j++) {
						batch.add(KafkaSessionTestSupport.userEvent(id, text));
					}
					repository.appendEvents(id, batch, repository.getEventVersion(id));
				}
				long start = System.nanoTime();
				try {
					List<SessionEvent> all = repository.findEvents(id, EventFilter.all());
					double millis = (System.nanoTime() - start) / 1e6;
					int bytes = mapper.writeValueAsBytes(SessionReply
						.ofEvents(all.stream().map(event -> EventValue.of(event, codec)).toList())).length;
					System.out.printf(Locale.ROOT,
							"full history: %5d events of 1 KB -> reply %,d bytes, read in %.0f ms%n", events, bytes,
							millis);
				}
				catch (IllegalStateException ex) {
					System.out.printf(Locale.ROOT, "full history: %5d events of 1 KB -> rejected: %s%n", events,
							ex.getMessage());
				}
				// The store keeps serving after a rejected read
				System.out.printf(Locale.ROOT, "full history: %5d events, then lastN(20) -> %d events%n", events,
						repository.findEvents(id, EventFilter.lastN(20)).size());
			}
		}
	}

	private static void measure(KafkaSessionRepository repository, Duration interval) throws Exception {
		SessionService service = DefaultSessionService.builder().sessionRepository(repository).build();
		String id = UUID.randomUUID().toString();
		repository.save(Session.builder().id(id).build());
		for (int i = 0; i < 50; i++) {
			repository.appendEvent(KafkaSessionTestSupport.userEvent(id, "warm-up " + i));
		}
		String viewSession = UUID.randomUUID().toString();
		repository.save(Session.builder().id(viewSession).build());
		for (int i = 0; i < 20; i++) {
			repository.appendEvent(KafkaSessionTestSupport.userEvent(viewSession, "turn " + i));
		}

		print(interval, "appendEvent",
				time(200, () -> repository.appendEvent(KafkaSessionTestSupport.userEvent(id, "message"))));
		print(interval, "getEventVersion", time(200, () -> repository.getEventVersion(id)));
		print(interval, "findEvents(active, 20 events)",
				time(200, () -> repository.findEvents(viewSession, EventFilter.active())));

		// What an advisor does on a turn: find the session, read
		// the prompt history, store the question and the answer
		String conversation = UUID.randomUUID().toString();
		print(interval, "turn (find, read, 2 appends)", time(50, () -> {
			service.findOrCreate(CreateSessionRequest.builder().id(conversation).build());
			service.getActiveMessages(conversation);
			service.appendMessage(conversation, new UserMessage("question"));
			service.appendMessage(conversation, new AssistantMessage("answer"));
		}));

		int threads = 16;
		int perThread = 50;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Future<?>> futures = new ArrayList<>();
			long start = System.nanoTime();
			for (int t = 0; t < threads; t++) {
				futures.add(executor.submit(() -> {
					String session = UUID.randomUUID().toString();
					repository.save(Session.builder().id(session).build());
					for (int i = 0; i < perThread; i++) {
						repository.appendEvent(KafkaSessionTestSupport.userEvent(session, "message " + i));
					}
				}));
			}
			for (Future<?> future : futures) {
				future.get();
			}
			double seconds = (System.nanoTime() - start) / 1e9;
			System.out.printf(Locale.ROOT, "commit %3d ms | %-32s %,.0f calls/s (%d threads)%n", interval.toMillis(),
					"concurrent appends", threads * (perThread + 1) / seconds, threads);
		}
		finally {
			executor.shutdownNow();
		}
	}

	private static double[] time(int calls, Runnable call) {
		double[] millis = new double[calls];
		for (int i = 0; i < calls; i++) {
			long start = System.nanoTime();
			call.run();
			millis[i] = (System.nanoTime() - start) / 1e6;
		}
		Arrays.sort(millis);
		return millis;
	}

	private static void print(Duration interval, String label, double[] sorted) {
		System.out.printf(Locale.ROOT, "commit %3d ms | %-32s p50 %6.1f ms  p95 %6.1f ms  max %6.1f ms%n",
				interval.toMillis(), label, sorted[sorted.length / 2], sorted[(int) (sorted.length * 0.95)],
				sorted[sorted.length - 1]);
	}

	private static KafkaSessionRepository start(String prefix, Duration commitInterval) {
		KafkaSessionRepository repository = KafkaSessionTestSupport
			.repository(broker, prefix + "-" + UUID.randomUUID(), stateDirectory.resolve(prefix))
			.commitInterval(commitInterval)
			.build();
		repository.start();
		return repository;
	}

}
