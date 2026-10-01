package com.khack.review.practice.domain;

import org.jspecify.annotations.Nullable;

/**
 * 예/아니오(noul) 이유 질문의 확률을 참·거짓으로 읽는 구간. noul은 확률만 돌려주고 신뢰도가 없으므로, 확률이 애매하면
 * 억지로 정하지 않고 {@code null}(미확정)로 둔다. 원래 확률은 판정 기록에 그대로 남는다(docs/jev.md).
 *
 * @param yesMin 이 값 이상이면 그 이유가 있다
 * @param noMax  이 값 이하이면 그 이유가 없다
 */
public record ReasonBand(double yesMin, double noMax) {

    public ReasonBand {
        if (noMax < 0 || yesMin > 1 || noMax >= yesMin) {
            throw new IllegalArgumentException("이유 구간은 0 <= noMax < yesMin <= 1이어야 합니다: noMax=%s, yesMin=%s".formatted(noMax, yesMin));
        }
    }

    /** 있으면 true, 없으면 false, 애매하면 null. */
    public @Nullable Boolean decide(double probability) {
        if (probability >= yesMin) {
            return true;
        }
        return probability <= noMax ? false : null;
    }
}
