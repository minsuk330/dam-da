package com.khack.review.question.application.port.out;

import java.util.List;

/**
 * 정답 기준의 사실 주장을 외부 출처에서 찾는다(#178). 대화 원문은 보내지 않고 주장 문장만 보낸다.
 * 구현(검색 서비스)은 아직 정하지 않았다. 벤치마크로 판정 성능을 본 뒤 고른다.
 */
public interface SourceSearchPort {

    /** 찾은 출처. 없으면 빈 목록. 공식 문서를 앞에 둔다. */
    List<SourceExcerpt> search(String claim);

    /** 출처 한 건. {@code text}는 주장과 관련된 부분만 담는다. */
    record SourceExcerpt(String url, String title, String text) {
    }
}
