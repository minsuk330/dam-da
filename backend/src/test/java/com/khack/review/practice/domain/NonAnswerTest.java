package com.khack.review.practice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NonAnswerTest {

    @ParameterizedTest
    @ValueSource(strings = {"ㅁㄴㅇㄹ", "ㄴㄴ", "ㅋㅋㅋ", "ㅁㄴㅇㄹ ㅁㄴㅇㄹ", "sss", "AAAA", "...", "?!", "   ", "ㅠㅠ."})
    void meaninglessAnswers(String answer) {
        assertThat(NonAnswer.is(answer)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"힙", "B", "3", "TCP", "O(n)", "합집합", "에러?", "스택은 스레드마다 따로", "모르겠어요", "ss lock", "sql"})
    void realAnswersEvenShortOnes(String answer) {
        assertThat(NonAnswer.is(answer)).isFalse();
    }
}
