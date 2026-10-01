package com.khack.review.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.memory.domain.DailyQueuePlanner.CarryReason;
import com.khack.review.memory.domain.DailyQueuePlanner.NewCandidate;
import com.khack.review.memory.domain.DailyQueuePlanner.Plan;
import com.khack.review.memory.domain.DailyQueuePlanner.Policy;
import com.khack.review.memory.domain.DailyQueuePlanner.ReviewCandidate;
import com.khack.review.memory.domain.DailyQueuePlanner.Source;
import com.khack.review.question.domain.QuestionType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 매일 학습 큐 계획: 후보·우선순위·시간 예산·하루 1개·신규 분리 (스펙 §6.4.4). */
class DailyQueuePlannerTest {

    static final Map<QuestionType, Duration> TIMES = Map.of(
            QuestionType.MULTIPLE_CHOICE, Duration.ofSeconds(20),
            QuestionType.SHORT_ANSWER, Duration.ofSeconds(40),
            QuestionType.ESSAY, Duration.ofSeconds(90),
            QuestionType.ERROR_FINDING, Duration.ofSeconds(60),
            QuestionType.CASE_JUDGMENT, Duration.ofSeconds(90),
            QuestionType.CASE_APPLICATION, Duration.ofSeconds(120));

    static final Policy POLICY = policy(Duration.ofMinutes(5));

    static Policy policy(Duration budget) {
        return new Policy(budget, 3, Map.of(MemoryItemKind.CONFUSION, 3.0, MemoryItemKind.WARNING, 2.0,
                MemoryItemKind.FACT, 1.0, MemoryItemKind.PRACTICE, 1.0), 0.5, TIMES);
    }

    /** 목표 유지율 0.9 항목. 단위는 항목마다 다르다(id = 단위 id). */
    static ReviewCandidate review(long id, MemoryItemKind kind, Double r, QuestionType type) {
        return new ReviewCandidate(id, id, 1L, kind, r, 0.9, false, false, type, null);
    }

    static ReviewCandidate review(long id, long unit, MemoryItemKind kind, Double r) {
        return new ReviewCandidate(id, unit, 1L, kind, r, 0.9, false, false, QuestionType.SHORT_ANSWER, null);
    }

    static NewCandidate fresh(long id, long unit, MemoryItemKind kind, QuestionType type) {
        return new NewCandidate(id, unit, 2L, kind, type);
    }

    static List<Long> ids(Plan plan) {
        return plan.entries().stream().map(DailyQueuePlanner.Entry::itemId).toList();
    }

    @Test
    void candidatesAreDueHeldOrRelearnOnly() {
        ReviewCandidate due = review(1, MemoryItemKind.FACT, 0.85, QuestionType.SHORT_ANSWER);
        ReviewCandidate fine = review(2, MemoryItemKind.FACT, 0.95, QuestionType.SHORT_ANSWER);
        ReviewCandidate exactlyAtTarget = review(3, MemoryItemKind.FACT, 0.9, QuestionType.SHORT_ANSWER);
        ReviewCandidate unratedPlain = review(4, MemoryItemKind.FACT, null, QuestionType.SHORT_ANSWER);
        ReviewCandidate held = new ReviewCandidate(5L, 5L, 1L, MemoryItemKind.FACT, 0.97, 0.9, true, false,
                QuestionType.SHORT_ANSWER, null);
        ReviewCandidate unratedHeld = new ReviewCandidate(6L, 6L, 1L, MemoryItemKind.FACT, null, 0.9, true, false,
                QuestionType.MULTIPLE_CHOICE, null);
        ReviewCandidate relearn = new ReviewCandidate(7L, 7L, 1L, MemoryItemKind.FACT, 0.99, 0.9, false, true,
                QuestionType.SHORT_ANSWER, null);

        Plan plan = DailyQueuePlanner.plan(List.of(due, fine, exactlyAtTarget, unratedPlain, held, unratedHeld, relearn),
                List.of(), Set.of(), POLICY);

        assertThat(ids(plan)).containsExactlyInAnyOrder(1L, 5L, 6L, 7L);
        assertThat(plan.entries()).allMatch(e -> e.source() == Source.REVIEW);
        assertThat(plan.reviewCount()).isEqualTo(4);
        assertThat(plan.newCount()).isZero();
    }

