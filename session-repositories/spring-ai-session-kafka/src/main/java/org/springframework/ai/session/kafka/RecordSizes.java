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

import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;

/**
 * How large a record is once Kafka frames it: its key, value and headers, plus room for
 * the batch and record framing. Kafka refuses a record over {@code max.request.size} or
 * the topic's {@code max.message.bytes}; a store task that tries to send one fails, so
 * the store checks the size first, on the record it will really send.
 */
final class RecordSizes {

	/** Room for the framing of a batch of one record: a few dozen bytes in practice. */
	static final int FRAMING = 512;

	private RecordSizes() {
	}

	/** The framed size of a record with these key, value and headers. */
	static int of(@Nullable String key, byte[] value, Iterable<Header> headers) {
		int size = FRAMING + bytes(key) + value.length;
		for (Header header : headers) {
			size += bytes(header.key()) + ((header.value() != null) ? header.value().length : 0);
		}
		return size;
	}

	static int bytes(@Nullable String text) {
		return (text != null) ? text.getBytes(StandardCharsets.UTF_8).length : 0;
	}

}
