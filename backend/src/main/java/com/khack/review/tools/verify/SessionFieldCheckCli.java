package com.khack.review.tools.verify;

import com.khack.review.analysis.application.FieldTaxonomy;
import com.khack.review.analysis.application.SessionFieldClassifier;
import com.khack.review.analysis.application.SessionFieldOutcome;
import com.khack.review.analysis.application.SessionFieldPolicy;
import com.khack.review.analysis.application.SessionFieldQuestions;
import com.khack.review.analysis.application.SessionFieldState;
import com.khack.review.common.adapter.out.typesafe.TypeSafeJevAdapter;
import com.khack.review.common.application.port.out.JevPort;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClient;

/**
 * 학습 분야 판정 질문({@link SessionFieldQuestions})으로 실제 Jev를 호출해 표본별 선택·신뢰도와 기대 일치 여부를 출력한다.
 * 질문이나 신뢰도 기준을 바꿀 때 실제 분포를 보는 용도다. 키는 backend/.env의 TYPESAFE_API_KEY.
 * 사용: ./gradlew -q sessionFieldCheck [-Pargs=다른/.env/경로]
 */
public final class SessionFieldCheckCli {

    /** application.yml의 review.analysis.field 기본값과 같게 유지한다. 기준을 0으로 두면 원래 선택을 그대로 본다. */
    private static final SessionFieldPolicy POLICY = new SessionFieldPolicy(true, 0.5, 3, Duration.ofSeconds(1));

    record Sample(String expected, @Nullable String topic, List<String> unitTitles) {
    }

    private static final List<Sample> SAMPLES = List.of(
            new Sample("cs.db", "MySQL InnoDB", List.of("MVCC와 일반 SELECT", "잠금 읽기", "갭 락과 팬텀")),
            new Sample("cs.os", "운영체제 중간고사", List.of("프로세스와 스레드 차이", "컨텍스트 스위칭 비용", "뮤텍스와 세마포어")),
            new Sample("cs.network", null, List.of("TCP 3-way handshake", "TIME_WAIT 상태", "HTTP keep-alive")),
            new Sample("cs.algo", "코딩테스트 준비", List.of("다익스트라 시간복잡도", "우선순위 큐 구현")),
            new Sample("cs.ai", "LLM 공부", List.of("트랜스포머 어텐션", "파인튜닝과 RAG 차이")),
            new Sample("biz.accounting", "회계원리", List.of("발생주의와 현금주의", "감가상각 방법", "재무상태표 구성")),
            new Sample("biz.marketing", "마케팅원론 과제", List.of("STP 전략", "4P 믹스", "브랜드 포지셔닝")),
            new Sample("biz.finance", "재무관리", List.of("WACC 계산", "NPV와 IRR 비교")),
            new Sample("biz.macro", null, List.of("기준금리 인상의 효과", "인플레이션과 실업률")),
            new Sample("math.stats", "통계학 기말", List.of("p-value 해석", "t검정 조건", "회귀계수 의미")),
            new Sample("lang.english", "토익", List.of("가정법 과거완료", "관계대명사 what")),
            new Sample("hum.philosophy", null, List.of("칸트의 정언명령", "공리주의 비판")),
            new Sample("etc.life", null, List.of("오늘 저녁 메뉴 고르기")),
            // 경계: 분류표 hint로 나눈 이웃 항목
            new Sample("cs.data", "매출 데이터 정리", List.of("엑셀 피벗 테이블", "VLOOKUP과 XLOOKUP")),
            new Sample("biz.mis", "경영정보시스템 수업", List.of("ERP 도입 효과", "디지털 전환 사례")));

    public static void main(String[] args) throws IOException {
        String apiKey = loadApiKey(args.length > 0 ? Path.of(args[0]) : Path.of(".env"));
        if (apiKey.isBlank()) {
            System.err.println("TYPESAFE_API_KEY가 없습니다. backend/.env에 넣거나 -Pargs=경로로 지정하세요.");
            System.exit(1);
        }
        JevPort jev = new TypeSafeJevAdapter(RestClient.builder(), apiKey, "https://api.typesafe.ai", "jev-latest");
        SessionFieldClassifier classifier = new SessionFieldClassifier(jev, new FieldTaxonomy(), POLICY);

        int matched = 0;
        for (Sample sample : SAMPLES) {
            SessionFieldOutcome outcome = classifier.classify(new SessionFieldState(sample.topic(), sample.unitTitles(), null));
            boolean ok = outcome.code().equals(sample.expected());
            matched += ok ? 1 : 0;
            System.out.printf("%s 기대 %-15s → %-15s | %s%n", ok ? "O" : "X", sample.expected(), outcome.code(), outcome.reason());
        }
        System.out.printf("%n일치 %d/%d (기준 %.2f)%n", matched, SAMPLES.size(), POLICY.minConfidence());
    }

    private static String loadApiKey(Path env) throws IOException {
        if (Files.exists(env)) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(env)) {
                properties.load(reader);
            }
            String key = properties.getProperty("TYPESAFE_API_KEY", "");
            if (!key.isBlank()) {
                return key.trim();
            }
        }
        return System.getenv().getOrDefault("TYPESAFE_API_KEY", "");
    }
}
