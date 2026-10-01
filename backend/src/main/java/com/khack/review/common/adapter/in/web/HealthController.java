package com.khack.review.common.adapter.in.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {

    @GetMapping("/healthz")
    Map<String, Boolean> health() {
        return Map.of("ok", true);
    }
}
