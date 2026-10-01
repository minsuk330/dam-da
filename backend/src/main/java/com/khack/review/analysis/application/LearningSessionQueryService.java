package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.ConfirmationWarnings;
import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.MemoryItem;
import com.khack.review.analysis.domain.ReviewUnit;
import com.khack.review.collection.application.ConversationQueryService;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.common.application.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 현재 사용자의 학습 세션 조회. 다른 컨텍스트는 문제 생성 전에 {@link #requireConfirmed}로 확인 완료를 검사한다. */
@Service
public class LearningSessionQueryService {

    private final LearningSessionRepository sessions;
    private final ConversationQueryService conversations;
    private final CurrentUser currentUser;

    public LearningSessionQueryService(LearningSessionRepository sessions, ConversationQueryService conversations,
            CurrentUser currentUser) {
        this.sessions = sessions;
        this.conversations = conversations;
        this.currentUser = currentUser;
    }

    /** 최근 것부터. */
    @Transactional(readOnly = true)
    public List<LearningSessionSummary> list() {
        return sessions.findAllByUserIdOrderByCreatedAtDescIdDesc(currentUser.id()).stream()
                .map(session -> {
                    SavedSession conversation = conversations.find(session.getConversationId());
                    return new LearningSessionSummary(session.getId(), conversation.id(), session.getStatus(), session.getTopicHint(),
                            conversation.source(), conversation.transcription(), session.getCreatedAt(),
                            session.getUnits().size(), session.items().size());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public LearningSessionDetail detail(Long sessionId) {
        return detail(owned(sessionId));
    }

    /**
     * 비동기 처리(문제 생성 등)용: 요청 사용자 확인 없이 세션 상세를 돌려준다. 그 세션을 다루는 요청의 소유자 확인은 처리를 시작한 쪽이 이미 했다.
     * 사용자는 {@link #ownerOf}로 얻는다.
     */
    @Transactional(readOnly = true)
    public LearningSessionDetail detailForProcessing(Long sessionId) {
        return detail(sessions.findById(sessionId).orElseThrow(() -> new LearningSessionNotFoundException(sessionId)));
    }

    /** 세션 소유자 ID. 요청 밖(비동기)에서 사용자 ID가 필요할 때 쓴다. */
    @Transactional(readOnly = true)
    public Long ownerOf(Long sessionId) {
        return sessions.findById(sessionId).orElseThrow(() -> new LearningSessionNotFoundException(sessionId)).getUserId();
    }

    private LearningSessionDetail detail(LearningSession session) {
        SavedSession conversation = conversations.find(session.getConversationId());
        List<UserTurn> turns = conversation.userTurns();
        return new LearningSessionDetail(session.getId(), conversation.id(), session.getStatus(), session.getTopicHint(),
                conversation.source(), conversation.transcription(), session.getCreatedAt(), session.getConfirmedAt(),
                turns.stream().map(turn -> new LearningSessionDetail.Turn(turn.index(), turn.text(), turn.quotedText(),
                        turn.intent(), turn.aiVerdict(), turn.correction())).toList(),
                session.getUnits().stream().map(LearningSessionQueryService::unit).toList(),
                ConfirmationWarnings.of(session, turns));
    }

    /** 문제 생성처럼 확인 이후에만 할 수 있는 일 앞에서 부른다(규칙 12). 확인 전이면 {@link IllegalStateException}. */
    @Transactional(readOnly = true)
    public void requireConfirmed(Long sessionId) {
        LearningSession session = sessions.findById(sessionId).orElseThrow(() -> new LearningSessionNotFoundException(sessionId));
        if (!session.isConfirmed()) {
            throw new IllegalStateException("학습 세션 %d은(는) 아직 사용자 확인 전(%s)입니다. 문제는 확인 뒤에 만듭니다."
                    .formatted(sessionId, session.getStatus()));
        }
    }

    /** 현재 사용자의 세션 엔티티. 같은 컨텍스트의 서비스만 쓴다. 남의 세션은 없는 것으로 취급한다. */
    /** 대화(외부 ID)에서 만든 학습 세션. 아직 없거나 다른 사용자의 것이면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<Long> sessionIdOf(String conversationId) {
        return conversations.idOf(conversationId)
                .flatMap(sessions::findByConversationId)
                .filter(session -> session.getUserId().equals(currentUser.id()))
                .map(LearningSession::getId);
    }

    public LearningSession owned(Long sessionId) {
        return sessions.findById(sessionId)
                .filter(session -> session.getUserId().equals(currentUser.id()))
                .orElseThrow(() -> new LearningSessionNotFoundException(sessionId));
    }

    private static LearningSessionDetail.Unit unit(ReviewUnit unit) {
        return new LearningSessionDetail.Unit(unit.getId(), unit.getTitle(), unit.isExcluded(), unit.getVerdict(),
                unit.getVerdictReason(), unit.evidenceTurns(), unit.getItems().stream().map(LearningSessionQueryService::item).toList());
    }

    private static LearningSessionDetail.Item item(MemoryItem item) {
        return new LearningSessionDetail.Item(item.getId(), item.getKind(), item.getContent(), item.getSourceTurns(), item.getStatus());
    }
}
