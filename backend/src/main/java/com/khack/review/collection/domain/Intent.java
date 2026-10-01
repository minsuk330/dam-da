package com.khack.review.collection.domain;

/** Learning role of a user message. Lowercase constants so the JSON values match the tool contract. */
public enum Intent {
    info_request, rephrase_request, understanding_check, restatement, challenge, meta
}
