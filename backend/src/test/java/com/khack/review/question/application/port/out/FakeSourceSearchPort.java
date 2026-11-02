package com.khack.review.question.application.port.out;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 테스트용 출처 검색. 주장별로 돌려줄 출처를 정해 두고, 없으면 빈 목록이다. 받은 주장을 기록한다. */
public class FakeSourceSearchPort implements SourceSearchPort {

    private final Map<String, List<SourceExcerpt>> results;
    private final List<String> claims = new ArrayList<>();

    public FakeSourceSearchPort(Map<String, List<SourceExcerpt>> results) {
        this.results = Map.copyOf(results);
    }

    public List<String> claims() {
        return List.copyOf(claims);
    }

    @Override
    public List<SourceExcerpt> search(String claim) {
        claims.add(claim);
        return results.getOrDefault(claim, List.of());
    }
}
