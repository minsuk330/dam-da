package com.khack.review.question.application;

import com.khack.review.common.application.port.out.JevQuestion;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사실 주장과 외부 출처 대조 Jev 질문 정의(#178, docs/jev.md). 질문 문장·선택지 설명은 프롬프트이므로 ai 소유다.
 * 출처 하나씩 따로 묻는다({@link ClaimSourceJudge.SourceState}). 출처 여러 개를 한 상태에 넣으면, 한 질문에 묻든 출처 번호로
 * 나눠 묻든 관련 없는 출처나 서로 다른 출처에 판정이 흔들렸다(2026-11-03 어려운 사례 벤치마크).
 * 판정 서비스({@link ClaimSourceJudge})는 질문 이름과 선택지 키에만 의존한다.
 */
public final class ClaimSourceQuestions {

    /** choice: 출처가 주장을 어떻게 다루는가. */
    public static final String SUPPORT = "support";

    public static final String SUPPORTED = "supported";
    public static final String CONTRADICTED = "contradicted";
    public static final String NOT_ADDRESSED = "not_addressed";

    private ClaimSourceQuestions() {
    }

    public static Map<String, JevQuestion> questions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(SUPPORTED, "출처가 주장의 핵심 내용을 그대로 말하거나 분명히 뒷받침한다");
        options.put(CONTRADICTED, "출처가 주장과 반대되거나 양립할 수 없는 내용을 말한다(조건·범위·수치가 다른 경우 포함)");
        options.put(NOT_ADDRESSED, "출처가 주장을 다루지 않거나, 맞는지 틀리는지 판단할 만큼 말하지 않는다");
        return Map.of(SUPPORT, JevQuestion.choice(
                "`source`의 `text`만 근거로 할 때 `claim`은 어떤가? 출처에 없는 배경지식으로 판단하지 않는다.",
                options));
    }
}
