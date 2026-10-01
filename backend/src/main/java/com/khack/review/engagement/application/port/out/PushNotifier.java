package com.khack.review.engagement.application.port.out;

/**
 * 기기 푸시 알림 포트. 해커톤은 앱 안 알림 목록이 기본이며, 푸시는 붙일 자리만 둔다.
 * 실패해도 앱 안 알림은 남아야 하므로 호출하는 쪽이 예외를 삼킨다.
 */
public interface PushNotifier {

    void push(Long userId, String title, String body);
}
