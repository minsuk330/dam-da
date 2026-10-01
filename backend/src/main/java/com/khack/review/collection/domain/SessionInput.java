package com.khack.review.collection.domain;

import java.util.List;

public record SessionInput(List<UserTurn> userTurns, List<ReviewUnit> reviewUnits, String topicHint) {
}
