package com.khack.review.memory.application;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.memory.domain.DailyQueuePlanner;
import com.khack.review.memory.domain.QuestionLadder;
import com.khack.review.question.domain.QuestionType;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 매일 학습 큐 설정 ({@code review.memory.daily.*}, 스펙 §6.4.4, §6.4.6). 가중치·경계값은 초기안이며 조정한다.
 * 문제 유형별 기준 시간은 등급 변환의 {@code review.memory.rating.reference-times}를 그대로 쓴다.
 *
 * @param budget 하루 시간 예산
 * @param maxNewItems 신규 항목 하루 상한
 * @param weights 항목 종류별 가중치(헷갈린 지점 &gt; warning &gt; fact·practice)
 * @param heldMinUrgency 보류 항목의 최소 긴급도(보류는 상태를 바꾸지 않아 R이 높을 수 있다)
 * @param ladderLevel2MinStabilityDays 사다리 2단계(단답·서술) 시작 S(일)
 * @param ladderLevel3MinStabilityDays 사다리 3단계(사례 판단·응용) 시작 S(일)
 */
@ConfigurationProperties("review.memory.daily")
public record DailyQueueProperties(Duration budget, int maxNewItems, Map<MemoryItemKind, Double> weights, double heldMinUrgency,
        double ladderLevel2MinStabilityDays, double ladderLevel3MinStabilityDays) {

    /** {@code userBudget}은 사용자가 정한 하루 시간 예산. 없으면 {@link #budget}. */
    DailyQueuePlanner.Policy policy(Map<QuestionType, Duration> referenceTimes, Duration userBudget) {
        return new DailyQueuePlanner.Policy(userBudget, maxNewItems, weights, heldMinUrgency, referenceTimes);
    }

    QuestionLadder ladder() {
        return new QuestionLadder(ladderLevel2MinStabilityDays, ladderLevel3MinStabilityDays);
    }
}
