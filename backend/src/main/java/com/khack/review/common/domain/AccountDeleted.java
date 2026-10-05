package com.khack.review.common.domain;

/**
 * 사용자가 탈퇴했다(#148). 탈퇴와 같은 트랜잭션에서 발행되며, 각 컨텍스트가 이 사용자의 데이터를 지운다.
 * 사용자({@code AppUser})는 모든 처리가 끝난 뒤 지운다.
 */
public record AccountDeleted(Long userId) {
}
