package com.khack.review.analysis.application;

import com.khack.review.collection.domain.Fidelity;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 복습 단위 검수 기준 ({@code review.analysis.*}, docs/jev.md 신뢰도 기준). 값은 실제 판정 분포로 맞춘다.
 *
 * @param minConfidence            근거 연결(score) 신뢰도 기준. 이보다 낮으면 보류
 * @param minConfidenceTranscribed 모델이 옮겨 적은 발화(`model_transcribed`)일 때의 기준. 더 높게 잡는다(스펙 §7.3)
 * @param holdBandLow              복습 가치(noul) 확률이 이 값 이상이고
 * @param holdBandHigh             이 값 이하이면 보류
 * @param minEvidenceFit           근거 연결 점수를 0~1로 바꾼 값의 통과 기준
 * @param maxAttempts              429·529일 때 첫 호출을 포함한 최대 호출 횟수
 * @param retryDelay               재시도 대기. n번째 재시도는 n배 기다린다
 */
@ConfigurationProperties("review.analysis")
public record UnitReviewPolicy(
        double minConfidence,
        double minConfidenceTranscribed,
        double holdBandLow,
        double holdBandHigh,
        double minEvidenceFit,
        int maxAttempts,
        Duration retryDelay) {

    public double minConfidenceFor(Fidelity fidelity) {
        return fidelity == Fidelity.model_transcribed ? minConfidenceTranscribed : minConfidence;
    }

    public boolean inHoldBand(double probability) {
        return probability >= holdBandLow && probability <= holdBandHigh;
    }
}
