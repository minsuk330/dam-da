package com.khack.review.common.adapter.out.openai;

import com.khack.review.common.application.port.out.LlmPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiLlmAdapter implements LlmPort {

	private final ChatClient chatClient;

	/** {@link Reasoning#MINIMAL}일 때 보내는 {@code reasoning_effort}. 모델마다 지원 값이 달라 설정으로 둔다. */
	private final String minimalReasoningEffort;

	public OpenAiLlmAdapter(ChatClient.Builder chatClientBuilder,
			@Value("${review.llm.minimal-reasoning-effort:none}") String minimalReasoningEffort) {
		this.chatClient = chatClientBuilder.build();
		this.minimalReasoningEffort = minimalReasoningEffort;
	}

	@Override
	public String generate(String systemPrompt, String userPrompt) {
		return chatClient.prompt()
			.system(systemPrompt)
			.user(userPrompt)
			.call()
			.content();
	}

	@Override
	public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType, Reasoning reasoning) {
		ChatClient.ChatClientRequestSpec request = chatClient.prompt().system(systemPrompt).user(userPrompt);
		if (reasoning == Reasoning.MINIMAL) {
			request = request.options(OpenAiChatOptions.builder().reasoningEffort(minimalReasoningEffort));
		}
		return request.call().entity(responseType);
	}

}
