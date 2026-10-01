package com.khack.review.tools.question;

import com.khack.review.analysis.application.SessionContent;
import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.common.json.Json;
import com.khack.review.practice.domain.FirstStudyComposer;
import com.khack.review.practice.domain.LearningGoal;
import com.khack.review.question.adapter.out.llm.LlmQuestionGenerator;
import com.khack.review.question.application.port.out.UnitQuestionRequest;
import com.khack.review.question.application.port.out.UnitQuestionResult;
import com.khack.review.tools.verify.OpenAiCli;
import com.khack.review.tools.verify.SessionSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 실제 LLM으로 첫 학습 문제를 만들어 출력한다. 서버와 같은 순서(세션·기억 항목 → 첫 학습 계획 → 단위별 생성 요청)를 거치고
 * 저장과 품질 검사는 하지 않는다. 프롬프트를 바꿀 때 결과를 눈으로 보는 용도다. 키와 모델은 backend/.env.
 *
 * <pre>
 * ./gradlew -q questionGen -Pargs="fixtures/sessions/b2-etag.json CORRECT_MISCONCEPTION,PRINCIPLE"
 * ./gradlew -q questionGen -Pargs="latest KEY_RECALL"        (실행 중인 서버에 저장된 마지막 대화, 또는 대화 ID. -Pserver로 서버 지정)
 * ./gradlew -q questionGen -Pargs="fixtures/sessions/b2-etag.json KEY_RECALL --plan"   (LLM 호출 없이 생성 요청만)
 * ./gradlew -q questionGen -Pargs="... --out build/question-gen.json"   (UTF-8 파일로 저장. 콘솔에서 한글이 깨질 때)
 * </pre>
 */
public final class QuestionGenCli {

    /** 서버 기본값(review.practice.first-study-max-questions)과 같게 유지한다. */
    private static final int MAX_QUESTIONS = 12;

    /** 저장하지 않은 세션에는 ID가 없어 순서대로 번호를 붙인다. */
    private record Numbered(long id, int unitIndex, SessionContent.Item item) {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: ./gradlew -q questionGen -Pargs=\"<session.json|sessionId|latest> <GOAL[,GOAL]> [--plan] [--out <file>]\"");
            System.err.println("goals: " + Arrays.toString(LearningGoal.values()));
            System.exit(1);
        }
        SessionInput input = load(args[0]);
        List<LearningGoal> goals = Arrays.stream(args[1].split(",")).map(String::strip).map(LearningGoal::valueOf).toList();
        List<String> options = List.of(args).subList(2, args.length);

        SessionContent content = SessionContent.of(LearningSession.create(0L, 0L, input, Instant.EPOCH));
        List<Numbered> items = number(content);
        FirstStudyComposer.Composition plan = FirstStudyComposer.compose(goals, composerUnits(content, items), MAX_QUESTIONS);
        List<UnitQuestionRequest> requests = requests(content, input.userTurns(), items, plan.questions());

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("source", args[0]);
        output.put("goals", plan.goals());
        output.put("requests", requests);
        if (!options.contains("--plan")) {
            output.put("model", OpenAiCli.model());
            LlmQuestionGenerator generator = new LlmQuestionGenerator(OpenAiCli.llm());
            List<UnitQuestionResult> results = requests.stream().map(generator::generate).toList();
            output.put("results", results);
        }
        OpenAiCli.print(output, options);
    }

    private static SessionInput load(String arg) throws IOException {
        Path file = Path.of(arg);
        if (Files.isRegularFile(file)) {
            return Json.MAPPER.readValue(Files.readString(file), SessionInput.class);
        }
        String[] id = arg.equals("latest") ? new String[0] : new String[] {arg};
        SavedSession session = SessionSource.find(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + arg));
        return new SessionInput(session.userTurns(), session.reviewUnits(), session.topicHint());
    }

    private static List<Numbered> number(SessionContent content) {
        List<Numbered> items = new ArrayList<>();
        for (int u = 0; u < content.units().size(); u++) {
            for (SessionContent.Item item : content.units().get(u).items()) {
                items.add(new Numbered(items.size() + 1L, u, item));
            }
        }
        return items;
    }

    private static List<FirstStudyComposer.Unit> composerUnits(SessionContent content, List<Numbered> items) {
        List<FirstStudyComposer.Unit> units = new ArrayList<>();
        for (int u = 0; u < content.units().size(); u++) {
            int unitIndex = u;
            units.add(new FirstStudyComposer.Unit((long) u, items.stream().filter(n -> n.unitIndex() == unitIndex)
                    .map(n -> new FirstStudyComposer.Item(n.id(), n.item().kind())).toList()));
        }
        return units;
    }

    /** 계획된 문제를 복습 단위별 생성 요청으로 묶는다. 서버의 {@code QuestionGenerationService}와 같은 모양이다. */
    private static List<UnitQuestionRequest> requests(SessionContent content, List<UserTurn> turns, List<Numbered> items,
            List<FirstStudyComposer.PlannedQuestion> planned) {
        Map<Long, Numbered> byId = new LinkedHashMap<>();
        items.forEach(n -> byId.put(n.id(), n));
        Map<Integer, UserTurn> byIndex = new LinkedHashMap<>();
        turns.forEach(turn -> byIndex.putIfAbsent(turn.index(), turn));

        Map<Integer, List<UnitQuestionRequest.Target>> targets = new LinkedHashMap<>();
        for (int i = 0; i < planned.size(); i++) {
            FirstStudyComposer.PlannedQuestion question = planned.get(i);
            Numbered target = byId.get(question.memoryItemId());
            SessionContent.Item item = target.item();
            String correction = item.kind() != MemoryItemKind.CONFUSION ? null
                    : item.sourceTurns().stream().map(byIndex::get).filter(Objects::nonNull).map(UserTurn::correction)
                            .filter(Objects::nonNull).findFirst().orElse(null);
            String related = question.relatedItemId() == null ? null : byId.get(question.relatedItemId()).item().content();
            targets.computeIfAbsent(target.unitIndex(), unit -> new ArrayList<>()).add(new UnitQuestionRequest.Target(
                    "q" + i, target.id(), item.kind(), item.content(), correction, item.sourceTurns(), question.type(),
                    question.goal().name(), related, List.of()));
        }

        List<UnitQuestionRequest> requests = new ArrayList<>();
        targets.forEach((unitIndex, unitTargets) -> {
            SessionContent.Unit unit = content.units().get(unitIndex);
            List<UnitQuestionRequest.KeyPoint> keyPoints = items.stream().filter(n -> n.unitIndex() == unitIndex)
                    .map(n -> new UnitQuestionRequest.KeyPoint(n.id(), n.item().kind(), n.item().content())).toList();
            List<UnitQuestionRequest.EvidenceTurn> evidence = unitTargets.stream().flatMap(t -> t.sourceTurns().stream())
                    .distinct().sorted().map(byIndex::get).filter(Objects::nonNull)
                    .map(t -> new UnitQuestionRequest.EvidenceTurn(t.index(), t.text(),
                            t.aiVerdict() == null ? null : t.aiVerdict().name(), t.correction()))
                    .toList();
            requests.add(new UnitQuestionRequest(unit.title(), content.topicHint(), keyPoints, evidence, unitTargets));
        });
        return requests;
    }
}
