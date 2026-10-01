package com.khack.review.collection.application;

import com.khack.review.collection.domain.SessionStore;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ConnectorConfig {

    @Bean
    SessionStore sessionStore(@Value("${review.sessions-file}") String file, Clock clock) {
        return new SessionStore(Path.of(file), clock);
    }
}
