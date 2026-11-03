package com.khack.review.engagement.adapter.in.web;

import com.khack.review.engagement.application.UsageMetricsService;
import com.khack.review.engagement.domain.UsageMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전체 사용자 사용 지표(#182). 개발 도구라 접근 제한은 {@code DevToolsFilter}가 한다.
 * 배포 서버에서는 {@code curl -H "X-Dev-Token: $DEV_TOOLS_TOKEN" https://<서버>/dev/metrics}로 본다.
 */
@RestController
class DevUsageMetricsController {

    private final UsageMetricsService metrics;

    DevUsageMetricsController(UsageMetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/dev/metrics")
    UsageMetrics current() {
        return metrics.current();
    }
}
