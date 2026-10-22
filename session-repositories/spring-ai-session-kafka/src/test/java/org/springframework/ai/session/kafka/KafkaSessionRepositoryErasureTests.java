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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.Session;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A deleted session's content leaves Kafka: from the changelogs by compaction, and from
 * the requests and replies topics by retention. The content retention is two seconds and
 * the broker runs its cleaner and retention checks every second. The partition keeps
 * receiving writes, which closes the segments that hold the content; Kafka never compacts
 * or deletes an open segment, so on a partition that receives nothing more the content
 * stays until the next write.
 */
class KafkaSessionRepositoryErasureTests {

	@TempDir
	Path stateDirectory;

	private static EmbeddedKafkaKraftBroker broker;

	@BeforeAll
	static void startBroker() {
		broker = KafkaSessionTestSupport
			.startBroker(Map.of("log.cleaner.backoff.ms", "500", "log.retention.check.interval.ms", "1000"));
	}

	@AfterAll
	static void stopBroker() {
		if (broker != null) {
			broker.destroy();
		}
	}

	@Test
	void deletedContentLeavesEveryTopicWhileThePartitionIsWritten() {
		String prefix = "erasure-" + UUID.randomUUID();
		String secret = "secret-" + UUID.randomUUID();
		String deleted = UUID.randomUUID().toString();
		// A session on the same partitions keeps writing, so the segments that hold the
		// secret close; compaction relies on max.compaction.lag.ms, not a low dirty ratio
		String neighbour = KafkaSessionTestSupport.sessionOnSamePartition(deleted);
		try (KafkaSessionRepository repository = KafkaSessionTestSupport.repository(broker, prefix, this.stateDirectory)
			.contentRetention(Duration.ofSeconds(2))
			.build()) {
			repository.start();
			repository.save(Session.builder().id(deleted).build());
			repository.appendEvent(KafkaSessionTestSupport.userEvent(deleted, secret));
			// Read it back, so a reply holds it too
			assertThat(repository.findEvents(deleted, EventFilter.all())).hasSize(1);
			repository.save(Session.builder().id(neighbour).build());
			assertThat(holding(secret, prefix)).contains(prefix + ".requests", prefix + ".replies",
					prefix + "-" + SessionProcessor.EVENTS + "-changelog");

			repository.delete(deleted);

			await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
				repository.appendEvent(KafkaSessionTestSupport.userEvent(neighbour, "tick"));
				assertThat(holding(secret, prefix)).isEmpty();
			});
		}
	}

	/** The topics that still hold a record with the text, read uncommitted. */
	private static List<String> holding(String text, String prefix) {
		return List
			.of(prefix + ".requests", prefix + ".replies", prefix + "-" + SessionProcessor.EVENTS + "-changelog",
					prefix + "-" + SessionProcessor.SESSIONS + "-changelog")
			.stream()
			.filter(topic -> KafkaSessionTestSupport.readAll(broker, topic, "read_uncommitted")
				.stream()
				.anyMatch(record -> record.value() != null
						&& new String(record.value(), StandardCharsets.UTF_8).contains(text)))
			.toList();
	}

}
