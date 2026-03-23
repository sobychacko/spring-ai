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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptCacheOptionsTests {

	@Test
	void defaultBuilderCreatesDisabledOptions() {
		PromptCacheOptions options = PromptCacheOptions.builder().build();
		assertThat(options.getStrategy()).isEqualTo(PromptCacheStrategy.NONE);
		assertThat(options.getTtl()).isNull();
		assertThat(options.getMinContentLength()).isNull();
		assertThat(options.isDisabled()).isTrue();
	}

	@Test
	void disabledFactoryMethodCreatesDisabledOptions() {
		PromptCacheOptions options = PromptCacheOptions.disabled();
		assertThat(options.getStrategy()).isEqualTo(PromptCacheStrategy.NONE);
		assertThat(options.isDisabled()).isTrue();
	}

	@Test
	void builderWithAllFields() {
		PromptCacheOptions options = PromptCacheOptions.builder()
			.strategy(PromptCacheStrategy.SYSTEM_AND_TOOLS)
			.ttl(Duration.ofMinutes(60))
			.minContentLength(1024)
			.build();

		assertThat(options.getStrategy()).isEqualTo(PromptCacheStrategy.SYSTEM_AND_TOOLS);
		assertThat(options.getTtl()).isEqualTo(Duration.ofMinutes(60));
		assertThat(options.getMinContentLength()).isEqualTo(1024);
		assertThat(options.isDisabled()).isFalse();
	}

	@Test
	void isDisabledReturnsFalseForNonNoneStrategies() {
		for (PromptCacheStrategy strategy : PromptCacheStrategy.values()) {
			PromptCacheOptions options = PromptCacheOptions.builder().strategy(strategy).build();
			if (strategy == PromptCacheStrategy.NONE) {
				assertThat(options.isDisabled()).isTrue();
			}
			else {
				assertThat(options.isDisabled()).isFalse();
			}
		}
	}

	@Test
	void toStringIncludesStrategy() {
		PromptCacheOptions options = PromptCacheOptions.builder().strategy(PromptCacheStrategy.SYSTEM_ONLY).build();
		assertThat(options.toString()).contains("SYSTEM_ONLY");
		assertThat(options.toString()).doesNotContain("ttl=");
		assertThat(options.toString()).doesNotContain("minContentLength=");
	}

	@Test
	void toStringIncludesAllSetFields() {
		PromptCacheOptions options = PromptCacheOptions.builder()
			.strategy(PromptCacheStrategy.CONVERSATION_HISTORY)
			.ttl(Duration.ofMinutes(5))
			.minContentLength(512)
			.build();
		assertThat(options.toString()).contains("CONVERSATION_HISTORY");
		assertThat(options.toString()).contains("ttl=");
		assertThat(options.toString()).contains("minContentLength=512");
	}

	@Test
	void allStrategyValuesExist() {
		assertThat(PromptCacheStrategy.values()).containsExactly(PromptCacheStrategy.NONE,
				PromptCacheStrategy.SYSTEM_ONLY, PromptCacheStrategy.TOOLS_ONLY, PromptCacheStrategy.SYSTEM_AND_TOOLS,
				PromptCacheStrategy.CONVERSATION_HISTORY);
	}

}
