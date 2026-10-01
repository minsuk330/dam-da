package com.khack.review.memory.application;

import com.khack.review.memory.domain.RatingPolicy;
import com.khack.review.question.domain.QuestionType;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 등급 변환 정책 설정 ({@code review.memory.rating.*}). 값은 실제 풀이 기록으로 맞춘다(스펙 §12.2). */
@ConfigurationProperties("review.memory.rating")
public record RatingPolicyProperties(double minConfidence, Map<QuestionType, Duration> referenceTimes, double easyMaxTimeRatio) {

    @Configuration
    static class Config {

        @Bean
        RatingPolicy ratingPolicy(RatingPolicyProperties properties) {
            return new RatingPolicy(properties.minConfidence(), properties.referenceTimes(), properties.easyMaxTimeRatio());
        }
    }
}
