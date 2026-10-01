package com.khack.review.question.application;

import com.khack.review.common.application.port.out.JevQuestion;
import java.util.List;
import java.util.Map;

/**
 * 문제 품질 검사 Jev 질문 정의 (스펙 §6.2 "문제의 원문 근거성, 명확성, 중복", docs/jev.md). 질문 문장·단계 설명은
 * 프롬프트이므로 ai 소유이며 이 초안을 다듬는다. 판정 서비스({@link QuestionQualityJudge})는 질문 이름과 단계 수에만 의존한다.
 * 상태 필드는 {@link QuestionQualityState}.
 */
public final class QuestionQualityQuestions {

    /** noul: 문제와 정답 기준이 기억 항목과 근거 발화만으로 답할 수 있는 내용인가. */
    public static final String GROUNDED = "grounded";

    /** score: 문제가 하나의 답을 명확히 요구하는가. 0(모호)부터 단계가 오를수록 명확하다. */
    public static final String CLARITY = "clarity";

    /** noul: 같은 기억 항목의 기존 문제와 사실상 같은 문제인가. */
    public static final String DUPLICATE = "duplicate";

    static final List<String> CLARITY_LEVELS = List.of(
            "모호하거나 정답이 여러 개일 수 있다",
            "대체로 명확하지만 표현을 다듬어야 한다",
            "하나의 답을 명확히 요구하고 정답 기준과 맞는다");

    private QuestionQualityQuestions() {
    }

    public static Map<String, JevQuestion> questions() {
        return Map.of(
                GROUNDED, JevQuestion.noul(
                        "`question`과 `answerCriteria`는 `item`과 `evidence`(사용자 발화와 AI 판정·교정)에 있는 내용만으로 답하고 채점할 수 있는가?",
                        "기억 항목과 근거 발화만으로 답할 수 있다",
                        "대화에 없는 사실을 요구하거나 정답 기준이 근거와 맞지 않는다"),
                CLARITY, JevQuestion.score(
                        "`question`(객관식이면 `choices`와 `correctChoice` 포함)은 학습자에게 하나의 답을 명확히 요구하고, `answerCriteria`로 채점할 수 있는가?",
                        CLARITY_LEVELS),
                DUPLICATE, JevQuestion.noul(
                        "`question`은 `existing`(같은 기억 항목의 기존 문제) 중 하나와 표현만 다를 뿐 사실상 같은 문제인가? `existing`이 비어 있으면 아니다.",
                        "기존 문제와 사실상 같다",
                        "기존 문제와 다르다"));
    }
}
