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

package org.springframework.ai.chat.prompt;

import java.util.List;

public final class ChatOptionsBuilder {

	private final DefaultChatOptions options = new DefaultChatOptions();

	private ChatOptionsBuilder() {
	}

	public static ChatOptionsBuilder builder() {
		return new ChatOptionsBuilder();
	}

	public ChatOptionsBuilder withModel(String model) {
		this.options.setModel(model);
		return this;
	}

	public ChatOptionsBuilder withFrequencyPenalty(Double frequencyPenalty) {
		this.options.setFrequencyPenalty(frequencyPenalty);
		return this;
	}

	public ChatOptionsBuilder withMaxTokens(Integer maxTokens) {
		this.options.setMaxTokens(maxTokens);
		return this;
	}

	public ChatOptionsBuilder withPresencePenalty(Double presencePenalty) {
		this.options.setPresencePenalty(presencePenalty);
		return this;
	}

	public ChatOptionsBuilder withStopSequences(List<String> stop) {
		this.options.setStopSequences(stop);
		return this;
	}

	public ChatOptionsBuilder withTemperature(Double temperature) {
		this.options.setTemperature(temperature);
		return this;
	}

	public ChatOptionsBuilder withTopK(Integer topK) {
		this.options.setTopK(topK);
		return this;
	}

	public ChatOptionsBuilder withTopP(Double topP) {
		this.options.setTopP(topP);
		return this;
	}

	public ChatOptions build() {
		return this.options;
	}

	private static class DefaultChatOptions implements ChatOptions {

		private String model;

		private Double frequencyPenalty;

		private Integer maxTokens;

		private Double presencePenalty;

		private List<String> stopSequences;

		private Double temperature;

		private Integer topK;

		private Double topP;

		@Override
		public String getModel() {
			return this.model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		@Override
		public Double getFrequencyPenalty() {
			return this.frequencyPenalty;
		}

		public void setFrequencyPenalty(Double frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
		}

		@Override
		public Integer getMaxTokens() {
			return this.maxTokens;
		}

		public void setMaxTokens(Integer maxTokens) {
			this.maxTokens = maxTokens;
		}

		@Override
		public Double getPresencePenalty() {
			return this.presencePenalty;
		}

		public void setPresencePenalty(Double presencePenalty) {
			this.presencePenalty = presencePenalty;
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
		public Integer getTopK() {
			return this.topK;
		}

		public void setTopK(Integer topK) {
			this.topK = topK;
		}

		@Override
		public Double getTopP() {
			return this.topP;
		}

		public void setTopP(Double topP) {
			this.topP = topP;
		}

		@Override
		public ChatOptions copy() {
			return builder().withModel(this.model)
				.withFrequencyPenalty(this.frequencyPenalty)
				.withMaxTokens(this.maxTokens)
				.withPresencePenalty(this.presencePenalty)
				.withStopSequences(this.stopSequences != null ? List.copyOf(this.stopSequences) : null)
				.withTemperature(this.temperature)
				.withTopK(this.topK)
				.withTopP(this.topP)
				.build();
		}

	}

}
