package com.khack.review.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.port.out.LlmPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** 실제 OpenAI 연결 확인. 문제 생성·추출 구현이 생기면 그 흐름의 live 테스트를 각 이슈에서 추가한다. */
@Tag("live")
@SpringBootTest
class LiveLlmIT {

    @Autowired
    Environment env;

    @Autowired
    LlmPort llm;

    @BeforeEach
    void keys() {
        LiveKeys.require(env);
    }

    @Test
    void realOpenAiAnswers() {
        String answer = llm.generate("You answer with exactly one word.", "Reply with the word: pong");

        System.out.println("[live] OpenAI → " + answer);
        assertThat(answer).isNotBlank().containsIgnoringCase("pong");
    }
}
