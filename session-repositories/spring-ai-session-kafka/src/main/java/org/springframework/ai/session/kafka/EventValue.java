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

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.support.SessionEventCodec;
import org.springframework.ai.session.support.SessionEventCodec.EncodedEvent;
import org.springframework.util.Assert;

/**
 * A {@link SessionEvent} as it travels in requests and replies and sits in the events
 * store: the four fields of {@link SessionEventCodec}, plus identity, timestamp and the
 * metadata as JSON.
 *
 * @param id the event id
 * @param sessionId the session the event belongs to
 * @param timestamp when the event was recorded
 * @param type the event type name
 * @param messageType the message type name, or {@code null} for an event without a
 * message
 * @param text the message text, or {@code null}
 * @param payload the JSON payload the codec writes
 * @param metadata the event metadata as JSON
 */
record EventValue(String id, String sessionId, Instant timestamp, String type, @Nullable String messageType,
		@Nullable String text, String payload, String metadata) {

	EventValue {
		Assert.hasText(id, "id must not be empty");
		Assert.hasText(sessionId, "sessionId must not be empty");
		Assert.notNull(timestamp, "timestamp must not be null");
		Assert.hasText(type, "type must not be empty");
		Assert.notNull(payload, "payload must not be null");
		Assert.notNull(metadata, "metadata must not be null");
	}

	static EventValue of(SessionEvent event, SessionEventCodec codec) {
		EncodedEvent encoded = codec.encode(event);
		MessageType messageType = encoded.messageType();
		return new EventValue(event.getId(), event.getSessionId(), event.getTimestamp(), encoded.type().name(),
				(messageType != null) ? messageType.name() : null, encoded.text(), encoded.payload(),
				codec.toJson(event.getMetadata()));
	}

	SessionEvent toEvent(SessionEventCodec codec) {
		MessageType messageType = (this.messageType != null) ? MessageType.valueOf(this.messageType) : null;
		return codec.decode(new EncodedEvent(EventType.of(this.type), messageType, this.text, this.payload))
			.id(this.id)
			.sessionId(this.sessionId)
			.timestamp(this.timestamp)
			.metadata(codec.fromJsonMap(this.metadata))
			.build();
	}

}
