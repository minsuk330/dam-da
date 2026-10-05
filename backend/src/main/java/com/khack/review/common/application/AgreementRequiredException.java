package com.khack.review.common.application;

/** 약관 동의 전에 대화를 저장하려 했다(#149). 웹에서는 403 {@code agreement_required}로 응답한다. */
public class AgreementRequiredException extends RuntimeException {

    public AgreementRequiredException() {
        super("대화를 저장하려면 이용약관과 개인정보 처리에 동의해 주세요.");
    }
}
