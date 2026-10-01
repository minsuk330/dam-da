package com.khack.review.question.application;

import com.khack.review.common.application.port.out.JevQuestion;
import java.util.List;
import java.util.Map;

/**
 * 문제 품질 검사 Jev 질문 정의 (스펙 §6.2 "문제의 원문 근거성, 명확성, 중복", docs/jev.md). 질문 문장·단계 설명은
 * 프롬프트이므로 ai 소유다. 바꿀 때는 {@code ./gradlew -q questionQualityCheck}로 실제 확률·신뢰도 분포를 본다. 판정 서비스({@link QuestionQualityJudge})는 질문 이름과 단계 수에만 의존한다.
 * 상태 필드는 {@link QuestionQualityState}.
 */
public final class QuestionQualityQuestions {

    /** noul: 정답 기준이 기억 항목과 대화 속 교정의 내용에서 나왔는가(대화에 없는 사실을 정답으로 삼지 않는다, 규칙 8). */
    public static final String GROUNDED = "grounded";

    /** score: 문제가 하나의 답을 명확히 요구하는가. 0(모호)부터 단계가 오를수록 명확하다. */
    public static final String CLARITY = "clarity";

    /** noul: 같은 기억 항목의 기존 문제와 사실상 같은 문제인가. */
    public static final String DUPLICATE = "duplicate";

    /**
     * 가운데 단계를 "쓸 만하지만 다듬을 것"으로 두면 좋은 문제의 확률이 위 두 단계로 갈려 신뢰도가 낮게 나온다.
     * 단계마다 서로 다른 결함을 가리키게 해, 결함이 없는 문제가 맨 위 단계에 모이게 한다.
     */
    static final List<String> CLARITY_LEVELS = List.of(
            "무엇을 답해야 하는지 알 수 없거나, 서로 다른 답이 여럿 정답이 될 수 있다",
            "무엇을 묻는지는 알 수 있지만 `answerCriteria`가 그 질문의 답이 아니어서 채점할 수 없다",
            "무엇을 답해야 하는지 분명하고 `answerCriteria`로 채점할 수 있다");

    private QuestionQualityQuestions() {
    }

    public static Map<String, JevQuestion> questions() {
        return Map.of(
                GROUNDED, JevQuestion.noul(
                        "`answerCriteria`의 모든 기준은 `item`(기억 항목의 내용. `itemKind`가 CONFUSION이면 사용자가 잘못 믿었던 내용)이나 "
                                + "`correction`(대화 속 AI의 교정)에 적힌 내용과 같은 뜻인가? `itemKind`가 CONFUSION이면 정답의 근거는 `correction`이다. "
                                + "`evidence`의 사용자 발화는 질문의 맥락일 뿐이라 답이 들어 있지 않아도 된다. "
                                + "`question`이 새로운 상황을 제시하더라도 정답 기준이 `item`·`correction`의 내용이면 그렇다고 본다.",
                        "정답 기준이 기억 항목이나 교정의 내용과 같은 뜻이다",
                        "정답 기준이 기억 항목·교정에 없는 사실을 요구하거나 그 내용과 반대다"),
                CLARITY, JevQuestion.score(
                        "`question`(객관식이면 `choices`와 0부터 세는 정답 번호 `correctChoice` 포함)이 학습자에게 무엇을 답하라고 하는지 얼마나 분명한가? "
                                + "서술형은 답의 표현이 달라도 `answerCriteria`의 내용을 담으면 정답이므로, 표현이 여러 가지일 수 있다는 이유로 낮추지 않는다.",
                        CLARITY_LEVELS),
                DUPLICATE, JevQuestion.noul(
                        "`question`은 `existing`(같은 기억 항목의 기존 문제) 중 하나와 표현만 다를 뿐 사실상 같은 문제인가? `existing`이 비어 있으면 아니다.",
                        "기존 문제와 사실상 같다",
                        "기존 문제와 다르다"));
    }
}
