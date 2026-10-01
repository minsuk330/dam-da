package com.khack.review.memory.adapter.in.web.dev;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.FsrsParametersService;
import com.khack.review.memory.application.FsrsParametersService.ParameterSet;
import com.khack.review.memory.application.ParameterActivationService;
import com.khack.review.memory.application.ParameterActivationService.Activated;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 개인 매개변수 옵티마이저(`optimizer/`, 스펙 §6.4.9)가 쓰는 개발 도구 API. 현재 매개변수를 비교 기준으로 읽고,
 * 검증을 통과한 매개변수를 새 버전으로 저장한다. 버전 적용·롤백과 기억 상태 재계산(#25)도 여기서 한다. `/dev/**` 보호를 받는다.
 */
@RestController
class DevFsrsParametersController {

    private final FsrsParametersService parameters;
    private final ParameterActivationService activation;
    private final CurrentUser currentUser;

    DevFsrsParametersController(FsrsParametersService parameters, ParameterActivationService activation, CurrentUser currentUser) {
        this.parameters = parameters;
        this.activation = activation;
        this.currentUser = currentUser;
    }

    /** {@code validation}은 옵티마이저 검증 결과(지표, 기록 수, 기간 등)이며 그대로 저장한다. */
    record RegisterRequest(double[] weights, JsonNode validation) {
    }

    record ErrorResponse(String code, String message) {
    }

    @GetMapping("/dev/fsrs-parameters")
    List<ParameterSet> all() {
        return parameters.all(currentUser.id());
    }

    /** 현재 사용자에게 이 버전을 적용하고 기억 상태를 다시 계산한다. 이전 버전을 주면 롤백이다. */
    @PostMapping("/dev/fsrs-parameters/{version}/activate")
    Activated activate(@PathVariable int version) {
        return activation.activate(currentUser.id(), version);
    }

    @GetMapping("/dev/fsrs-parameters/active")
    ParameterSet active() {
        return parameters.activeParameters(currentUser.id());
    }

    @PostMapping("/dev/fsrs-parameters")
    @ResponseStatus(HttpStatus.CREATED)
    ParameterSet register(@RequestBody RegisterRequest request) {
        if (request.weights() == null || request.validation() == null || !request.validation().isObject()) {
            throw new IllegalArgumentException("weights와 validation(객체)이 필요합니다");
        }
        return parameters.registerOptimized(currentUser.id(), request.weights(), request.validation().toString());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_input", e.getMessage()));
    }
}
