/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.model;

import java.util.Collections;
import java.util.Map;

/**
 * Represents the context for tool execution in a function calling scenario.
 *
 * <p>
 * This class encapsulates a map of contextual information that can be passed to tools
 * (functions) when they are called. It provides an immutable view of the context to
 * ensure thread-safety and prevent modification after creation.
 * </p>
 *
 * <p>
 * The context is typically populated from the {@code toolContext} field of
 * {@code FunctionCallingOptions} and is used in the function execution process.
 * </p>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ToolContext {

	private final Map<String, Object> context;

	/**
	 * Constructs a new ToolContext with the given context map.
	 * @param context A map containing the tool context information. This map is wrapped
	 * in an unmodifiable view to prevent changes.
	 */
	public ToolContext(Map<String, Object> context) {
		this.context = Collections.unmodifiableMap(context);
	}

	/**
	 * Returns the immutable context map.
	 * @return An unmodifiable view of the context map.
	 */
	public Map<String, Object> getContext() {
		return this.context;
	}

}
