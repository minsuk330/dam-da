package com.khack.review.engagement.adapter.out.log;

import com.khack.review.engagement.application.port.out.PushNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 실제 푸시 대신 로그만 남긴다. 푸시 서비스를 붙이면 이 구현을 교체한다. */
@Component
class LoggingPushNotifier implements PushNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushNotifier.class);

    @Override
    public void push(Long userId, String title, String body) {
        log.info("[push] user={} {} - {}", userId, title, body);
    }
}
