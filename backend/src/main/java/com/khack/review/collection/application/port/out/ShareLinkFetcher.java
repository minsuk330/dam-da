package com.khack.review.collection.application.port.out;

import com.khack.review.collection.domain.ShareExtraction;

/**
 * 대화 공유 링크에서 발화를 가져온다.
 */
public interface ShareLinkFetcher {

    ShareExtraction fetch(String url);
}
