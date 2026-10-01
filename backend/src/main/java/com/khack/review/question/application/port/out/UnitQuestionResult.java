package com.khack.review.question.application.port.out;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 복습 단위 1개의 생성 결과. 요청한 대상마다 하나씩, 문제 또는 만들지 못한 이유를 돌려준다.
 * 빠진 대상은 서버가 실패로 보고 다시 요청한다.
 */
public record UnitQuestionResult(List<TargetResult> results) {

    /** {@code question}과 {@code failure} 중 하나만 있다. */
    public record TargetResult(String targetId, @Nullable GeneratedQuestion question, @Nullable String failure) {

        public static TargetResult made(String targetId, GeneratedQuestion question) {
            return new TargetResult(targetId, question, null);
        }

        public static TargetResult failed(String targetId, String reason) {
            return new TargetResult(targetId, null, reason);
        }
    }
}
