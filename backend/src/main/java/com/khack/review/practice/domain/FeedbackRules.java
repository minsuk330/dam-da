package com.khack.review.practice.domain;

import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 단계적 피드백의 상태 기계 (스펙 §7 5단계, §6.2, §6.4.8). 시도 결과와 지금까지 보여 준 도움으로 다음에 할 수 있는 행동을 정한다.
 * 선택지가 하나면 코드가 정하고, 둘 이상이면 Jev가 그 안에서 고른다. 어느 경우도 FSRS 등급에는 관여하지 않는다.
 *
 * <pre>
 * 첫 학습  오답 → give_hint → (재도전) → explain_concept → (확인 문제가 큐 끝에 편성됨) → advance
 *          오답 → explain_concept(힌트 생략) 도 가능. 힌트 후 정답이면 경로만 기록한다(relearn_today 선택 가능).
 * 매일 학습 오답 → relearn_today(그날 큐 끝에 한 번 더) / explain_concept(보여 주기만) → advance
 * 재확인 제시(지연된 무도움 재확인)는 다시 편성하지 않는다. 한 번뿐이다.
 * </pre>
 */
public final class FeedbackRules {

    private FeedbackRules() {
    }

    /**
     * @param kind                        풀이 세션 종류
     * @param recheck                     같은 날 재확인 제시인가
     * @param outcome                     마지막 시도의 결과
     * @param aidSeen                     이 제시에서 내용 도움(힌트·설명)을 본 적이 있는가
     * @param hintShown                   힌트를 보여 줬는가
     * @param explanationShown            설명을 보여 줬는가
     * @param contentAidAfterLatestAttempt 마지막 시도 뒤에 내용 도움을 보여 줘 재도전을 기다리는가
     * @param relearnQueued               이 제시를 이미 오늘 큐 끝에 다시 넣었는가
     * @param relearnCapReached           풀이 세션의 다시 묻기 상한에 닿았는가
     */
    public record State(PracticeKind kind, boolean recheck, AttemptOutcome outcome, boolean aidSeen, boolean hintShown,
            boolean explanationShown, boolean contentAidAfterLatestAttempt, boolean relearnQueued, boolean relearnCapReached) {
    }

    /** 할 수 있는 행동과, 판정 없이 또는 판정이 믿을 수 없을 때 쓰는 기본 행동. */
    public record Plan(Set<FeedbackAction> allowed, FeedbackAction fallback) {

        public boolean needsJudgment() {
            return allowed.size() > 1;
        }
    }

    public static Plan plan(State s) {
        switch (s.outcome()) {
            case UNCERTAIN:
                return only(FeedbackAction.REQUEST_CONFIRMATION);
            case QUESTION_AMBIGUOUS:
                return only(s.relearnQueued() || s.recheck() ? FeedbackAction.ADVANCE : FeedbackAction.GENERATE_VARIANT);
            case CORRECT:
                return correct(s);
            case WRONG:
            default:
                return wrong(s);
        }
    }

    private static Plan correct(State s) {
        // 힌트·설명을 보고 맞힌 첫 시도 뒤에는 오늘 한 번 더 확인한다. 첫 학습은 도움 뒤 정답이면 항상 다른 문제 몇 개 뒤에
        // 확인 문제를 낸다(스펙 §7 5단계, §11.3 7단계). 매일 학습은 다시 물을지 고른다.
        if (s.aidSeen() && !s.recheck() && !s.relearnQueued() && !s.relearnCapReached()) {
            return s.kind() == PracticeKind.FIRST_STUDY ? only(FeedbackAction.RELEARN_TODAY)
                    : new Plan(EnumSet.of(FeedbackAction.ADVANCE, FeedbackAction.RELEARN_TODAY), FeedbackAction.ADVANCE);
        }
        return only(FeedbackAction.ADVANCE);
    }

    private static Plan wrong(State s) {
        if (s.recheck()) {
            return only(FeedbackAction.ADVANCE);
        }
        if (s.contentAidAfterLatestAttempt()) {
            return only(FeedbackAction.RETRY);
        }
        if (s.kind() == PracticeKind.FIRST_STUDY) {
            if (s.explanationShown()) {
                return only(FeedbackAction.ADVANCE);
            }
            return s.hintShown() ? only(FeedbackAction.EXPLAIN_CONCEPT)
                    : new Plan(EnumSet.of(FeedbackAction.GIVE_HINT, FeedbackAction.EXPLAIN_CONCEPT), FeedbackAction.GIVE_HINT);
        }
        // 매일 학습: 시간 예산을 지키려고 확인 문제 대신 틀린 항목을 그날 큐 끝에 한 번 더 낸다.
        Set<FeedbackAction> allowed = EnumSet.noneOf(FeedbackAction.class);
        boolean canRelearn = !s.relearnQueued() && !s.relearnCapReached();
        if (canRelearn) {
            allowed.add(FeedbackAction.RELEARN_TODAY);
        }
        if (!s.explanationShown()) {
            allowed.add(FeedbackAction.EXPLAIN_CONCEPT);
        }
        if (!canRelearn) {
            allowed.add(FeedbackAction.ADVANCE);
        }
        return new Plan(allowed, canRelearn ? FeedbackAction.RELEARN_TODAY : FeedbackAction.ADVANCE);
    }

    private static Plan only(FeedbackAction action) {
        return new Plan(EnumSet.of(action), action);
    }

    /**
     * 풀이 경로 (표시용). 마지막 시도가 정답이면 그 전에 본 가장 센 도움으로, 오답이 두 번 이상이면 반복 오답이다. 아직 모르면 null.
     */
    public static @Nullable FeedbackPath path(AttemptOutcome latest, int wrongAttempts, boolean hintBeforeLatest,
            boolean explanationBeforeLatest) {
        if (latest == AttemptOutcome.CORRECT) {
            return explanationBeforeLatest ? FeedbackPath.AFTER_EXPLANATION
                    : hintBeforeLatest ? FeedbackPath.AFTER_HINT : FeedbackPath.INDEPENDENT;
        }
        return wrongAttempts >= 2 ? FeedbackPath.REPEATED_WRONG : null;
    }
}
