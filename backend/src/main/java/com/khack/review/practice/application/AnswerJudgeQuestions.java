package com.khack.review.practice.application;

import com.khack.review.common.application.port.out.JevQuestion;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 답변 판정 Jev 질문 정의 (스펙 §6.4.5 Jev 출력, docs/jev.md). 질문 문장·선택지 설명은 프롬프트이므로 ai 소유이며 이 초안을 다듬는다.
 * 판정 서비스({@link AnswerJudge})는 질문 이름과 {@code verdict} 선택지 이름에만 의존한다. 상태 필드는 {@link AnswerJudgeState}.
 */
public final class AnswerJudgeQuestions {

    /** choice: 정답 기준 충족 여부. 선택지 {@link #MET}·{@link #NOT_MET}·{@link #UNABLE_TO_JUDGE}. */
    public static final String VERDICT = "verdict";

    public static final String MET = "met";
    public static final String NOT_MET = "not_met";
    public static final String UNABLE_TO_JUDGE = "unable_to_judge";

    /** noul: 필수 내용이 빠졌는가. */
    public static final String OMISSION = "omission";

    /** noul: 평가 대상 기준을 부정하거나 틀리게 주장했는가. */
    public static final String CONTRADICTION = "contradiction";

    /** noul: 질문을 다르게 이해했는가. */
    public static final String MISREAD = "misread";

    /** noul: 틀린 주장이 대화 속 `userBelief`와 같은 내용인가. 헷갈린 지점 항목에서만 묻는다. */
    public static final String REPEATS_USER_BELIEF = "repeats_user_belief";

    /** noul: 평가 대상과 무관한 부분에 틀린 내용이 있는가. */
    public static final String OFF_TARGET_ERROR = "off_target_error";

    private AnswerJudgeQuestions() {
    }

    public static Map<String, JevQuestion> questions(boolean withUserBelief) {
        Map<String, @Nullable String> verdicts = new LinkedHashMap<>();
        verdicts.put(MET, "`answerCriteria`의 필수 기준을 모두 충족하고, 같은 답변 안에 기준과 모순되는 내용이 없다");
        verdicts.put(NOT_MET, "필수 기준이 빠졌거나, 기준을 부정하거나 틀리게 주장한다. 기준을 언급했더라도 같은 답변에서 부정하면 여기다");
        verdicts.put(UNABLE_TO_JUDGE, "답변이 너무 짧거나 모호하거나 질문이 모호해 판정할 수 없다");
        Map<String, JevQuestion> questions = new LinkedHashMap<>();
        questions.put(VERDICT, JevQuestion.choice(
                "`question`에 대한 학습자의 `answer`는 `answerCriteria`(참고: `modelAnswer`)를 충족하는가? 표현이 달라도 뜻이 같으면 충족이다.",
                verdicts));
        questions.put(OMISSION, JevQuestion.noul(
                "`answer`에 `answerCriteria`의 필수 내용 중 빠진 것이 있는가?",
                "필수 내용이 빠졌다", "필수 내용이 모두 있다"));
        questions.put(CONTRADICTION, JevQuestion.noul(
                "`answer`가 `answerCriteria`의 내용을 부정하거나 틀리게 주장하는가?",
                "기준과 반대되거나 틀린 주장이 있다", "기준과 반대되는 주장이 없다"));
        questions.put(MISREAD, JevQuestion.noul(
                "`answer`는 `question`이 묻는 것과 다른 것에 답하고 있는가(질문을 다르게 이해함)?",
                "질문을 다르게 이해했다", "질문이 묻는 것에 답했다"));
        if (withUserBelief) {
            questions.put(REPEATS_USER_BELIEF, JevQuestion.noul(
                    "`answer`의 틀린 주장이 학습자가 대화에서 믿었던 `userBelief`와 같은 내용인가?",
                    "대화에서 믿었던 틀린 내용을 다시 주장한다", "그 내용이 아니다"));
        }
        questions.put(OFF_TARGET_ERROR, JevQuestion.noul(
                "`answer`에 `answerCriteria`와 무관한 부분의 틀린 내용이 있는가?",
                "평가 대상 밖에 틀린 내용이 있다", "없다"));
        return questions;
    }
}
