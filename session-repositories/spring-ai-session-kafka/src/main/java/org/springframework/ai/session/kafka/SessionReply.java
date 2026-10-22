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
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The answer to a {@link SessionRequest}, as a record on the replies topic: the result
 * the operation returns, or the rejection the caller rethrows. Only the fields the
 * operation returns are set.
 *
 * @param rejection how the request was rejected, or {@code null} when it was not
 * @param message the message of the rejection
 * @param flag the result of a conditional write
 * @param count the event version, or the number of sessions deleted
 * @param createdAt the creation time a save kept
 * @param session the session found
 * @param sessions the sessions a metadata lookup found
 * @param events the events a read selected
 */
record SessionReply(@Nullable Rejection rejection, @Nullable String message, @Nullable Boolean flag,
		@Nullable Long count, @Nullable Instant createdAt, @Nullable SessionValue session,
		@Nullable List<SessionValue> sessions, @Nullable List<EventValue> events) {

	static SessionReply empty() {
		return new SessionReply(null, null, null, null, null, null, null, null);
	}

	static SessionReply ofFlag(boolean flag) {
		return new SessionReply(null, null, flag, null, null, null, null, null);
	}

	static SessionReply ofCount(long count) {
		return new SessionReply(null, null, null, count, null, null, null, null);
	}

	static SessionReply ofCreatedAt(Instant createdAt) {
		return new SessionReply(null, null, null, null, createdAt, null, null, null);
	}

	static SessionReply ofSession(@Nullable SessionValue session) {
		return new SessionReply(null, null, null, null, null, session, null, null);
	}

	static SessionReply ofSessions(List<SessionValue> sessions) {
		return new SessionReply(null, null, null, null, null, null, sessions, null);
	}

	static SessionReply ofEvents(List<EventValue> events) {
		return new SessionReply(null, null, null, null, null, null, null, events);
	}

	static SessionReply rejected(Rejection rejection, @Nullable String message) {
		return new SessionReply(rejection, message, null, null, null, null, null, null);
	}

	/**
	 * Rethrows a rejection as the exception the repository contract names.
	 * @return this reply, when it is not a rejection
	 */
	SessionReply orThrow() {
		if (this.rejection == null) {
			return this;
		}
		String text = (this.message != null) ? this.message : this.rejection.name();
		throw switch (this.rejection) {
			case ILLEGAL_ARGUMENT -> new IllegalArgumentException(text);
			case ILLEGAL_STATE -> new IllegalStateException(text);
			case UNREADABLE_REQUEST ->
				new IllegalStateException("The session store could not read the request: " + text);
		};
	}

	/** Why a request was rejected. */
	enum Rejection {

		/**
		 * The contract's {@link IllegalArgumentException}: an unknown session, a bad
		 * batch.
		 */
		ILLEGAL_ARGUMENT,

		/** The contract's {@link IllegalStateException}. */
		ILLEGAL_STATE,

		/** The task could not read the request; nothing was changed. */
		UNREADABLE_REQUEST

	}

}
