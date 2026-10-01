package com.khack.review.collection.application.port.out;

/** 원문 대화를 스키마로 추출하지 못했다. 사용자에게는 다시 시도하거나 붙여넣기로 입력하도록 안내한다. */
public class ConversationExtractionException extends RuntimeException {

    public ConversationExtractionException(String message) {
        super(message);
    }

    public ConversationExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
