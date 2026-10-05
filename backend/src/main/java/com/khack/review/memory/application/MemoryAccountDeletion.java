package com.khack.review.memory.application;

import com.khack.review.common.domain.AccountDeleted;
import com.khack.review.memory.domain.FsrsParametersRepository;
import com.khack.review.memory.domain.MemoryStateRepository;
import com.khack.review.memory.domain.ReviewLogRepository;
import com.khack.review.memory.domain.SessionMemorySettingsRepository;
import com.khack.review.memory.domain.UserFsrsSettingsRepository;
import com.khack.review.memory.domain.UserStudySettingsRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 탈퇴한 사용자의 기억 상태·복습 기록·학습 설정·개인 FSRS 매개변수를 지운다(#148). */
@Component
class MemoryAccountDeletion {

    private final MemoryStateRepository states;
    private final ReviewLogRepository logs;
    private final SessionMemorySettingsRepository sessionSettings;
    private final UserStudySettingsRepository studySettings;
    private final UserFsrsSettingsRepository fsrsSettings;
    private final FsrsParametersRepository parameters;

    MemoryAccountDeletion(MemoryStateRepository states, ReviewLogRepository logs, SessionMemorySettingsRepository sessionSettings,
            UserStudySettingsRepository studySettings, UserFsrsSettingsRepository fsrsSettings, FsrsParametersRepository parameters) {
        this.states = states;
        this.logs = logs;
        this.sessionSettings = sessionSettings;
        this.studySettings = studySettings;
        this.fsrsSettings = fsrsSettings;
        this.parameters = parameters;
    }

    @EventListener
    void on(AccountDeleted event) {
        Long userId = event.userId();
        states.deleteByUserId(userId);
        logs.deleteByUserId(userId);
        sessionSettings.deleteByUserId(userId);
        studySettings.deleteByUserId(userId);
        fsrsSettings.deleteByUserId(userId);
        parameters.deleteByOwnerUserId(userId);
    }
}