    @Test
    void ordersByUrgencyTimesKindWeight() {
        // 우선순위: fact R0.3 → 0.7, confusion R0.8 → 0.6, warning R0.5 → 1.0, practice R0.89 → 0.11
        Plan plan = DailyQueuePlanner.plan(List.of(
                review(1, MemoryItemKind.FACT, 0.3, QuestionType.SHORT_ANSWER),
                review(2, MemoryItemKind.CONFUSION, 0.8, QuestionType.SHORT_ANSWER),
                review(3, MemoryItemKind.WARNING, 0.5, QuestionType.SHORT_ANSWER),
                review(4, MemoryItemKind.PRACTICE, 0.89, QuestionType.SHORT_ANSWER)), List.of(), Set.of(), POLICY);

        assertThat(ids(plan)).containsExactly(3L, 1L, 2L, 4L);
        assertThat(plan.entries().get(0).priority()).isEqualTo(1.0);
    }

    @Test
    void sameRetrievabilityPrefersConfusionThenWarningThenFact() {
        Plan plan = DailyQueuePlanner.plan(List.of(
                review(1, MemoryItemKind.FACT, 0.7, QuestionType.SHORT_ANSWER),
                review(2, MemoryItemKind.WARNING, 0.7, QuestionType.SHORT_ANSWER),
                review(3, MemoryItemKind.CONFUSION, 0.7, QuestionType.SHORT_ANSWER),
                review(4, MemoryItemKind.PRACTICE, 0.7, QuestionType.SHORT_ANSWER)), List.of(), Set.of(), POLICY);

        assertThat(ids(plan).subList(0, 3)).containsExactly(3L, 2L, 1L);
    }

    @Test
    void heldItemHasMinimumUrgencyAndRelearnHasFullUrgency() {
        ReviewCandidate held = new ReviewCandidate(1L, 1L, 1L, MemoryItemKind.FACT, 0.99, 0.9, true, false,
                QuestionType.SHORT_ANSWER, null);
        ReviewCandidate relearn = new ReviewCandidate(2L, 2L, 1L, MemoryItemKind.FACT, 0.99, 0.9, false, true,
                QuestionType.SHORT_ANSWER, null);
        ReviewCandidate due = review(3, MemoryItemKind.FACT, 0.7, QuestionType.SHORT_ANSWER);

        Plan plan = DailyQueuePlanner.plan(List.of(held, relearn, due), List.of(), Set.of(), POLICY);

        assertThat(ids(plan)).containsExactly(2L, 1L, 3L);
        assertThat(plan.entries().get(0).priority()).isEqualTo(1.0);
        assertThat(plan.entries().get(1).priority()).isEqualTo(0.5);
    }

    @Test
    void onlyOneItemPerReviewUnitPerDay() {
        Plan plan = DailyQueuePlanner.plan(List.of(
                review(1, 10, MemoryItemKind.FACT, 0.6),
                review(2, 10, MemoryItemKind.WARNING, 0.6),
                review(3, 11, MemoryItemKind.FACT, 0.8)), List.of(), Set.of(), POLICY);

        assertThat(ids(plan)).containsExactly(2L, 3L);
        assertThat(plan.carriedOver()).containsExactly(new DailyQueuePlanner.Carried(1L, CarryReason.SAME_UNIT));
    }

    @Test
    void unitAlreadyServedTodayIsBlockedExceptRelearn() {
        ReviewCandidate sibling = review(1, 10, MemoryItemKind.FACT, 0.5);
        ReviewCandidate relearn = new ReviewCandidate(2L, 10L, 1L, MemoryItemKind.FACT, 0.99, 0.9, false, true,
                QuestionType.SHORT_ANSWER, null);
        ReviewCandidate other = review(3, 11, MemoryItemKind.FACT, 0.5);

        Plan blocked = DailyQueuePlanner.plan(List.of(sibling, other), List.of(), Set.of(10L), POLICY);
        Plan withRelearn = DailyQueuePlanner.plan(List.of(sibling, relearn, other), List.of(), Set.of(10L), POLICY);

        assertThat(ids(blocked)).containsExactly(3L);
        assertThat(ids(withRelearn)).containsExactly(2L, 3L);
        assertThat(withRelearn.carriedOver()).containsExactly(new DailyQueuePlanner.Carried(1L, CarryReason.SAME_UNIT));
    }

