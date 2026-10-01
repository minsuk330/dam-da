package com.khack.review.practice.application;

import com.khack.review.common.application.port.out.JevQuestion;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 답변 판정 Jev 질문 정의 (스펙 §6.2, §6.4.5 Jev 출력, docs/jev.md). 질문 문장·선택지 설명은 프롬프트이므로 ai 소유다.
 * 바꿀 때는 {@code ./gradlew -q answerJudgeCheck}로 실제 확률·신뢰도 분포를 본다.
 * 판정 서비스({@link AnswerJudge})는 질문 이름과 선택지 이름에만 의존한다. 상태 필드는 {@link AnswerJudgeState}.
 *
 * <p>설계 원칙
 * <ul>
 *   <li>정오는 {@code answerCriteria}만으로 정한다. {@code modelAnswer}는 참고용이라 거기에만 있는 내용을 필수로 삼지 않고,
 *       {@code item}·{@code userBelief}·{@code correction}은 배경이다. 대화 당시의 AI 판정은 상태에 넣지 않는다.</li>
 *   <li>예/아니오(noul)는 확률만 돌려주고 신뢰도가 없다. 신뢰도가 필요한 {@code verdict}와 {@code misread}만 선택형(choice)으로 묻는다.</li>
 *   <li>관련 질문은 한 번의 호출에 묶되, 각 질문은 다른 질문의 답과 상관없이 독립적으로 판단하게 한다.</li>
 * </ul>
 */
public final class AnswerJudgeQuestions {

    /** choice: 정답 기준 충족 여부. 선택지 {@link #MET}·{@link #NOT_MET}·{@link #UNABLE_TO_JUDGE}. */
    public static final String VERDICT = "verdict";

    public static final String MET = "met";
    public static final String NOT_MET = "not_met";
    public static final String UNABLE_TO_JUDGE = "unable_to_judge";

    /** noul: 필수 기준 중 답변에 없는 것이 있는가. */
    public static final String OMISSION = "omission";

    /** noul: 평가 대상 기준을 부정하거나 틀리게 주장했는가. */
    public static final String CONTRADICTION = "contradiction";

    /**
     * choice: 질문을 다르게 이해했는가. 선택지 {@link #MISREAD_CHOICE}·{@link #AS_ASKED}·{@link #QUESTION_UNCLEAR}.
     * 신뢰도가 필요해 noul이 아닌 choice로 묻는다(§6.4.5: 신뢰도는 `verdict`와 `misread` 각각).
     */
    public static final String MISREAD = "misread";

    public static final String MISREAD_CHOICE = "misread";
    public static final String AS_ASKED = "answered_as_asked";
    public static final String QUESTION_UNCLEAR = "question_unclear";

    /** noul: 틀린 주장이 대화 속 `userBelief`와 같은 내용인가. 헷갈린 지점 항목에서만 묻는다. */
    public static final String REPEATS_USER_BELIEF = "repeats_user_belief";

    /** noul: 평가 대상과 무관한 부분에 틀린 내용이 있는가. */
    public static final String OFF_TARGET_ERROR = "off_target_error";

    private AnswerJudgeQuestions() {
    }

