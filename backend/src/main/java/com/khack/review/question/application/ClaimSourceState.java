package com.khack.review.question.application;

import com.khack.review.question.application.port.out.SourceSearchPort.SourceExcerpt;
import java.util.List;

/** 출처 대조 Jev 상태(#178). {@code claim}은 정답 기준에서 뽑은 사실 주장 한 문장이다. */
public record ClaimSourceState(String claim, List<SourceExcerpt> sources) {
}
