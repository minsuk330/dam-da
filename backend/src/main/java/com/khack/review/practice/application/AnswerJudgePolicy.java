package com.khack.review.practice.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 답변 판정 기준 ({@code review.practice.judge.*}). 판정 신뢰도 기준은 등급 변환({@code review.memory.rating})이 적용한다.
 *
 * @param failureThreshold 이유(noul) 확률이 이 값 이상이면 그 이유가 있다고 본다
 * @param maxAttempts      Jev 429·529일 때 첫 호출을 포함한 최대 호출 횟수
 * @param retryDelay       Jev 재시도 대기. n번째 재시도는 n배 기다린다
 */
@ConfigurationProperties("review.practice.judge")
public record AnswerJudgePolicy(double failureThreshold, int maxAttempts, Duration retryDelay) {
}
