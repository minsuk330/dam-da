package com.khack.review.practice.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 단계적 피드백 기준 ({@code review.practice.feedback.*}, docs/jev.md 신뢰도 기준). 값은 실제 판정 분포로 맞춘다.
 *
 * @param minConfidence                다음 행동 선택(choice) 신뢰도 기준. 이보다 낮으면 규칙의 기본 행동을 쓴다
 * @param maxAttempts                  Jev 429·529일 때 첫 호출을 포함한 최대 호출 횟수
 * @param retryDelay                   재시도 대기. n번째 재시도는 n배 기다린다
 * @param relearnMaxPerSession         풀이 세션 하나에서 오늘 다시 묻기(확인 문제 포함)로 큐 끝에 넣을 수 있는 최대 개수(하루 분량 상한)
 * @param repeatedDifficultyPresentations 한 기억 항목에서 틀린 제시가 이만큼 쌓이면 반복 어려움으로 보고 선행 개념을 제안한다
 */
@ConfigurationProperties("review.practice.feedback")
public record FeedbackPolicy(
        double minConfidence,
        int maxAttempts,
        Duration retryDelay,
        int relearnMaxPerSession,
        int repeatedDifficultyPresentations) {
}
