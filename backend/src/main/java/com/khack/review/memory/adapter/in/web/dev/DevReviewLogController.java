package com.khack.review.memory.adapter.in.web.dev;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.ReviewLogExport;
import com.khack.review.memory.application.ReviewLogExport.PyFsrsReviewLog;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 현재 사용자의 복습 기록을 py-fsrs {@code ReviewLog} 형식으로 내보낸다. 개발 도구라 `/dev/**` 보호를 받는다. */
@RestController
class DevReviewLogController {

    private final ReviewLogExport export;
    private final CurrentUser currentUser;

    DevReviewLogController(ReviewLogExport export, CurrentUser currentUser) {
        this.export = export;
        this.currentUser = currentUser;
    }

    @GetMapping("/dev/review-logs.json")
    List<PyFsrsReviewLog> reviewLogs() {
        return export.pyFsrs(currentUser.id());
    }
}
