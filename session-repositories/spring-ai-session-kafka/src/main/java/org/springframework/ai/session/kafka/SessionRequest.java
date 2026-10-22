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
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.EventType;
import org.springframework.util.Assert;

/**
 * One call to the repository, as a record on the requests topic. A request about one
 * session is keyed by its id, so it reaches the task that owns the session; a request
 * about every session is sent once to each partition.
 * <p>
 * Every record checks its required fields when it is built, so a request that is valid
 * JSON but lacks one fails to parse, and the task skips it rather than failing on it.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "op")
@JsonSubTypes({ @JsonSubTypes.Type(value = SessionRequest.Save.class, name = "SAVE"),
		@JsonSubTypes.Type(value = SessionRequest.SaveIfAbsent.class, name = "SAVE_IF_ABSENT"),
		@JsonSubTypes.Type(value = SessionRequest.FindSession.class, name = "FIND_SESSION"),
		@JsonSubTypes.Type(value = SessionRequest.Delete.class, name = "DELETE"),
		@JsonSubTypes.Type(value = SessionRequest.Append.class, name = "APPEND"),
		@JsonSubTypes.Type(value = SessionRequest.AppendBatch.class, name = "APPEND_BATCH"),
		@JsonSubTypes.Type(value = SessionRequest.GetVersion.class, name = "GET_VERSION"),
		@JsonSubTypes.Type(value = SessionRequest.FindEvents.class, name = "FIND_EVENTS"),
		@JsonSubTypes.Type(value = SessionRequest.FindByMetadata.class, name = "FIND_BY_METADATA"),
		@JsonSubTypes.Type(value = SessionRequest.DeleteExpired.class, name = "DELETE_EXPIRED") })
sealed interface SessionRequest {

	record Save(SessionValue session) implements SessionRequest {

		public Save {
			Assert.notNull(session, "session must not be null");
		}

	}

	record SaveIfAbsent(SessionValue session) implements SessionRequest {

		public SaveIfAbsent {
			Assert.notNull(session, "session must not be null");
		}

	}

	record FindSession(String sessionId) implements SessionRequest {

		public FindSession {
			Assert.hasText(sessionId, "sessionId must not be empty");
		}

	}

	record Delete(String sessionId) implements SessionRequest {

		public Delete {
			Assert.hasText(sessionId, "sessionId must not be empty");
		}

	}

	record Append(EventValue event) implements SessionRequest {

		public Append {
			Assert.notNull(event, "event must not be null");
		}

	}

	record AppendBatch(String sessionId, List<EventValue> events, long expectedVersion) implements SessionRequest {

		public AppendBatch {
			Assert.hasText(sessionId, "sessionId must not be empty");
			Assert.notNull(events, "events must not be null");
			Assert.noNullElements(events, "events must not contain null");
		}

	}

	record GetVersion(String sessionId) implements SessionRequest {

		public GetVersion {
			Assert.hasText(sessionId, "sessionId must not be empty");
		}

	}

	record FindEvents(String sessionId, Filter filter) implements SessionRequest {

		public FindEvents {
			Assert.hasText(sessionId, "sessionId must not be empty");
			Assert.notNull(filter, "filter must not be null");
		}

	}

	/** Sent to every partition; each answers for the sessions it owns. */
	record FindByMetadata(String name, String value) implements SessionRequest {

		public FindByMetadata {
			Assert.hasText(name, "name must not be empty");
			Assert.notNull(value, "value must not be null");
		}

	}

	/** Sent to every partition; each deletes its own expired sessions. */
	record DeleteExpired(Instant before) implements SessionRequest {

		public DeleteExpired {
			Assert.notNull(before, "before must not be null");
		}

	}

	/**
	 * An {@link EventFilter} in a form JSON carries; the filter is applied by the task,
	 * so only the events it selects travel back.
	 */
	record Filter(@Nullable Set<MessageType> messageTypes, @Nullable Set<String> eventTypes, boolean excludeSynthetic,
			boolean activeView, @Nullable Integer lastN) {

		static Filter of(EventFilter filter) {
			Set<EventType> eventTypes = filter.eventTypes();
			Integer lastN = (filter.window() instanceof EventFilter.Window.Last last) ? last.n() : null;
			return new Filter(filter.messageTypes(),
					(eventTypes != null) ? eventTypes.stream().map(EventType::name).collect(Collectors.toSet()) : null,
					filter.excludeSynthetic(), filter.activeView(), lastN);
		}

		EventFilter toEventFilter() {
			Set<String> names = this.eventTypes;
			return EventFilter.builder()
				.messageTypes(this.messageTypes)
				.eventTypes((names != null) ? names.stream().map(EventType::of).collect(Collectors.toSet()) : null)
				.excludeSynthetic(this.excludeSynthetic)
				.activeView(this.activeView)
				.lastN(this.lastN)
				.build();
		}

	}

}
