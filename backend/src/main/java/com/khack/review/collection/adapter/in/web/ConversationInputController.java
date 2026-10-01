package com.khack.review.collection.adapter.in.web;

import com.khack.review.collection.application.SessionRejectedException;
import com.khack.review.collection.application.ShareLinkUnavailableException;
import com.khack.review.collection.application.TranscriptIntakeService;
import com.khack.review.collection.application.port.out.ConversationExtractionException;
import com.khack.review.collection.domain.SavedSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱의 대화 입력 (스펙 §7.5). 공유 링크 수집이 실패하면 응답의 {@code fallback: "paste"}로 붙여넣기를 안내한다.
 * 수집과 추출을 기다렸다가 응답한다.
 */
@RestController
class ConversationInputController {

    private final TranscriptIntakeService intake;

    ConversationInputController(TranscriptIntakeService intake) {
        this.intake = intake;
    }

    record ShareLinkRequest(String url) {
    }

    record PasteRequest(String text) {
    }

    record IntakeResponse(String conversationId, String inputPath, int userTurnCount, int reviewUnitCount, List<String> warnings) {

        static IntakeResponse of(SavedSession saved) {
            return new IntakeResponse(saved.id(), saved.source(), saved.userTurns().size(), saved.reviewUnits().size(), saved.warnings());
        }
    }

    record ErrorResponse(String code, String message, List<String> errors, String fallback) {
    }

    @PostMapping("/api/conversations/share-link")
    ResponseEntity<IntakeResponse> shareLink(@RequestBody ShareLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(IntakeResponse.of(intake.fromShareLink(request.url())));
    }

    @PostMapping("/api/conversations/paste")
    ResponseEntity<IntakeResponse> paste(@RequestBody PasteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(IntakeResponse.of(intake.fromPaste(request.text())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_input", e.getMessage(), List.of(), null));
    }

    @ExceptionHandler(ShareLinkUnavailableException.class)
    ResponseEntity<ErrorResponse> unavailable(ShareLinkUnavailableException e) {
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse("share_unavailable",
                "공유 링크에서 대화를 가져오지 못했습니다(%s). 대화를 복사해 붙여넣어 주세요.".formatted(e.status()), List.of(), "paste"));
    }

    @ExceptionHandler(ConversationExtractionException.class)
    ResponseEntity<ErrorResponse> extractionFailed(ConversationExtractionException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse("extraction_failed",
                "대화에서 학습 내용을 추출하지 못했습니다. 잠시 후 다시 시도하세요.", List.of(), null));
    }

    @ExceptionHandler(SessionRejectedException.class)
    ResponseEntity<ErrorResponse> rejected(SessionRejectedException e) {
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse("invalid_extraction",
                "추출 결과가 스키마 검증을 통과하지 못했습니다. 다시 시도하세요.", e.errors(), null));
    }
}
