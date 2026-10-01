package com.khack.review.tools.verify;

import java.util.List;

public record ProbeReport(
        int status,
        String contentType,
        int bytes,
        boolean cloudflare,
        boolean hasRoleAttr,
        List<String> needlesFound,
        List<String> needlesMissing,
        boolean staticViable) {
}
