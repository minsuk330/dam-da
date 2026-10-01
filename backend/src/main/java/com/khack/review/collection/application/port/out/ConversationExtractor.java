package com.khack.review.collection.application.port.out;

import com.khack.review.collection.domain.RawConversation;
import com.khack.review.collection.domain.SessionInput;

/**
 * 원문 대화를 커넥터 스키마 v5({@link SessionInput})로 추출한다 (스펙 §6.1, §7.5). 공유 링크·붙여넣기 입력에 쓰며,
 * 커넥터 입력은 사용자의 AI가 직접 작성하므로 쓰지 않는다. 구현(LLM 프롬프트)은 ai 담당이다.
 *
 * <p>계약
 * <ul>
 *   <li>{@code userTurns}: 원문의 사용자 발화를 대화 순서대로 빠짐없이 1개씩, {@code text}는 원문 그대로.
 *       서버는 공유 링크 입력의 발화 text를 원문으로 다시 맞추고, 붙여넣기 입력은 text가 원문에 있는지 경고로 확인한다.</li>
 *   <li>{@code intent}, {@code aiVerdict}, {@code correction}, {@code reviewUnits} 규칙은 커넥터 도구 설명
 *       ({@code LearningSessionTools.DESCRIPTION})과 같다. 결과는 서버가 같은 검증기로 검사한다.</li>
 *   <li>추출할 수 없으면 {@link ConversationExtractionException}을 던진다. 재시도는 구현이 판단한다.</li>
 * </ul>
 */
public interface ConversationExtractor {

    SessionInput extract(RawConversation conversation);
}
