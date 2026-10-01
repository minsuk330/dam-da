package com.khack.review.analysis.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 학습 분야 판정 기준 ({@code review.analysis.field.*}, 스펙 §7.10). 값은 실제 판정 분포({@code ./gradlew -q sessionFieldCheck})로 맞춘다.
 *
 * @param autoClassify  세션 저장 뒤 자동 판정 여부. 테스트는 끄고 필요한 테스트만 켠다
 * @param minConfidence choice 신뢰도 기준. 1단계가 낮으면 미분류, 2단계가 낮으면 그 대분류의 기타
 * @param maxAttempts   429·529일 때 첫 호출을 포함한 최대 호출 횟수
 * @param retryDelay    재시도 대기. n번째 재시도는 n배 기다린다
 */
@ConfigurationProperties("review.analysis.field")
public record SessionFieldPolicy(boolean autoClassify, double minConfidence, int maxAttempts, Duration retryDelay) {
}
