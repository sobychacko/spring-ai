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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link SessionMemoryAdvisor} on a {@link KafkaSessionRepository}, nested inside a
 * tool-calling loop, as the JDBC store is tested. Messages read back from the store do
 * not carry the model output's metadata, so they are not {@code equals} to the messages
 * the loop re-sends; the advisor must still recognize the history already in the prompt.
 */
class SessionMemoryAdvisorKafkaTests {

	@TempDir
	static Path stateDirectory;

	private static EmbeddedKafkaKraftBroker broker;

	private static KafkaSessionRepository repository;

	private final AdvisorChain chain = mock(AdvisorChain.class);

	private SessionService sessionService;

	private SessionMemoryAdvisor advisor;

	private String sessionId;

	@BeforeAll
	static void startRepository() {
		broker = KafkaSessionTestSupport.startBroker();
		repository = KafkaSessionTestSupport.repository(broker, "advisor", stateDirectory).build();
		repository.start();
	}

	@AfterAll
	static void stopRepository() {
		if (repository != null) {
			repository.close();
		}
		if (broker != null) {
			broker.destroy();
		}
	}

	@BeforeEach
	void setUp() {
		this.sessionService = DefaultSessionService.builder().sessionRepository(repository).build();
		this.advisor = SessionMemoryAdvisor.builder(this.sessionService).build();
		this.sessionId = UUID.randomUUID().toString();
	}

	@Test
	void toolLoopDoesNotResendHistoryReadBackFromKafka() {
		UserMessage userMessage = new UserMessage("What is the weather in Paris?");
		AssistantMessage toolCallMessage = weatherCall(Map.of("id", "msg-1", "finishReason", "TOOL_CALLS"));

		List<Message> round1 = before(List.of(userMessage));
		after(toolCallMessage);

		// Round 2: the loop re-sends round 1's prompt plus the tool exchange
		ToolResponseMessage toolResponse = weatherResult();
		List<Message> round2Input = new ArrayList<>(round1);
		round2Input.add(toolCallMessage);
		round2Input.add(toolResponse);
		List<Message> round2 = before(round2Input);

		assertThat(round2).containsExactlyElementsOf(round2Input);
	}

	@Test
	void earlierTurnsReadBackFromKafkaArePrependedOnce() {
		before(List.of(new UserMessage("Hello")));
		after(AssistantMessage.builder().content("Bonjour !").properties(Map.of("id", "msg-0")).build());

		UserMessage userMessage = new UserMessage("What is the weather in Paris?");
		AssistantMessage toolCallMessage = weatherCall(Map.of("id", "msg-1"));

		List<Message> round1 = before(List.of(userMessage));
		after(toolCallMessage);

		ToolResponseMessage toolResponse = weatherResult();
		List<Message> round2Input = new ArrayList<>(round1);
		round2Input.add(toolCallMessage);
		round2Input.add(toolResponse);
		List<Message> round2 = before(round2Input);

		assertThat(round1).extracting(Message::getText)
			.containsExactly("Hello", "Bonjour !", "What is the weather in Paris?");
		assertThat(round2).containsExactlyElementsOf(round2Input);
	}

	@Test
	void lastNWindowReadBackFromKafkaKeepsToolTurnWhole() {
		AssistantMessage toolCallMessage = weatherCall(Map.of());
		ToolResponseMessage toolResponse = weatherResult();
		before(List.of(new UserMessage("What is the weather in Paris?")));
		after(toolCallMessage);
		before(List.of(toolResponse));
		after(AssistantMessage.builder().content("Sunny.").build());
		SessionMemoryAdvisor windowed = SessionMemoryAdvisor.builder(this.sessionService)
			.eventFilter(EventFilter.lastN(2))
			.build();

		// The window lands on the tool result; the store extends it to the user message
		// that started the turn, so the tool call is answered
		List<Message> prompt = windowed
			.before(ChatClientRequest.builder()
				.prompt(new Prompt(List.of(new UserMessage("And tomorrow?"))))
				.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
				.build(), this.chain)
			.prompt()
			.getInstructions();

		assertThat(prompt).extracting(Message::getMessageType)
			.containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL, MessageType.ASSISTANT,
					MessageType.USER);
	}

	/**
	 * The model asking for the Paris weather, with the metadata a model output carries.
	 */
	private static AssistantMessage weatherCall(Map<String, Object> properties) {
		return AssistantMessage.builder()
			.properties(properties)
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-1", "function", "get_weather", "{\"city\":\"Paris\"}")))
			.build();
	}

	private static ToolResponseMessage weatherResult() {
		return ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "get_weather", "15 degrees and sunny")))
			.build();
	}

	private List<Message> before(List<Message> messages) {
		return this.advisor
			.before(ChatClientRequest.builder()
				.prompt(new Prompt(messages))
				.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
				.build(), this.chain)
			.prompt()
			.getInstructions();
	}

	private void after(AssistantMessage output) {
		this.advisor.after(ChatClientResponse.builder()
			.chatResponse(ChatResponse.builder().generations(List.of(new Generation(output))).build())
			.context(Map.of(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, this.sessionId))
			.build(), this.chain);
	}

}
