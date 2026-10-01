package com.khack.review.question.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 문제 품질 검사와 생성 기준 ({@code review.question.*}, docs/jev.md 신뢰도 기준). 값은 실제 판정 분포로 맞춘다.
 *
 * @param minGrounded          근거성(noul) 확률 통과 기준
 * @param minClarity           명확성 점수를 0~1로 바꾼 값의 통과 기준
 * @param minConfidence        명확성(score) 신뢰도 기준. 낮으면 통과시키지 않는다(규칙 3)
 * @param maxDuplicate         중복(noul) 확률이 이 값 이상이면 떨어뜨린다
 * @param maxAttempts          Jev 429·529일 때 첫 호출을 포함한 최대 호출 횟수
 * @param retryDelay           Jev 재시도 대기. n번째 재시도는 n배 기다린다
 * @param maxGenerations       문제 1개 자리에 생성을 시도하는 최대 횟수. 넘으면 그 항목 출제를 보류한다
 */
@ConfigurationProperties("review.question")
public record QuestionQualityPolicy(
        double minGrounded,
        double minClarity,
        double minConfidence,
        double maxDuplicate,
        int maxAttempts,
        Duration retryDelay,
        int maxGenerations) {
}
