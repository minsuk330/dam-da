package com.khack.review.collection.adapter.in.mcp;

import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.application.SessionRejectedException;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class LearningSessionTools {

    public static final String TOOL_NAME = "save_learning_session";

    static final String DESCRIPTION = """
            사용자가 "복습에 넣어줘"처럼 현재 학습 대화를 복습 앱에 저장해 달라고 요청할 때 한 번 호출한다. \
            인자는 짧게 쓴다. 선택 필드는 기본값이면 생략한다.

            [userTurns] 사용자 메시지 1개 = 1항목. 대화의 첫 AI 답변부터 마지막 답변까지 하나씩 짚으며 각 답변 직전의 사용자 메시지를 적는다. \
            앞 질문이나 뒤 정리와 내용이 겹치는 확인 질문도 별도 메시지라면 따로 넣는다.
            - index: 1부터. text: 사용자 메시지 원문을 오타까지 수정 없이 그대로. 요약·번역·병합 금지.
            - intent(필수): info_request(새 정보 요청, 예 "trx_id가 뭔데"), rephrase_request(방금 설명을 다시 쉽게 요청), \
            understanding_check(자기 이해가 맞는지 확인, 예 "스캔 하고 락을 거는게 아니라?"), restatement(자기 말로 정리, 예 "정리 1. … 2. …"), \
            challenge(AI 주장의 근거를 묻거나 반박), meta(학습 외 발화: 저장 요청, 잡담, 대화나 저장 기능 자체에 대한 질문 예 "왜 한번에 못 찾았어?").
            - quotedText: 사용자가 AI 답변 일부를 인용해 답장했을 때만 인용된 부분.
            - aiVerdict: 판정을 새로 하지 말고 대화 속 AI 답변의 판정을 추출. "네, 맞습니다"=confirmed, "절반은 맞습니다"·"거의 맞는데"=partial, \
            "방향이 반대입니다"·"아닙니다"=corrected. 판정할 이해가 없던 발화는 생략(=not_applicable).
            - correction: aiVerdict가 partial·corrected일 때 AI가 교정한 내용.

            [reviewUnits] 복습할 주제 1~7개. 사용자가 몰랐던 정보 요청 주제도 포함한다. meta가 아닌 모든 발화의 AI 답변이 어느 주제에든 반영되어야 한다.
            - keyPoints: 이 주제에 대한 AI 답변의 핵심. point(내용)와 turns(그 내용을 설명한 AI 답변 직전 사용자 발화 index 목록). \
            답변 본론뿐 아니라 뒷부분의 "추가로", "참고", "주의", "실무에서" 섹션도 포함한다. \
            여러 답변이 같은 내용을 반복했다면 한 번만 쓰고 turns에 모두 넣는다. 내용이 다르거나 조건·예외가 다르면 따로 적는다.
            - kind: 생략=fact(개념 설명). practice=실무 팁·확인 방법·명령어(예 "EXPLAIN으로 type ALL 확인"). \
            warning=AI 답변에 "헷갈리기 쉽다", "주의", "이렇게 외우면 안 된다", "오해" 같은 경고 표현이 실제로 있을 때만. 경고를 단순화하거나 뒤집지 않는다.
            - confusionPoints: aiVerdict가 partial·corrected인 발화는 모두 confusionPoints에 넣는다(관련 주제 단위에). \
            turn과 userBelief(사용자가 믿었던 것, 예 "ETag는 서버가 파일 내용을 전부 비교해서 만든다")만 적고, 교정은 그 발화의 correction을 쓴다.

            구조 오류가 있으면 오류 메시지가 반환되니 지적된 부분을 고쳐 다시 호출한다.""";

    private static final Logger log = LoggerFactory.getLogger(LearningSessionTools.class);

    private final ConnectorIntakeService intakeService;

    public LearningSessionTools(ConnectorIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @McpTool(name = TOOL_NAME, title = "학습 대화를 복습에 저장", description = DESCRIPTION,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, openWorldHint = false))
    public String saveLearningSession(
            @McpToolParam(required = true, description = "1층. 모든 사용자 메시지를 대화 순서대로, 메시지 1개당 1항목")
            List<UserTurn> userTurns,
            @McpToolParam(required = true, description = "2층. 복습 단위 1~7개. keyPoints와 confusionPoints는 userTurns의 index로 출처를 참조")
            List<ReviewUnit> reviewUnits,
            @McpToolParam(required = false, description = "학습 주제 (선택)")
            String topicHint) {
        var spikeAuth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        log.info("[spike] thread={} principal={}", Thread.currentThread().getName(), spikeAuth == null ? null : spikeAuth.getName());
        SessionInput input = new SessionInput(userTurns, reviewUnits, topicHint);
        SavedSession saved;
        try {
            saved = intakeService.intake(input);
        } catch (SessionRejectedException e) {
            log.info("[{}] rejected: {}", TOOL_NAME, e.errors());
            throw new IllegalArgumentException("저장하지 않았습니다. 아래를 고쳐 다시 호출하세요.\n- "
                    + String.join("\n- ", e.errors()));
        }
        log.info("[{}] {} turns={} units={} warnings={}", TOOL_NAME, saved.id(),
                saved.userTurns().size(), saved.reviewUnits().size(), saved.warnings().size());
        String warning = saved.warnings().isEmpty() ? "" : " 경고 %d건은 앱 확인 단계에서 표시됩니다.".formatted(saved.warnings().size());
        return "복습 저장 접수됨 (id: %s, 사용자 발화 %d개, 복습 단위 %d개).%s 앱에서 확인 후 복습이 시작됩니다."
                .formatted(saved.id(), saved.userTurns().size(), saved.reviewUnits().size(), warning);
    }
}
