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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionEvent;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Record size limits, measured on the records Kafka will really send: a call over the
 * limit fails clearly, and never after it changed anything.
 */
class KafkaSessionRepositoryLimitsTests {

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
	void eventLargerThanBrokerDefaultRecordIsStoredAndRead() {
		// The broker default is about 1 MB, for the changelogs as for any topic
		String text = "x".repeat(1_500_000);
		try (KafkaSessionRepository repository = start(
				KafkaSessionTestSupport.repository(broker, "large-" + UUID.randomUUID(), this.stateDirectory))) {
			repository.save(Session.builder().id("s").build());
			repository.appendEvent(KafkaSessionTestSupport.userEvent("s", text));

			assertThat(repository.findEvents("s", EventFilter.all())).singleElement()
				.satisfies(event -> assertThat(event.getMessage().getText()).hasSize(text.length()));
		}
	}

	@Test
	void tooLargeReplyIsRejectedAndStoreKeepsServing() {
		try (KafkaSessionRepository repository = start(
				KafkaSessionTestSupport.repository(broker, "reply-" + UUID.randomUUID(), this.stateDirectory)
					.maxMessageBytes(64 * 1024))) {
			repository.save(Session.builder().id("s").build());
			// Each batch fits a request; together they are too large for one reply
			for (int b = 0; b < 3; b++) {
				List<SessionEvent> batch = new ArrayList<>();
				for (int i = 0; i < 20; i++) {
					batch.add(KafkaSessionTestSupport.userEvent("s", "x".repeat(1000)));
				}
				assertThat(repository.appendEvents("s", batch, b)).isTrue();
			}

			assertThatIllegalStateException().isThrownBy(() -> repository.findEvents("s", EventFilter.all()))
				.withMessageContaining("over the limit");

			assertThat(repository.findEvents("s", EventFilter.lastN(5))).hasSize(5);
			repository.appendEvent(KafkaSessionTestSupport.userEvent("s", "after"));
			assertThat(repository.getEventVersion("s")).isEqualTo(4);
		}
	}

	@Test
	void tooLargeRequestIsRejectedBeforeSending() {
		try (KafkaSessionRepository repository = start(
				KafkaSessionTestSupport.repository(broker, "request-" + UUID.randomUUID(), this.stateDirectory)
					.maxMessageBytes(64 * 1024))) {
			repository.save(Session.builder().id("s").build());

			assertThatIllegalArgumentException()
				.isThrownBy(() -> repository.appendEvent(KafkaSessionTestSupport.userEvent("s", "x".repeat(100_000))))
				.withMessageContaining("over the limit");
			assertThat(repository.getEventVersion("s")).isZero();
		}
	}

	@Test
	void theSessionIdCountsTowardsTheLimit() {
		// The reply carries the request's key: a long id must not let a reply that fits
		// on its own become a record the broker refuses
		String id = "s-" + "x".repeat(10_000);
		try (KafkaSessionRepository repository = start(
				KafkaSessionTestSupport.repository(broker, "long-id-" + UUID.randomUUID(), this.stateDirectory)
					.maxMessageBytes(64 * 1024)
					.operationTimeout(Duration.ofSeconds(5)))) {
			repository.save(Session.builder().id(id).build());
			boolean refused = false;
			for (int i = 0; i < 40 && !refused; i++) {
				repository.appendEvent(KafkaSessionTestSupport.userEvent(id, "x".repeat(500)));
				try {
					repository.findEvents(id, EventFilter.all());
				}
				catch (IllegalStateException ex) {
					assertThat(ex).hasMessageContaining("over the limit");
					refused = true;
				}
			}

			assertThat(refused).isTrue();
			assertThat(repository.isHealthy()).isTrue();
			assertThat(repository.findEvents(id, EventFilter.lastN(1))).hasSize(1);
		}
	}

	@Test
	void aRefusedSaveWritesNothing() {
		try (KafkaSessionRepository repository = start(
				KafkaSessionTestSupport.repository(broker, "save-size-" + UUID.randomUUID(), this.stateDirectory)
					.maxMessageBytes(64 * 1024))) {
			// Find where saves start being refused, then walk the
			// kilobyte below it: there, a reply larger than its
			// request would be refused after the write
			int refusedFrom = 0;
			for (int size = 50_000; size <= 70_000 && refusedFrom == 0; size += 1000) {
				if (!saveAndCheck(repository, size)) {
					refusedFrom = size;
				}
			}
			assertThat(refusedFrom).isPositive();
			for (int size = refusedFrom - 1000; size < refusedFrom; size += 5) {
				saveAndCheck(repository, size);
			}
		}
	}

	/**
	 * Saves a session with this much metadata and checks the outcome against the store: a
	 * save that failed left no session, one that succeeded did.
	 * @return whether the save succeeded
	 */
	private static boolean saveAndCheck(KafkaSessionRepository repository, int size) {
		String id = UUID.randomUUID().toString();
		boolean saved;
		try {
			repository.save(Session.builder().id(id).metadata("blob", "x".repeat(size)).build());
			saved = true;
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			saved = false;
		}
		assertThat(repository.saveIfAbsent(Session.builder().id(id).build()))
			.as("a save of %d bytes of metadata that %s", size, saved ? "succeeded" : "was refused")
			.isEqualTo(!saved);
		return saved;
	}

	private static KafkaSessionRepository start(KafkaSessionRepository.Builder builder) {
		KafkaSessionRepository repository = builder.build();
		repository.start();
		return repository;
	}

}