    @Test
    void dedupedSiblingDoesNotRefillFromBudgetCut() {
        // 단위 10의 높은 우선순위 항목이 예산에서 잘려도 같은 단위 형제가 대신 들어오지 않는다.
        Policy tight = policy(Duration.ofSeconds(100));
        Plan plan = DailyQueuePlanner.plan(List.of(
                review(1, 11, MemoryItemKind.CONFUSION, 0.1),
                new ReviewCandidate(2L, 10L, 1L, MemoryItemKind.WARNING, 0.2, 0.9, false, false, QuestionType.CASE_JUDGMENT, null),
                review(3, 10, MemoryItemKind.FACT, 0.3)), List.of(), Set.of(), tight);

        assertThat(ids(plan)).containsExactly(1L);
        assertThat(plan.carriedOver()).extracting(DailyQueuePlanner.Carried::itemId).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void estimatedTimeIsTheSumOfTypeReferenceTimesAndCutsAtBudget() {
        // 120s + 90s + 60s = 270s, 다음 40s는 310s라 예산 300s를 넘어 잘린다.
        Plan plan = DailyQueuePlanner.plan(List.of(
                review(1, MemoryItemKind.PRACTICE, 0.1, QuestionType.CASE_APPLICATION),
                review(2, MemoryItemKind.WARNING, 0.2, QuestionType.CASE_JUDGMENT),
                review(3, MemoryItemKind.CONFUSION, 0.3, QuestionType.ERROR_FINDING),
                review(4, MemoryItemKind.FACT, 0.4, QuestionType.SHORT_ANSWER)), List.of(), Set.of(), POLICY);

        assertThat(plan.entries()).hasSize(3);
        assertThat(plan.estimatedTime()).isEqualTo(Duration.ofSeconds(270));
        assertThat(plan.carriedOver()).extracting(DailyQueuePlanner.Carried::reason).containsExactly(CarryReason.BUDGET);
    }

    @Test
    void cutIsStrictByPriorityEvenIfALaterItemWouldFit() {
        Policy tight = policy(Duration.ofSeconds(150));
        Plan plan = DailyQueuePlanner.plan(List.of(
                review(1, MemoryItemKind.PRACTICE, 0.1, QuestionType.CASE_APPLICATION),
                review(2, MemoryItemKind.PRACTICE, 0.2, QuestionType.CASE_APPLICATION),
                review(3, MemoryItemKind.PRACTICE, 0.3, QuestionType.MULTIPLE_CHOICE)), List.of(), Set.of(), tight);

        assertThat(ids(plan)).containsExactly(1L);
        assertThat(plan.carriedOver()).hasSize(2);
    }

    @Test
    void firstItemIsKeptEvenIfItExceedsTheBudget() {
        Plan plan = DailyQueuePlanner.plan(List.of(review(1, MemoryItemKind.PRACTICE, 0.1, QuestionType.CASE_APPLICATION)),
                List.of(), Set.of(), policy(Duration.ofSeconds(30)));

        assertThat(ids(plan)).containsExactly(1L);
    }

    @Test
    void backlogAfterABreakStaysWithinTheDailyCapAndCarriesOver() {
        // 며칠 쉬어 쌓인 20개: 5분 예산(단답 40초 x 7 = 280초)만 낸다. 나머지는 사라지지 않고 이월 목록에 남는다.
        List<ReviewCandidate> backlog = new java.util.ArrayList<>();
        for (long id = 1; id <= 20; id++) {
            backlog.add(review(id, MemoryItemKind.FACT, 0.2 + id * 0.01, QuestionType.SHORT_ANSWER));
        }

        Plan plan = DailyQueuePlanner.plan(backlog, List.of(), Set.of(), POLICY);

        assertThat(plan.entries()).hasSize(7);
        assertThat(plan.estimatedTime()).isLessThanOrEqualTo(Duration.ofMinutes(5));
        assertThat(plan.carriedOver()).hasSize(13).allMatch(c -> c.reason() == CarryReason.BUDGET);
        assertThat(ids(plan)).as("R이 낮은(급한) 항목부터").startsWith(1L, 2L, 3L);
    }

    @Test
    void newItemsComeAfterReviewsAndOnlyFillTheRemainingBudget() {
        // 복습 3개(40s x 3 = 120s) 뒤 남은 180s에 신규. 신규끼리는 헷갈린 지점 > warning > fact.
        // 신규 60s + 90s = 150s는 들어가고, 다음 40s는 310s라 예산을 넘어 잘린다.
        List<ReviewCandidate> reviews = List.of(
                review(1, MemoryItemKind.FACT, 0.5, QuestionType.SHORT_ANSWER),
                review(2, MemoryItemKind.FACT, 0.6, QuestionType.SHORT_ANSWER),
                review(3, MemoryItemKind.FACT, 0.7, QuestionType.SHORT_ANSWER));
        List<NewCandidate> news = List.of(
                fresh(10, 20, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER),
                fresh(11, 21, MemoryItemKind.CONFUSION, QuestionType.ERROR_FINDING),
                fresh(12, 22, MemoryItemKind.WARNING, QuestionType.CASE_JUDGMENT),
                fresh(13, 23, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER));

        Plan plan = DailyQueuePlanner.plan(reviews, news, Set.of(), POLICY);

        assertThat(ids(plan)).containsExactly(1L, 2L, 3L, 11L, 12L);
        assertThat(plan.reviewCount()).isEqualTo(3);
        assertThat(plan.newCount()).isEqualTo(2);
        assertThat(plan.entries().subList(3, 5)).allMatch(e -> e.source() == Source.NEW);
        assertThat(plan.carriedOver()).containsExactlyInAnyOrder(new DailyQueuePlanner.Carried(10L, CarryReason.BUDGET),
                new DailyQueuePlanner.Carried(13L, CarryReason.BUDGET));
        assertThat(plan.estimatedTime()).isEqualTo(Duration.ofSeconds(120 + 60 + 90));
    }

    @Test
    void newItemsAreCappedAtThreePerDay() {
        List<NewCandidate> news = List.of(
                fresh(10, 20, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER),
                fresh(11, 21, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER),
                fresh(12, 22, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER),
                fresh(13, 23, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER));

        Plan plan = DailyQueuePlanner.plan(List.of(), news, Set.of(), POLICY);

        assertThat(ids(plan)).containsExactly(10L, 11L, 12L);
        assertThat(plan.carriedOver()).containsExactly(new DailyQueuePlanner.Carried(13L, CarryReason.NEW_LIMIT));
    }

    @Test
    void reviewsFillTheBudgetFirstSoNewItemsGetNothingWhenFull() {
        List<ReviewCandidate> reviews = List.of(
                review(1, MemoryItemKind.PRACTICE, 0.1, QuestionType.CASE_APPLICATION),
                review(2, MemoryItemKind.PRACTICE, 0.2, QuestionType.CASE_APPLICATION),
                review(3, MemoryItemKind.FACT, 0.3, QuestionType.MULTIPLE_CHOICE));
        List<NewCandidate> news = List.of(fresh(10, 20, MemoryItemKind.FACT, QuestionType.MULTIPLE_CHOICE));

        // 120 + 120 + 20 = 260s. 신규 MC 20s는 280s로 들어가고, 신규가 하나 더 있어도 상한 3 안에서 예산이 허락하는 만큼만 든다.
        Plan plan = DailyQueuePlanner.plan(reviews, news, Set.of(), POLICY);
        assertThat(ids(plan)).containsExactly(1L, 2L, 3L, 10L);

        Plan fullPlan = DailyQueuePlanner.plan(reviews,
                List.of(fresh(10, 20, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER)), Set.of(), POLICY);
        assertThat(ids(fullPlan)).as("40s는 남은 40s에 정확히 들어간다").containsExactly(1L, 2L, 3L, 10L);

        Plan overflow = DailyQueuePlanner.plan(reviews,
                List.of(fresh(10, 20, MemoryItemKind.CONFUSION, QuestionType.ERROR_FINDING)), Set.of(), POLICY);
        assertThat(ids(overflow)).containsExactly(1L, 2L, 3L);
        assertThat(overflow.carriedOver()).containsExactly(new DailyQueuePlanner.Carried(10L, CarryReason.BUDGET));
    }

    @Test
    void newItemsRespectTheOnePerUnitRule() {
        List<ReviewCandidate> reviews = List.of(review(1, 10, MemoryItemKind.FACT, 0.5));
        List<NewCandidate> news = List.of(
                fresh(10, 10, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER),
                fresh(11, 20, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER),
                fresh(12, 20, MemoryItemKind.WARNING, QuestionType.CASE_JUDGMENT),
                fresh(13, 30, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER));

        Plan plan = DailyQueuePlanner.plan(reviews, news, Set.of(30L), POLICY);

        assertThat(ids(plan)).containsExactly(1L, 12L);
        assertThat(plan.carriedOver()).extracting(DailyQueuePlanner.Carried::itemId).containsExactlyInAnyOrder(10L, 11L, 13L);
        assertThat(plan.carriedOver()).allMatch(c -> c.reason() == CarryReason.SAME_UNIT);
    }

    @Test
    void newItemsAreNeverReviewCandidates() {
        Plan plan = DailyQueuePlanner.plan(List.of(), List.of(fresh(10, 20, MemoryItemKind.FACT, QuestionType.SHORT_ANSWER)),
                Set.of(), POLICY);

        assertThat(plan.reviewCount()).isZero();
        assertThat(plan.newCount()).isEqualTo(1);
        assertThat(plan.entries().getFirst().retrievability()).isNull();
    }

    @Test
    void emptyInputGivesEmptyQueue() {
        Plan plan = DailyQueuePlanner.plan(List.of(), List.of(), Set.of(), POLICY);

        assertThat(plan.entries()).isEmpty();
        assertThat(plan.estimatedTime()).isEqualTo(Duration.ZERO);
    }
}
