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

import org.jspecify.annotations.Nullable;

/**
 * The value of the sessions store: the session, the version of its log, the sequence
 * number the next event gets, and where the newest compaction boundary sits.
 *
 * @param session the session
 * @param version the event version, advanced once per new event and once per batch
 * @param nextSeq the sequence number of the next event; orders the events store
 * @param boundarySeq the sequence number of the newest compaction boundary, or
 * {@code null} when the log was never compacted; an active-view read starts there
 */
record StoredSession(SessionValue session, long version, long nextSeq, @Nullable Long boundarySeq) {

	/** A session with an empty log. */
	static StoredSession created(SessionValue session) {
		return new StoredSession(session, 0, 0, null);
	}

	StoredSession withSession(SessionValue session) {
		return new StoredSession(session, this.version, this.nextSeq, this.boundarySeq);
	}

	/**
	 * The session after {@code events} new events appended in one step.
	 * @param newBoundarySeq the sequence number of the newest boundary among them, or
	 * {@code null} when they hold none
	 */
	StoredSession appended(int events, @Nullable Long newBoundarySeq) {
		return new StoredSession(this.session, this.version + 1, this.nextSeq + events,
				(newBoundarySeq != null) ? newBoundarySeq : this.boundarySeq);
	}

}
