package com.khack.review.tools.verify;

import com.khack.review.collection.adapter.out.llm.LlmConversationExtractor;
import com.khack.review.collection.adapter.out.playwright.ShareExtractor;
import com.khack.review.collection.domain.RawConversation;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.SessionValidator;
import com.khack.review.collection.domain.ShareExtraction;
import com.khack.review.collection.domain.ShareLink;
import com.khack.review.collection.domain.ShareLinkPolicy;
import com.khack.review.collection.domain.ShareSource;
import com.khack.review.collection.domain.ShareStatus;
import com.khack.review.collection.domain.ShareTurn;
import com.khack.review.collection.domain.TranscriptAlignment;
import com.khack.review.collection.domain.ValidationResult;
import com.khack.review.common.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 실제 LLM으로 원문 대화에서 스키마 v5를 추출해 출력한다. 서버와 같은 순서(추출 → 원문 맞추기 → 검증)를 거치고 저장하지 않는다.
 * 추출 프롬프트를 바꿀 때 결과를 보는 용도다.
 *
 * <pre>
 * ./gradlew -q extractCheck -Pargs="https://chatgpt.com/share/..."          (ChatGPT·Claude·Codex 공유 링크를 Playwright로 수집)
 * ./gradlew -q extractCheck -Pargs="fixtures/transcripts/etag.json"        (발화자가 구분된 원문 {title, turns:[{role,text}]})
 * ./gradlew -q extractCheck -Pargs="fixtures/transcripts/etag.json --paste" (발화자 표시 없이 붙여넣은 것처럼)
 * ./gradlew -q extractCheck -Pargs="대화.txt"                               (붙여넣기 텍스트 파일)
 * ./gradlew -q extractCheck -Pargs="... --out build/extract.json"          (UTF-8 파일로 저장)
 * </pre>
 */
public final class ExtractCheckCli {

    private record Transcript(String title, List<ShareTurn> turns) {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: ./gradlew -q extractCheck -Pargs=\"<공유 링크|원문.json|붙여넣기.txt> [--paste] [--out <file>]\"");
            System.exit(1);
        }
        List<String> options = List.of(args).subList(1, args.length);
        RawConversation raw = load(args[0], options.contains("--paste"));

        SessionInput extracted = new LlmConversationExtractor(OpenAiCli.llm()).extract(raw);
        TranscriptAlignment.Result aligned = TranscriptAlignment.align(raw, extracted);
        ValidationResult validation = SessionValidator.validate(aligned.input());

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("source", args[0]);
        output.put("inputPath", raw.inputPath());
        output.put("model", OpenAiCli.model());
        output.put("originalUserTurns", raw.userTurnTexts().size());
        output.put("extracted", aligned.input());
        output.put("alignmentWarnings", aligned.warnings());
        output.put("errors", validation.errors());
        output.put("warnings", validation.warnings());
        OpenAiCli.print(output, options);
    }

    private static RawConversation load(String arg, boolean asPaste) throws IOException {
        if (arg.startsWith("https://")) {
            ShareLink link = ShareLinkPolicy.requireAllowed(arg);
            ShareExtraction fetched = ShareExtractor.extract(link, false, null);
            ShareStatus status = ShareStatus.classify(fetched);
            if (status != ShareStatus.OK) {
                throw new IllegalStateException("공유 링크에서 대화를 가져오지 못했습니다: " + status);
            }
            return RawConversation.fromShareLink(link.source(), fetched.title(), fetched.turns());
        }
        String content = Files.readString(Path.of(arg));
        if (!arg.endsWith(".json")) {
            return RawConversation.fromPaste(content);
        }
        Transcript transcript = Json.MAPPER.readValue(content, Transcript.class);
        if (asPaste) {
            return RawConversation.fromPaste(transcript.turns().stream().map(ShareTurn::text).collect(Collectors.joining("\n\n")));
        }
        return RawConversation.fromShareLink(ShareSource.chatgpt, transcript.title(), transcript.turns());
    }
}
