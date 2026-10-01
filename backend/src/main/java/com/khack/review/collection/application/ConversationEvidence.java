package com.khack.review.collection.application;

import com.khack.review.collection.domain.Fidelity;
import com.khack.review.collection.domain.UserTurn;
import java.util.List;

/** 학습 대화의 원문 여부와 사용자 발화. 분석 컨텍스트가 근거 발화를 판정할 때 쓴다. */
public record ConversationEvidence(Fidelity fidelity, List<UserTurn> turns) {
}
