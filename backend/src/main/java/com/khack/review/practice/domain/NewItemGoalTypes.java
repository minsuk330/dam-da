package com.khack.review.practice.domain;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 신규 항목의 문제 유형을 세션의 학습 목표로 정한다 (스펙 §6.4.4, §7.8 표). 첫 학습 계획에 이 항목의 문제가 있으면 그 유형을 쓰고,
 * 계획에 들지 못한 항목은 고른 목표를 순서대로 훑어 항목 종류에 맞는 첫 목표의 주된 유형을 쓴다.
 * 맞는 목표가 없거나 목표를 고르지 않았으면 종류별 기본 유형이다.
 */
public final class NewItemGoalTypes {

    private NewItemGoalTypes() {
    }

    public static QuestionType typeFor(@Nullable FirstStudyPlan plan, Long memoryItemId, MemoryItemKind kind) {
        if (plan == null) {
            return defaultType(kind);
        }
        return plan.getQuestions().stream().filter(q -> q.getMemoryItemId().equals(memoryItemId))
                .map(PlannedQuestionEntry::getQuestionType).findFirst()
                .orElseGet(() -> fromGoals(plan.getGoals(), kind));
    }

    static QuestionType fromGoals(List<LearningGoal> goals, MemoryItemKind kind) {
        for (LearningGoal goal : goals) {
            QuestionType type = switch (goal) {
                case KEY_RECALL -> kind == MemoryItemKind.FACT ? QuestionType.SHORT_ANSWER : null;
                case PRINCIPLE, EXPLAIN_OWN_WORDS -> kind == MemoryItemKind.FACT ? QuestionType.ESSAY : null;
                case DISTINGUISH -> kind == MemoryItemKind.FACT ? QuestionType.MULTIPLE_CHOICE : null;
                case CONDITION -> kind == MemoryItemKind.WARNING ? QuestionType.CASE_JUDGMENT : null;
                case APPLY_CASE -> kind == MemoryItemKind.PRACTICE ? QuestionType.CASE_APPLICATION : null;
                case CORRECT_MISCONCEPTION -> kind == MemoryItemKind.CONFUSION ? QuestionType.ERROR_FINDING : null;
            };
            if (type != null) {
                return type;
            }
        }
        return defaultType(kind);
    }

    static QuestionType defaultType(MemoryItemKind kind) {
        return switch (kind) {
            case FACT -> QuestionType.SHORT_ANSWER;
            case WARNING -> QuestionType.CASE_JUDGMENT;
            case PRACTICE -> QuestionType.CASE_APPLICATION;
            case CONFUSION -> QuestionType.ERROR_FINDING;
        };
    }
}
