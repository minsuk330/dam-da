package com.khack.review.collection.application;

import com.khack.review.collection.domain.ShareStatus;

/** 공유 링크에서 대화를 가져오지 못했다. 사용자는 붙여넣기로 이어간다(스펙 §7.5). */
public class ShareLinkUnavailableException extends RuntimeException {

    private final ShareStatus status;

    public ShareLinkUnavailableException(ShareStatus status) {
        super("공유 링크에서 대화를 가져오지 못했습니다: " + status);
        this.status = status;
    }

    public ShareStatus status() {
        return status;
    }
}
