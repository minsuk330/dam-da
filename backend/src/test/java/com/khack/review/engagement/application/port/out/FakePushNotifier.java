package com.khack.review.engagement.application.port.out;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 테스트용 PushNotifier. 보낸 푸시를 기록하고, 원하면 실패시킨다. */
public class FakePushNotifier implements PushNotifier {

    public record Push(Long userId, String title, String body) {
    }

    private final List<Push> pushes = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    public FakePushNotifier willFail(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    public List<Push> pushes() {
        return List.copyOf(pushes);
    }

    @Override
    public void push(Long userId, String title, String body) {
        pushes.add(new Push(userId, title, body));
        if (failure != null) {
            throw failure;
        }
    }
}
