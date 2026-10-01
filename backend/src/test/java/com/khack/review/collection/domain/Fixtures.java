package com.khack.review.collection.domain;

import java.util.List;

public final class Fixtures {

    private Fixtures() {
    }

    public static UserTurn turn(int index, String text) {
        return new UserTurn(index, text, null, Intent.info_request, null, null);
    }

    public static UserTurn turn(int index, String text, Intent intent, AiVerdict verdict, String correction) {
        return new UserTurn(index, text, null, intent, verdict, correction);
    }

    public static KeyPoint point(String point, int... turns) {
        return new KeyPoint(point, java.util.Arrays.stream(turns).boxed().toList(), null);
    }

    public static ReviewUnit unit(String title, int... turns) {
        return new ReviewUnit(title, List.of(point("핵심", turns)), null);
    }

    public static SessionInput input(List<UserTurn> turns) {
        return new SessionInput(turns, List.of(unit("주제", turns.get(0).index())), "InnoDB");
    }
}
