package com.khack.review.live;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.core.env.Environment;

/** live 테스트는 실제 키가 있어야 한다. 없으면 무엇이 빠졌는지 알리고 실패한다. */
final class LiveKeys {

    private LiveKeys() {
    }

    static void require(Environment env) {
        assertThat(env.getProperty("review.jev.api-key", ""))
                .as("TYPESAFE_API_KEY가 비어 있습니다. backend/.env에 넣고 ./gradlew liveTest로 실행하세요.").isNotBlank();
        assertThat(env.getProperty("spring.ai.openai.api-key", ""))
                .as("OPENAI_API_KEY가 비어 있습니다. backend/.env에 넣고 ./gradlew liveTest로 실행하세요.").isNotBlank();
    }
}
