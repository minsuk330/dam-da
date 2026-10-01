package com.khack.review.practice.application;

import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.practice.domain.AttemptOutcome;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.question.domain.QuestionType;
import java.util.List;

/**
 * 다음 행동 선택에서 Jev에 보내는 상태. {@link NextActionQuestions}가 필드 이름을 가리킨다.
 * 사용자 답과 정답 기준은 보내지 않는다. 판정은 이미 끝났고, 여기서는 지금까지의 경과만 본다.
 *
 * @param stem               문제 문장
 * @param attempts           이 제시의 시도 경과(오래된 순)
 * @param hintShown          힌트를 보여 줬는가
 * @param explanationShown   설명을 보여 줬는가
 * @param repeatedDifficulty 이 항목을 여러 번 틀려 왔는가
 */
public record NextActionState(PracticeKind practiceKind, boolean sameDayRecheck, QuestionType questionType, String stem,
        AttemptOutcome latestOutcome, List<Step> attempts, boolean hintShown, boolean explanationShown,
        boolean repeatedDifficulty) {

    public record Step(AttemptKind kind, AttemptOutcome outcome) {
    }
}