    public static Map<String, JevQuestion> questions(boolean withUserBelief) {
        Map<String, @Nullable String> verdicts = new LinkedHashMap<>();
        verdicts.put(MET, "`answerCriteria`의 필수 기준이 모두 `answer`에 뜻으로 들어 있고, `answer`에 그 기준을 부정하거나 틀리게 주장하는 내용이 없다. "
                + "표현이 `modelAnswer`와 달라도 뜻이 같으면 충족이다. 기준과 상관없는 부분의 오류나 `modelAnswer`에만 있는 내용의 빠짐은 영향을 주지 않는다");
        verdicts.put(NOT_MET, "`answer`의 뜻은 알아볼 수 있는데 `answerCriteria`의 필수 기준이 하나라도 빠졌거나(모른다고 답한 경우 포함) "
                + "기준을 부정하거나 틀리게 주장한다. 일부만 맞힌 답이나, 기준을 언급하고도 같은 답변에서 부정한 답도 여기다");
        verdicts.put(UNABLE_TO_JUDGE, "`answer`의 뜻을 알아볼 수 없거나, `question`이나 `answerCriteria`가 모호해서 충족 여부를 신뢰성 있게 판단할 수 없다. "
                + "필수 기준이 빠진 것이 분명한 답은 여기가 아니라 not_met이다");

        Map<String, @Nullable String> misreads = new LinkedHashMap<>();
        misreads.put(MISREAD_CHOICE, "`question`은 분명한데 `answer`가 `question`이 묻는 것과 다른 것(다른 개념, 다른 질문)에 답하고 있다. "
                + "묻는 것에 답하려다 내용이 틀렸거나 모른다고 답한 경우는 여기가 아니다");
        misreads.put(AS_ASKED, "`answer`가 `question`이 묻는 것에 답하려 한 것이다. 내용이 틀리거나 부족해도 여기다");
        misreads.put(QUESTION_UNCLEAR, "`question` 자체가 모호하거나 여러 뜻으로 읽혀서, `answer`가 질문을 다르게 이해한 것인지 알 수 없다. "
                + "이 경우는 학습자의 오해로 보지 않는다");

        Map<String, JevQuestion> questions = new LinkedHashMap<>();
        questions.put(VERDICT, JevQuestion.choice(
                "`question`에 대한 학습자의 `answer`가 `answerCriteria`(필수 정답 요소)를 충족하는가? 정오는 `answerCriteria`만으로 정한다. "
                        + "`modelAnswer`는 참고용 예시이고, `item`·`userBelief`·`correction`은 배경이라 정오의 기준이 아니다. "
                        + "`answerCriteria`에 없는 지식을 새 필수 조건으로 요구하지 않는다.",
                verdicts));
        questions.put(OMISSION, JevQuestion.noul(
                "`answerCriteria`의 필수 기준을 하나씩 대조했을 때, `answer`에 뜻으로 들어 있지 않은 기준이 있는가? "
                        + "`answer`가 정답인지 오답인지와 상관없이 기준의 빠짐만 본다. `modelAnswer`에만 있고 `answerCriteria`에는 없는 내용의 빠짐은 세지 않는다.",
                "기준 중 `answer`에 없는 것이 있다", "모든 기준이 `answer`에 들어 있다"));
        questions.put(CONTRADICTION, JevQuestion.noul(
                "`answer`가 `answerCriteria`의 내용을 부정하거나 그와 반대로 주장하는가? 올바른 내용을 말했더라도 같은 답변에서 그것을 부정하거나 뒤집으면 그렇다. "
                        + "기준이 다루지 않는 일을 틀리게 말한 것이나, 기준이 단지 빠진 것은 해당하지 않는다.",
                "기준을 부정하거나 틀리게 주장한다", "기준과 어긋나는 주장이 없다"));
        questions.put(MISREAD, JevQuestion.choice(
                "`answer`는 `question`이 묻는 것에 대한 답인가, 아니면 질문을 다른 뜻으로 이해해 다른 것에 답한 것인가? "
                        + "`question`이 모호해서 판단할 수 없으면 학습자의 오해로 단정하지 않는다. 내용이 맞는지는 묻지 않는다.",
                misreads));
        if (withUserBelief) {
            questions.put(REPEATS_USER_BELIEF, JevQuestion.noul(
                    "`answer`에 `answerCriteria`와 어긋나는 주장이 있다면, 그 주장이 학습자가 대화에서 믿었던 `userBelief`와 같은 내용인가? "
                            + "`answer`가 기준과 어긋나지 않거나 `userBelief`와 다른 내용을 틀리게 말했다면 아니다.",
                    "대화에서 믿었던 틀린 내용을 다시 주장한다", "그 내용이 아니다"));
        }
        questions.put(OFF_TARGET_ERROR, JevQuestion.noul(
                "먼저 `answer`에서 `answerCriteria`의 내용을 다루거나 그것과 어긋나는 문장을 모두 제외한다. 남은 문장 중에 틀린 주장이 있는가? "
                        + "제외한 문장이 틀렸더라도 여기에 세지 않는다.",
                "평가 대상 밖에 틀린 내용이 있다", "없다"));
        return questions;
    }
}
