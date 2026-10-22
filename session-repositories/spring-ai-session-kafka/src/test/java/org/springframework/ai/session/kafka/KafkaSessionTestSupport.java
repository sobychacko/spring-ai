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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.utils.Utils;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.SessionEvent;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

/**
 * An embedded single-node broker that supports exactly-once, repository defaults that fit
 * it, and helpers to read a topic directly.
 */
final class KafkaSessionTestSupport {

	static final int PARTITIONS = 4;

	private KafkaSessionTestSupport() {
	}

	static EmbeddedKafkaKraftBroker startBroker() {
		return startBroker(Map.of());
	}

	static EmbeddedKafkaKraftBroker startBroker(Map<String, String> extraProperties) {
		EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 1);
		Map<String, String> properties = new HashMap<>(Map.of("auto.create.topics.enable", "false",
				"transaction.state.log.replication.factor", "1", "transaction.state.log.min.isr", "1"));
		properties.putAll(extraProperties);
		broker.brokerProperties(properties);
		broker.afterPropertiesSet();
		return broker;
	}

	static KafkaSessionRepository.Builder repository(EmbeddedKafkaKraftBroker broker, String topicPrefix,
			Path stateDirectory) {
		return KafkaSessionRepository.builder()
			.bootstrapServers(broker.getBrokersAsString())
			.topicPrefix(topicPrefix)
			.partitions(PARTITIONS)
			.replicationFactor((short) 1)
			.standbyReplicas(0)
			.stateDirectory(stateDirectory)
			.operationTimeout(Duration.ofSeconds(30));
	}

	/** A user message event of the session. */
	static SessionEvent userEvent(String sessionId, String text) {
		return SessionEvent.builder().sessionId(sessionId).message(new UserMessage(text)).build();
	}

	/**
	 * The partition a request keyed by this session id goes to, as the producer picks it.
	 */
	static int partitionOf(String sessionId) {
		return Utils.toPositive(Utils.murmur2(sessionId.getBytes(StandardCharsets.UTF_8))) % PARTITIONS;
	}

	/** A new session id that lands on the same partition as the given one. */
	static String sessionOnSamePartition(String sessionId) {
		int partition = partitionOf(sessionId);
		while (true) {
			String candidate = UUID.randomUUID().toString();
			if (partitionOf(candidate) == partition) {
				return candidate;
			}
		}
	}

	/**
	 * Every record of the topic, from the beginning, read with the given isolation level.
	 */
	static List<ConsumerRecord<String, byte[]>> readAll(EmbeddedKafkaKraftBroker broker, String topic,
			String isolationLevel) {
		Map<String, Object> properties = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
				ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel, ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties, new StringDeserializer(),
				new ByteArrayDeserializer())) {
			List<TopicPartition> partitions = consumer.partitionsFor(topic)
				.stream()
				.map(info -> new TopicPartition(topic, info.partition()))
				.toList();
			consumer.assign(partitions);
			consumer.seekToBeginning(partitions);
			Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
			List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
			long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
			while (partitions.stream().anyMatch(p -> consumer.position(p) < end.get(p))
					&& System.nanoTime() < deadline) {
				consumer.poll(Duration.ofMillis(200)).forEach(records::add);
			}
			return records;
		}
	}

}
