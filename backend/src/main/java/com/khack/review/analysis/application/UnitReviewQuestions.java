package com.khack.review.analysis.application;

import com.khack.review.common.application.port.out.JevQuestion;
import java.util.List;
import java.util.Map;

/**
 * 복습 단위 검수 Jev 질문 정의 (스펙 §6.2, docs/jev.md). 질문 문장·단계 설명은 프롬프트이므로 ai 소유다.
 * 판정 서비스({@link UnitReviewJudge})는 질문 이름과 단계 수에만 의존한다. 상태 필드는 {@link UnitReviewState}.
 */
public final class UnitReviewQuestions {

    /** noul: 기억 항목이 다시 떠올릴 가치가 있는 학습 내용인가. */
    public static final String WORTH_REVIEWING = "worth_reviewing";

    /** score: 기억 항목이 근거 발화와 연결되는가. 0(연결 안 됨)부터 단계가 오를수록 잘 연결된다. */
    public static final String EVIDENCE_FIT = "evidence_fit";

    static final List<String> EVIDENCE_FIT_LEVELS = List.of(
            "근거 발화와 무관하거나 모순된다",
            "일부 항목만 근거 발화에서 다룬 내용이다",
            "모든 항목이 근거 발화에서 다룬 질문·확인·정정에서 나왔다");

    private UnitReviewQuestions() {
    }

    public static Map<String, JevQuestion> questions() {
        return Map.of(
                WORTH_REVIEWING, JevQuestion.noul(
                        "`items`는 사용자가 `unitTitle`에 대해 학습하며 나중에 다시 떠올려야 할 지식(개념, 사실, 주의점, 실천법, 헷갈렸던 믿음)인가?",
                        "복습할 가치가 있는 학습 내용이다",
                        "잡담, 이번 대화에서만 쓰이는 맥락, 사소한 정보다"),
                EVIDENCE_FIT, JevQuestion.score(
                        "`evidence`는 사용자 발화와 그 발화에 대한 AI 판정·정정이다(AI 설명 본문은 없다). "
                                + "`items`의 각 항목은 `sourceTurns`가 가리키는 `evidence` 발화에서 다룬 질문·확인·정정과 연결되는가?",
                        EVIDENCE_FIT_LEVELS));
    }
}
