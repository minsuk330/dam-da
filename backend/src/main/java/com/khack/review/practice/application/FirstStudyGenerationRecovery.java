package com.khack.review.practice.application;

import com.khack.review.practice.domain.FirstStudyPlan;
import com.khack.review.practice.domain.FirstStudyPlanRepository;
import com.khack.review.practice.domain.GenerationStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 학습 문제 생성은 비동기라 서버가 생성 도중 멈추면 계획이 생성 중으로 남는다. 서버가 뜰 때 그런 계획을 실패로 바꿔
 * 사용자가 목표를 다시 골라 생성을 다시 시작할 수 있게 한다(세션은 확인 완료에 머물러 있다).
 */
@Component
class FirstStudyGenerationRecovery {

    static final String INTERRUPTED = "서버가 다시 시작되어 문제 생성이 중단됐습니다. 학습 목표를 다시 고르면 다시 만듭니다.";

    private static final Logger log = LoggerFactory.getLogger(FirstStudyGenerationRecovery.class);

    private final FirstStudyPlanRepository plans;

    FirstStudyGenerationRecovery(FirstStudyPlanRepository plans) {
        this.plans = plans;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedGenerations() {
        List<FirstStudyPlan> interrupted = plans.findByGenerationStatus(GenerationStatus.GENERATING);
        interrupted.forEach(plan -> plan.failGeneration(plan.getGeneration(), INTERRUPTED));
        if (!interrupted.isEmpty()) {
            log.info("생성 중에 멈춘 첫 학습 계획 {}개를 실패로 바꿈", interrupted.size());
        }
    }
}
