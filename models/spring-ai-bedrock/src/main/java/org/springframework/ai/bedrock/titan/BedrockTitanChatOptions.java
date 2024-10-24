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

package org.springframework.ai.bedrock.titan;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 0.8.0
 */
@JsonInclude(Include.NON_NULL)
public class BedrockTitanChatOptions implements ChatOptions {

	// @formatter:off
	/**
	 * The temperature value controls the randomness of the generated text.
	 */
	private @JsonProperty("temperature") Double temperature;

	/**
	 * The topP value controls the diversity of the generated text. Use a lower value to ignore less probable options.
	 */
	private @JsonProperty("topP") Double topP;

	/**
	 * Maximum number of tokens to generate.
	 */
	private @JsonProperty("maxTokenCount") Integer maxTokenCount;

	/**
	 * A list of tokens that the model should stop generating after.
	 */
	private @JsonProperty("stopSequences") List<String> stopSequences;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static BedrockTitanChatOptions fromOptions(BedrockTitanChatOptions fromOptions) {
		return builder().withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withMaxTokenCount(fromOptions.getMaxTokenCount())
			.withStopSequences(fromOptions.getStopSequences())
			.build();
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
	@JsonIgnore
	public Integer getMaxTokens() {
		return getMaxTokenCount();
	}

	@JsonIgnore
	public void setMaxTokens(Integer maxTokens) {
		setMaxTokenCount(maxTokens);
	}

	public Integer getMaxTokenCount() {
		return this.maxTokenCount;
	}

	public void setMaxTokenCount(Integer maxTokenCount) {
		this.maxTokenCount = maxTokenCount;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	@JsonIgnore
	public String getModel() {
		return null;
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
	public Integer getTopK() {
		return null;
	}

	@Override
	public BedrockTitanChatOptions copy() {
		return fromOptions(this);
	}

	public static class Builder {

		private BedrockTitanChatOptions options = new BedrockTitanChatOptions();

		public Builder withTemperature(Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder withTopP(Double topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder withMaxTokenCount(Integer maxTokenCount) {
			this.options.maxTokenCount = maxTokenCount;
			return this;
		}

		public Builder withStopSequences(List<String> stopSequences) {
			this.options.stopSequences = stopSequences;
			return this;
		}

		public BedrockTitanChatOptions build() {
			return this.options;
		}

	}

}
