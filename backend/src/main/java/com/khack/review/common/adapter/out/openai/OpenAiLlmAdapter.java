package com.khack.review.common.adapter.out.openai;

import com.khack.review.common.application.port.out.LlmPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class OpenAiLlmAdapter implements LlmPort {

	private final ChatClient chatClient;

	public OpenAiLlmAdapter(ChatClient.Builder chatClientBuilder) {
		this.chatClient = chatClientBuilder.build();
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
	public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
		return chatClient.prompt()
			.system(systemPrompt)
			.user(userPrompt)
			.call()
			.entity(responseType);
	}

}
