package com.khack.review.collection.application;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.Intent;
import org.jspecify.annotations.Nullable;

/** 사용자가 확인 화면에서 고치거나 추가하는 발화 내용. */
public record TurnContent(String text, Intent intent, @Nullable AiVerdict aiVerdict, @Nullable String correction) {
}
