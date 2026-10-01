package com.khack.review.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.question.domain.QuestionType;
import io.github.openspacedrepetition.Rating;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 스펙 §6.4.5 변환표. 기준 신뢰도 0.6, 단답 기준 시간 40초 × 1.5.
 * 결과는 등급(AGAIN·HARD·GOOD·EASY), HOLD:이유, NOT_EVALUATED 중 하나다. 응답 시간 빈 칸은 모름.
 */
class RatingPolicyTest {

    static final RatingPolicy POLICY = new RatingPolicy(0.6,
            Map.of(QuestionType.SHORT_ANSWER, Duration.ofSeconds(40), QuestionType.MULTIPLE_CHOICE, Duration.ofSeconds(20)), 1.5);

    @ParameterizedTest(name = "[{index}] {10}: {0} {1}({2}) misread={3}({4}) guess={5} {6} {7} {8}s → {9}")
    @CsvSource(delimiter = '|', textBlock = """
            # attempt         | verdict         | conf | misread | m.conf | guess | self                 | type            | sec | expected               | 사례
            # 행 1
            FIRST_UNASSISTED  | UNABLE_TO_JUDGE | 0.9  | false   | 0      | false | RECALLED_EASILY      | SHORT_ANSWER    | 10  | HOLD:UNABLE_TO_JUDGE   | 판정 불가
            FIRST_UNASSISTED  | MET             | 0.5  | false   | 0      | false | RECALLED_EASILY      | SHORT_ANSWER    | 10  | HOLD:LOW_CONFIDENCE    | 신뢰도 미만 정답
            FIRST_UNASSISTED  | NOT_MET         | 0.5  | false   | 0      | false | RECALLED_EASILY      | SHORT_ANSWER    | 10  | HOLD:LOW_CONFIDENCE    | 신뢰도 미만 오답도 보류
            FIRST_UNASSISTED  | NOT_MET         | 0.5  | false   | 0      | false | GUESSED              | SHORT_ANSWER    | 10  | HOLD:LOW_CONFIDENCE    | 행 1이 추측 자기평가(행 4)보다 먼저
            # 행 2
            FIRST_UNASSISTED  | NOT_MET         | 0.9  | true    | 0.8    | false | RECALLED_EASILY      | SHORT_ANSWER    | 10  | HOLD:MISREAD           | 질문 오독 확인
            FIRST_UNASSISTED  | NOT_MET         | 0.9  | true    | 0.6    | false | RECALLED_EASILY      | SHORT_ANSWER    | 10  | HOLD:MISREAD           | 오독 신뢰도가 기준과 같음
            # 행 3
            FIRST_UNASSISTED  | MET             | 1.0  | false   | 0      | true  | RECALLED_EASILY      | MULTIPLE_CHOICE | 2   | HOLD:GUESS_UNCONFIRMED | 너무 빨리 고른 객관식 정답
            FIRST_UNASSISTED  | MET             | 1.0  | false   | 0      | true  | RECALLED_WITH_EFFORT | MULTIPLE_CHOICE | 2   | HOLD:GUESS_UNCONFIRMED | 행 3이 Hard(행 5)보다 먼저
            # 행 4
            FIRST_UNASSISTED  | NOT_MET         | 0.9  | false   | 0      | false | RECALLED_EASILY      | SHORT_ANSWER    | 10  | AGAIN                  | 오답
            FIRST_UNASSISTED  | NOT_MET         | 0.9  | true    | 0.4    | false | RECALLED_EASILY      | SHORT_ANSWER    | 10  | AGAIN                  | 오독 신뢰도 부족이면 Again
            FIRST_UNASSISTED  | MET             | 0.9  | false   | 0      | false | GUESSED              | SHORT_ANSWER    | 10  | AGAIN                  | 정답이지만 추측했음
            FIRST_UNASSISTED  | MET             | 1.0  | false   | 0      | true  | GUESSED              | MULTIPLE_CHOICE | 2   | AGAIN                  | 추측 의심을 자기평가가 확인
            FIRST_UNASSISTED  | NOT_MET         | 0.9  | false   | 0      | false | RECALLED_WITH_EFFORT | SHORT_ANSWER    | 10  | AGAIN                  | 실패에 Hard를 쓰지 않음
            # 행 5
            FIRST_UNASSISTED  | MET             | 0.9  | false   | 0      | false | RECALLED_WITH_EFFORT | SHORT_ANSWER    | 10  | HARD                   | 힘들게 떠올림
            FIRST_UNASSISTED  | MET             | 1.0  | false   | 0      | false | RECALLED_WITH_EFFORT | MULTIPLE_CHOICE | 10  | HARD                   | 객관식도 Hard 가능
            # 행 6
            FIRST_UNASSISTED  | MET             | 0.9  | false   | 0      | false | RECALLED_EASILY      | SHORT_ANSWER    | 60  | EASY                   | 기준 시간 1.5배 이내
            DELAYED_RECHECK   | MET             | 0.9  | false   | 0      | false | RECALLED_EASILY      | SHORT_ANSWER    | 10  | EASY                   | 같은 날 재확인도 평가
            # 행 7
            FIRST_UNASSISTED  | MET             | 0.9  | false   | 0      | false | RECALLED_EASILY      | SHORT_ANSWER    | 61  | GOOD                   | 기준 시간을 크게 넘음
            FIRST_UNASSISTED  | MET             | 1.0  | false   | 0      | false | RECALLED_EASILY      | MULTIPLE_CHOICE | 5   | GOOD                   | 객관식은 Easy 없음
            FIRST_UNASSISTED  | MET             | 0.9  | false   | 0      | false | RECALLED_EASILY      | SHORT_ANSWER    |     | GOOD                   | 응답 시간 모름
            FIRST_UNASSISTED  | MET             | 0.9  | false   | 0      | false | RECALLED_EASILY      | ESSAY           | 10  | GOOD                   | 기준 시간이 없는 유형
            # 평가 대상 아님
            ASSISTED_RETRY    | MET             | 0.9  | false   | 0      | false | RECALLED_EASILY      | SHORT_ANSWER    | 10  | NOT_EVALUATED          | 힌트 후 재시도
            ASSISTED_RETRY    | UNABLE_TO_JUDGE | 0.1  | false   | 0      | false | GUESSED              | SHORT_ANSWER    | 10  | NOT_EVALUATED          | 재시도는 판정과 무관
            """)
    void conversionTable(AttemptKind attempt, AnswerVerdict verdict, double confidence, boolean misread, double misreadConfidence,
            boolean guess, SelfAssessment self, QuestionType type, Integer seconds, String expected, String scenario) {
        RatingDecision decision = POLICY.decide(new RatingInput(attempt, verdict, confidence, misread, misreadConfidence, guess,
                self, type, seconds == null ? null : Duration.ofSeconds(seconds)));

        assertThat(describe(decision)).as(scenario).isEqualTo(expected);
        assertThat(decision.policyVersion()).isEqualTo(RatingPolicy.VERSION);
    }

