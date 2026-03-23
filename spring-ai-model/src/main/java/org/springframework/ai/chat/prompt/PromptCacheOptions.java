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

package org.springframework.ai.chat.prompt;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

/**
 * Portable prompt cache configuration that providers translate to their native cache
 * control mechanisms. This allows users to configure caching once and have it work across
 * providers that support explicit cache control (Anthropic, AWS Bedrock).
 *
 * <p>
 * Providers that use automatic caching (OpenAI) or external cache lifecycle management
 * (Google Gemini) will ignore these options. Providers that do not support caching at all
 * (DeepSeek, Mistral, Ollama) will also ignore them.
 *
 * <p>
 * Provider-specific cache options (e.g., {@code AnthropicCacheOptions},
 * {@code BedrockCacheOptions}) take precedence over these portable options when both are
 * set.
 *
 * @author Soby Chacko
 * @since 2.0.0
 * @see PromptCacheStrategy
 */
public final class PromptCacheOptions {

	private final PromptCacheStrategy strategy;

	private final @Nullable Duration ttl;

	private final @Nullable Integer minContentLength;

	private PromptCacheOptions(PromptCacheStrategy strategy, @Nullable Duration ttl,
			@Nullable Integer minContentLength) {
		this.strategy = strategy;
		this.ttl = ttl;
		this.minContentLength = minContentLength;
	}

	/**
	 * Returns the caching strategy to use.
	 * @return the cache strategy, never null
	 */
	public PromptCacheStrategy getStrategy() {
		return this.strategy;
	}

	/**
	 * Returns the desired cache time-to-live. Providers map this to their nearest
	 * supported value. For example, Anthropic supports 5 minutes and 1 hour; a TTL of 30
	 * minutes would map to 5 minutes.
	 * @return the desired TTL, or null to use the provider default
	 */
	public @Nullable Duration getTtl() {
		return this.ttl;
	}

	/**
	 * Returns the minimum content length (in characters) required for a region to be
	 * eligible for caching. Content shorter than this threshold will not be cached.
	 * @return the minimum content length, or null to use the provider default
	 */
	public @Nullable Integer getMinContentLength() {
		return this.minContentLength;
	}

	/**
	 * Returns whether caching is effectively disabled.
	 * @return true if the strategy is {@link PromptCacheStrategy#NONE}
	 */
	public boolean isDisabled() {
		return this.strategy == PromptCacheStrategy.NONE;
	}

	/**
	 * Creates a new builder for {@link PromptCacheOptions}.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a disabled {@link PromptCacheOptions} with strategy {@code NONE}.
	 * @return a disabled cache options instance
	 */
	public static PromptCacheOptions disabled() {
		return new PromptCacheOptions(PromptCacheStrategy.NONE, null, null);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("PromptCacheOptions{");
		sb.append("strategy=").append(this.strategy);
		if (this.ttl != null) {
			sb.append(", ttl=").append(this.ttl);
		}
		if (this.minContentLength != null) {
			sb.append(", minContentLength=").append(this.minContentLength);
		}
		sb.append('}');
		return sb.toString();
	}

	/**
	 * Builder for {@link PromptCacheOptions}.
	 */
	public static final class Builder {

		private PromptCacheStrategy strategy = PromptCacheStrategy.NONE;

		private @Nullable Duration ttl;

		private @Nullable Integer minContentLength;

		private Builder() {
		}

		/**
		 * Sets the caching strategy.
		 * @param strategy the cache strategy
		 * @return this builder
		 */
		public Builder strategy(PromptCacheStrategy strategy) {
			this.strategy = strategy;
			return this;
		}

		/**
		 * Sets the desired cache TTL. Providers map this to their nearest supported
		 * value.
		 * @param ttl the desired time-to-live
		 * @return this builder
		 */
		public Builder ttl(Duration ttl) {
			this.ttl = ttl;
			return this;
		}

		/**
		 * Sets the minimum content length for cache eligibility.
		 * @param minContentLength the minimum content length in characters
		 * @return this builder
		 */
		public Builder minContentLength(int minContentLength) {
			this.minContentLength = minContentLength;
			return this;
		}

		/**
		 * Builds the {@link PromptCacheOptions}.
		 * @return the prompt cache options
		 */
		public PromptCacheOptions build() {
			return new PromptCacheOptions(this.strategy, this.ttl, this.minContentLength);
		}

	}

}
