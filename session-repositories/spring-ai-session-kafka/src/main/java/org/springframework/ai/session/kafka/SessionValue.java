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

import org.springframework.ai.session.Session;
import org.springframework.ai.session.support.SessionEventCodec;
import org.springframework.util.Assert;

/**
 * A {@link Session} as it travels in requests and replies and sits in the store: the
 * metadata is kept as the JSON text {@link SessionEventCodec} writes, as JDBC keeps it.
 *
 * @param id the session id
 * @param createdAt when the session was created
 * @param expiresAt when it expires, or {@code null} for never
 * @param metadata the metadata as JSON
 */
record SessionValue(String id, Instant createdAt, @Nullable Instant expiresAt, String metadata) {

	SessionValue {
		Assert.hasText(id, "id must not be empty");
		Assert.notNull(createdAt, "createdAt must not be null");
		Assert.notNull(metadata, "metadata must not be null");
	}

	static SessionValue of(Session session, SessionEventCodec codec) {
		return new SessionValue(session.id(), session.createdAt(), session.expiresAt(),
				codec.toJson(session.metadata()));
	}

	Session toSession(SessionEventCodec codec) {
		return Session.builder()
			.id(this.id)
			.createdAt(this.createdAt)
			.expiresAt(this.expiresAt)
			.metadata(codec.fromJsonMap(this.metadata))
			.build();
	}

	/** Whether the session expired before the given instant. */
	boolean isExpiredBefore(Instant before) {
		return this.expiresAt != null && this.expiresAt.isBefore(before);
	}

}