    @ParameterizedTest
    @CsvSource({"UNABLE_TO_JUDGE, 1", "MISREAD, 2", "GUESS_UNCONFIRMED, 3", "LOW_CONFIDENCE, 1"})
    void holdsReportTheirRowAndFollowUp(HoldReason reason, int row) {
        RatingDecision decision = POLICY.decide(switch (reason) {
            case UNABLE_TO_JUDGE -> input(AnswerVerdict.UNABLE_TO_JUDGE, 0.9, false, 0, false, SelfAssessment.RECALLED_EASILY);
            case LOW_CONFIDENCE -> input(AnswerVerdict.MET, 0.1, false, 0, false, SelfAssessment.RECALLED_EASILY);
            case MISREAD -> input(AnswerVerdict.NOT_MET, 0.9, true, 0.9, false, SelfAssessment.RECALLED_EASILY);
            case GUESS_UNCONFIRMED -> input(AnswerVerdict.MET, 1.0, false, 0, true, SelfAssessment.RECALLED_EASILY);
        });

        assertThat(decision).isEqualTo(new RatingDecision.Held(reason, row, RatingPolicy.VERSION));
        assertThat(reason.questionNeedsRecheck()).isEqualTo(reason == HoldReason.UNABLE_TO_JUDGE || reason == HoldReason.MISREAD);
        assertThat(reason.asksForReason()).isEqualTo(reason == HoldReason.GUESS_UNCONFIRMED);
    }

    @ParameterizedTest
    @CsvSource({"AGAIN, 4", "HARD, 5", "EASY, 6", "GOOD, 7"})
    void ratingsReportTheirRow(Rating rating, int row) {
        RatingInput input = switch (rating) {
            case AGAIN -> input(AnswerVerdict.NOT_MET, 0.9, false, 0, false, SelfAssessment.RECALLED_EASILY);
            case HARD -> input(AnswerVerdict.MET, 0.9, false, 0, false, SelfAssessment.RECALLED_WITH_EFFORT);
            case EASY -> input(AnswerVerdict.MET, 0.9, false, 0, false, SelfAssessment.RECALLED_EASILY);
            case GOOD -> new RatingInput(AttemptKind.FIRST_UNASSISTED, AnswerVerdict.MET, 0.9, false, 0, false,
                    SelfAssessment.RECALLED_EASILY, QuestionType.SHORT_ANSWER, Duration.ofMinutes(5));
        };

        assertThat(POLICY.decide(input)).isEqualTo(new RatingDecision.Rated(rating, row, RatingPolicy.VERSION));
    }

    static RatingInput input(AnswerVerdict verdict, double confidence, boolean misread, double misreadConfidence, boolean guess,
            SelfAssessment self) {
        return new RatingInput(AttemptKind.FIRST_UNASSISTED, verdict, confidence, misread, misreadConfidence, guess, self,
                QuestionType.SHORT_ANSWER, Duration.ofSeconds(10));
    }

    static String describe(RatingDecision decision) {
        return switch (decision) {
            case RatingDecision.Rated rated -> rated.rating().name();
            case RatingDecision.Held held -> "HOLD:" + held.reason().name();
            case RatingDecision.NotEvaluated ignored -> "NOT_EVALUATED";
        };
    }
}
