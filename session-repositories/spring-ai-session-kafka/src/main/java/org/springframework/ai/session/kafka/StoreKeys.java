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

import java.util.Locale;

import org.apache.kafka.common.serialization.StringSerializer;

/**
 * The keys of the events and event-ids stores. Every key starts with the session's
 * prefix, its length, the id and a colon, so a prefix scan returns one session's entries
 * and never the start of a longer id. An event key ends with a zero-padded sequence
 * number, so the scan returns the events in append order.
 */
final class StoreKeys {

	/** Serializes a prefix for {@code prefixScan}, as the store's key serde does. */
	static final StringSerializer PREFIX_SERIALIZER = new StringSerializer();

	private StoreKeys() {
	}

	static String sessionPrefix(String sessionId) {
		return sessionId.length() + ":" + sessionId + ":";
	}

	static String event(String sessionId, long seq) {
		return sessionPrefix(sessionId) + String.format(Locale.ROOT, "%019d", seq);
	}

	static String eventId(String sessionId, String eventId) {
		return sessionPrefix(sessionId) + eventId;
	}

}
