package com.khack.review.collection.domain;

/** The AI's correction is not repeated here; it lives on the referenced turn's {@code correction}. */
public record ConfusionPoint(int turn, String userBelief) {
}
