package com.khack.review.collection.domain;

/** {@link ShareLinkPolicy}를 통과한 공유 링크. {@code url}은 쿼리·프래그먼트를 뗀 정규화 주소다. */
public record ShareLink(String url, ShareSource source) {
}
