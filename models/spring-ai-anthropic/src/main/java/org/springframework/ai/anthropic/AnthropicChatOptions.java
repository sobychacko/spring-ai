/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.anthropic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicCacheType;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * The options to be used when sending a chat request to the Anthropic API.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @author Ilayaperumal Gopinathan
 * @author Soby Chacko
 * @author Austin Dase
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class AnthropicChatOptions implements ToolCallingChatOptions {

	// @formatter:off
	private @JsonProperty("model") String model;
	private @JsonProperty("max_tokens") Integer maxTokens;
	private @JsonProperty("metadata") ChatCompletionRequest.Metadata metadata;
	private @JsonProperty("stop_sequences") List<String> stopSequences;
	private @JsonProperty("temperature") Double temperature;
	private @JsonProperty("top_p") Double topP;
	private @JsonProperty("top_k") Integer topK;
	private @JsonProperty("thinking") ChatCompletionRequest.ThinkingConfig thinking;
	/**
	 * Cache control configuration options for the chat completion request.
	 */
	private @JsonProperty("cache_control") CacheControlConfiguration cacheControlConfiguration;

	/**
	 * Collection of {@link ToolCallback}s to be used for tool calling in the chat
	 * completion requests.
	 */
	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * Collection of tool names to be resolved at runtime and used for tool calling in the
	 * chat completion requests.
	 */
	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	@JsonIgnore
	private Boolean internalToolExecutionEnabled;

	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();


	/**
	 * Optional HTTP headers to be added to the chat completion request.
	 */
	@JsonIgnore
	private Map<String, String> httpHeaders = new HashMap<>();

	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static AnthropicChatOptions fromOptions(AnthropicChatOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.maxTokens(fromOptions.getMaxTokens())
			.metadata(fromOptions.getMetadata())
			.stopSequences(
					fromOptions.getStopSequences() != null ? new ArrayList<>(fromOptions.getStopSequences()) : null)
			.temperature(fromOptions.getTemperature())
			.topP(fromOptions.getTopP())
			.topK(fromOptions.getTopK())
			.thinking(fromOptions.getThinking())
			.toolCallbacks(
					fromOptions.getToolCallbacks() != null ? new ArrayList<>(fromOptions.getToolCallbacks()) : null)
			.toolNames(fromOptions.getToolNames() != null ? new HashSet<>(fromOptions.getToolNames()) : null)
			.internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled())
			.toolContext(fromOptions.getToolContext() != null ? new HashMap<>(fromOptions.getToolContext()) : null)
			.httpHeaders(fromOptions.getHttpHeaders() != null ? new HashMap<>(fromOptions.getHttpHeaders()) : null)
			.cacheControlConfiguration(fromOptions.getCacheControlConfiguration())
			.build();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public ChatCompletionRequest.Metadata getMetadata() {
		return this.metadata;
	}

	public void setMetadata(ChatCompletionRequest.Metadata metadata) {
		this.metadata = metadata;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	public ChatCompletionRequest.ThinkingConfig getThinking() {
		return this.thinking;
	}

	public void setThinking(ChatCompletionRequest.ThinkingConfig thinking) {
		this.thinking = thinking;
	}

	@Override
	@JsonIgnore
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	@JsonIgnore
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = toolCallbacks;
	}

	@Override
	@JsonIgnore
	public Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	@JsonIgnore
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(tool -> Assert.hasText(tool, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
	}

	@Override
	@Nullable
	@JsonIgnore
	public Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public Double getFrequencyPenalty() {
		return null;
	}

	@Override
	@JsonIgnore
	public Double getPresencePenalty() {
		return null;
	}

	@Override
	@JsonIgnore
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	@JsonIgnore
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	@JsonIgnore
	public Map<String, String> getHttpHeaders() {
		return this.httpHeaders;
	}

	public void setHttpHeaders(Map<String, String> httpHeaders) {
		this.httpHeaders = httpHeaders;
	}

	@JsonIgnore
	public CacheControlConfiguration getCacheControlConfiguration() {
		return this.cacheControlConfiguration;
	}

	public void setCacheControlConfiguration(CacheControlConfiguration cacheControlConfiguration) {
		this.cacheControlConfiguration = cacheControlConfiguration;
	}

	@Override
	@SuppressWarnings("unchecked")
	public AnthropicChatOptions copy() {
		return fromOptions(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnthropicChatOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.metadata, that.metadata)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.thinking, that.thinking)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, that.toolContext)
				&& Objects.equals(this.httpHeaders, that.httpHeaders)
				&& Objects.equals(this.cacheControlConfiguration, that.cacheControlConfiguration);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.maxTokens, this.metadata, this.stopSequences, this.temperature, this.topP,
				this.topK, this.thinking, this.toolCallbacks, this.toolNames, this.internalToolExecutionEnabled,
				this.toolContext, this.httpHeaders, this.cacheControlConfiguration);
	}

	public static class CacheControlConfiguration {

		/**
		 * The Anthropic API allows a maximum of 4 cache blocks. By default, we will
		 * attempt to cache up to 4 blocks.
		 */
		private static final int DEFAULT_MAX_CACHE_BLOCKS = 4;

		/**
		 * The minimum text or content length for a message to be considered for caching.
		 * By default, we will only cache messages with at least 2000 characters -
		 * counting characters as a lightweight way to roughly estimate tokens. This helps
		 * to avoid caching very short messages that are unlikely to benefit from caching.
		 * Note: The Anthropic API has a minimum cacheable message length of 1024 tokens.
		 * <a href=
		 * "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching#cache-limitations">See
		 * here</href>
		 */
		private static final int DEFAULT_MIN_CACHE_BLOCK_LENGTH = 2000;

		/**
		 * The default set of message types that are considered for caching. By default,
		 * we will cache system, user, assistant, and tool messages.
		 */
		private static final Set<MessageType> DEFAULT_CACHABLE_MESSAGE_TYPES = Set.of(MessageType.SYSTEM,
				MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL);

		/**
		 * The default cache types to use for each message type. By default, we will use
		 * EPHEMERAL_1H for system messages and EPHEMERAL for user, assistant, and tool
		 * messages. See <a href=
		 * "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching#1-hour-cache-duration">here</a>
		 */
		private static final Map<MessageType, AnthropicCacheType> DEFAULT_MESSAGE_TYPE_CACHE_TYPES = Map.of(
				MessageType.SYSTEM, AnthropicCacheType.EPHEMERAL_1H, MessageType.USER, AnthropicCacheType.EPHEMERAL,
				MessageType.ASSISTANT, AnthropicCacheType.EPHEMERAL, MessageType.TOOL, AnthropicCacheType.EPHEMERAL);

		private int maxCacheBlocks = DEFAULT_MAX_CACHE_BLOCKS;

		private int minCacheBlockLength = DEFAULT_MIN_CACHE_BLOCK_LENGTH;

		private Set<MessageType> cachableMessageTypes = new HashSet<>(DEFAULT_CACHABLE_MESSAGE_TYPES);

		private Map<MessageType, AnthropicCacheType> messageTypeCacheTypes = new HashMap<>(
				DEFAULT_MESSAGE_TYPE_CACHE_TYPES);

		/**
		 * To enable specific minimum block lengths per message type, use this map to
		 * override the default {@link #minCacheBlockLength} for specific message types.
		 * For example, you might want to set a higher minimum length for system messages
		 * and a lower minimum length for user messages.
		 */
		private Map<MessageType, Integer> messageTypeMinBlockLength = new HashMap<>();

		public static CacheControlConfiguration DEFAULT = new CacheControlConfiguration();

		public static Builder builder() {
			return new Builder();
		}

		public int getMaxCacheBlocks() {
			return this.maxCacheBlocks;
		}

		public void setMaxCacheBlocks(int maxCacheBlocks) {
			this.maxCacheBlocks = maxCacheBlocks;
		}

		public int getMinCacheBlockLength() {
			return this.minCacheBlockLength;
		}

		public void setMinCacheBlockLength(int minCacheBlockLength) {
			this.minCacheBlockLength = minCacheBlockLength;
		}

		public Set<MessageType> getCachableMessageTypes() {
			return this.cachableMessageTypes;
		}

		public void setCachableMessageTypes(Set<MessageType> cachableMessageTypes) {
			this.cachableMessageTypes = cachableMessageTypes;
		}

		public Map<MessageType, AnthropicCacheType> getMessageTypeCacheTypes() {
			return this.messageTypeCacheTypes;
		}

		public void setMessageTypeCacheTypes(Map<MessageType, AnthropicCacheType> messageTypeCacheTypes) {
			this.messageTypeCacheTypes = messageTypeCacheTypes;
		}

		public Map<MessageType, Integer> getMessageTypeMinBlockLength() {
			return this.messageTypeMinBlockLength;
		}

		public void setMessageTypeMinBlockLength(Map<MessageType, Integer> messageTypeMinBlockLength) {
			this.messageTypeMinBlockLength = messageTypeMinBlockLength;
		}

		/**
		 * Get the cache type for a given message type. If the message type is not
		 * configured, return EPHEMERAL as the default.
		 * @param messageType the message type
		 * @return the cache type for the message type
		 */
		public AnthropicCacheType getCacheTypeForMessageType(MessageType messageType) {
			return this.messageTypeCacheTypes.getOrDefault(messageType, AnthropicCacheType.EPHEMERAL);
		}

		/**
		 * Get the minimum block length for a given message type. If the message type is
		 * not configured, return the default minimum block length.
		 * @param messageType
		 * @return the minimum block length for the message type
		 */
		public Integer getMinBlockLengthForMessageType(MessageType messageType) {
			return this.messageTypeMinBlockLength.getOrDefault(messageType, this.minCacheBlockLength);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof CacheControlConfiguration that)) {
				return false;
			}
			return this.maxCacheBlocks == that.maxCacheBlocks && this.minCacheBlockLength == that.minCacheBlockLength
					&& Objects.equals(this.cachableMessageTypes, that.cachableMessageTypes)
					&& Objects.equals(this.messageTypeCacheTypes, that.messageTypeCacheTypes)
					&& Objects.equals(this.messageTypeMinBlockLength, that.messageTypeMinBlockLength);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.maxCacheBlocks, this.minCacheBlockLength, this.cachableMessageTypes,
					this.messageTypeCacheTypes, this.messageTypeMinBlockLength);
		}

		@Override
		public String toString() {
			return "CacheControlConfiguration{" + "maxCacheBlocks=" + this.maxCacheBlocks + ", minCacheBlockLength="
					+ this.minCacheBlockLength + ", cachableMessageTypes=" + this.cachableMessageTypes
					+ ", messageTypeCacheTypes=" + this.messageTypeCacheTypes + ", messageTypeMinBlockLength="
					+ this.messageTypeMinBlockLength + '}';
		}

		public static class Builder {

			private final CacheControlConfiguration configuration = new CacheControlConfiguration();

			public Builder() {
			}

			public Builder maxCacheBlocks(int maxCacheBlocks) {
				this.configuration.setMaxCacheBlocks(maxCacheBlocks);
				return this;
			}

			public Builder minCacheBlockLength(int minCacheBlockLength) {
				this.configuration.setMinCacheBlockLength(minCacheBlockLength);
				return this;
			}

			public Builder cachableMessageTypes(Set<MessageType> cachableMessageTypes) {
				this.configuration.setCachableMessageTypes(cachableMessageTypes);
				return this;
			}

			public Builder messageTypeCacheTypes(Map<MessageType, AnthropicCacheType> messageTypeCacheTypes) {
				this.configuration.setMessageTypeCacheTypes(messageTypeCacheTypes);
				return this;
			}

			public Builder addCachableMessageType(MessageType messageType) {
				if (this.configuration.getCachableMessageTypes() == null) {
					this.configuration.setCachableMessageTypes(new HashSet<>());
				}
				this.configuration.getCachableMessageTypes().add(messageType);
				return this;
			}

			public Builder addMessageTypeCacheType(MessageType messageType, AnthropicCacheType cacheType) {
				if (this.configuration.getMessageTypeCacheTypes() == null) {
					this.configuration.setMessageTypeCacheTypes(new HashMap<>());
				}
				this.configuration.getMessageTypeCacheTypes().put(messageType, cacheType);
				return this;
			}

			public Builder minBlockLengthForMessageType(MessageType messageType, Integer minBlockLength) {
				if (this.configuration.messageTypeMinBlockLength == null) {
					this.configuration.messageTypeMinBlockLength = new HashMap<>();
				}
				this.configuration.messageTypeMinBlockLength.put(messageType, minBlockLength);
				return this;
			}

			public CacheControlConfiguration build() {
				return this.configuration;
			}

		}

	}

	public static class Builder {

		private final AnthropicChatOptions options = new AnthropicChatOptions();

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder model(AnthropicApi.ChatModel model) {
			this.options.model = model.getValue();
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.options.maxTokens = maxTokens;
			return this;
		}

		public Builder metadata(ChatCompletionRequest.Metadata metadata) {
			this.options.metadata = metadata;
			return this;
		}

		public Builder stopSequences(List<String> stopSequences) {
			this.options.stopSequences = stopSequences;
			return this;
		}

		public Builder temperature(Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder topP(Double topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder topK(Integer topK) {
			this.options.topK = topK;
			return this;
		}

		public Builder thinking(ChatCompletionRequest.ThinkingConfig thinking) {
			this.options.thinking = thinking;
			return this;
		}

		public Builder thinking(AnthropicApi.ThinkingType type, Integer budgetTokens) {
			this.options.thinking = new ChatCompletionRequest.ThinkingConfig(type, budgetTokens);
			return this;
		}

		public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		public Builder toolCallbacks(ToolCallback... toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			this.options.toolCallbacks.addAll(Arrays.asList(toolCallbacks));
			return this;
		}

		public Builder toolNames(Set<String> toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.options.setToolNames(toolNames);
			return this;
		}

		public Builder toolNames(String... toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.options.toolNames.addAll(Set.of(toolNames));
			return this;
		}

		public Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
			return this;
		}

		public Builder toolContext(Map<String, Object> toolContext) {
			if (this.options.toolContext == null) {
				this.options.toolContext = toolContext;
			}
			else {
				this.options.toolContext.putAll(toolContext);
			}
			return this;
		}

		public Builder httpHeaders(Map<String, String> httpHeaders) {
			this.options.setHttpHeaders(httpHeaders);
			return this;
		}

		public Builder cacheControlConfiguration(CacheControlConfiguration cacheControlConfiguration) {
			this.options.cacheControlConfiguration = cacheControlConfiguration;
			return this;
		}

		public AnthropicChatOptions build() {
			return this.options;
		}

	}

}
