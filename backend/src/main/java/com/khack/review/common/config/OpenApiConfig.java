package com.khack.review.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 프론트엔드 API 계약(`frontend/openapi.json`)의 머리말. 서버 주소는 요청마다 달라지지 않게 상대 경로로 고정한다.
 */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("AI Learning Companion API").version("v1"))
                .servers(List.of(new Server().url("/")));
    }
}
