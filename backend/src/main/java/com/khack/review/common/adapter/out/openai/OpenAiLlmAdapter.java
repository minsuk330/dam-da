package com.khack.review.common.adapter.out.openai;

import com.khack.review.common.application.port.out.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiLlmAdapter implements LlmPort {

	private static final Logger log = LoggerFactory.getLogger(OpenAiLlmAdapter.class);

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
		long started = System.nanoTime();
		ResponseEntity<ChatResponse, T> response = request.call().responseEntity(responseType);
		// 응답 시간·토큰을 남겨 어디서 기다리는지 본다. 출력 토큰이 많으면 추론에 시간을 쓰는 것이다.
		Usage usage = response.response() == null ? null : response.response().getMetadata().getUsage();
		log.info("[llm] {} reasoning={} {}ms tokens in={} out={}", responseType.getSimpleName(), reasoning,
				(System.nanoTime() - started) / 1_000_000, usage == null ? null : usage.getPromptTokens(),
				usage == null ? null : usage.getCompletionTokens());
		return response.entity();
	}

}
