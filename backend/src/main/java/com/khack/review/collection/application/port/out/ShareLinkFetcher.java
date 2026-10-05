package com.khack.review.collection.application.port.out;

import com.khack.review.collection.domain.ShareExtraction;
import com.khack.review.collection.domain.ShareLink;

/**
 * 대화 공유 링크에서 발화를 가져온다. 출처마다 페이지 구조가 달라 {@link ShareLink#source()}로 읽는 방법을 고른다.
 */
public interface ShareLinkFetcher {

    ShareExtraction fetch(ShareLink link);
}
