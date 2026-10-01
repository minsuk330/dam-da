package com.khack.review.collection.domain;

import static com.khack.review.collection.domain.Fixtures.input;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionStoreTest {

    @TempDir
    Path dir;

    private SessionStore store() {
        return new SessionStore(dir.resolve("nested").resolve("sessions.jsonl"), Clock.systemUTC());
    }

    @Test
    void emptyBeforeAnythingIsSaved() {
        assertThat(store().list()).isEmpty();
        assertThat(store().latest()).isEmpty();
    }

    @Test
    void roundTripsV5SessionWithKoreanEmojiCodeAndNewlines() {
        String text = "TCP 핸드쉐잌 🤝\n```js\nconst a = \"x\";\n```";
        UserTurn first = new UserTurn(1, text, "클러스터 인덱스를", Intent.understanding_check, AiVerdict.corrected, "읽는 순간 잠근다");
        ReviewUnit unit = new ReviewUnit("잠금 순서",
                List.of(new KeyPoint("레코드마다 즉시 잠근다", List.of(1), null),
                        new KeyPoint("RC에서도 갭 락이 걸린다", List.of(1), FactKind.warning)),
                List.of(new ConfusionPoint(1, "스캔 후 잠근다")));
        SavedSession saved = store().save(new SessionInput(List.of(first), List.of(unit), "InnoDB"), List.of("경고"));

        SavedSession reloaded = store().list().get(0);
        assertThat(reloaded.id()).isEqualTo(saved.id());
        assertThat(reloaded.userTurns()).containsExactly(first);
        assertThat(reloaded.reviewUnits()).containsExactly(unit);
        assertThat(reloaded.warnings()).containsExactly("경고");
        assertThat(reloaded.transcription()).isEqualTo("model_transcribed");
    }

    @Test
    void readsLegacyV1AndV3Lines() throws Exception {
        Path file = dir.resolve("legacy.jsonl");
        Files.writeString(file, "{\"id\":\"v1\",\"receivedAt\":\"2026-09-27T10:00:00Z\",\"source\":\"connector\","
                + "\"transcription\":\"model_transcribed\",\"userTurns\":[{\"index\":1,\"text\":\"질문\"}],"
                + "\"totalUserTurns\":1,\"assistantSummary\":\"요약\",\"topicHint\":null,\"turnCountMismatch\":false}\n"
                + "{\"id\":\"v3\",\"receivedAt\":\"2026-09-27T11:00:00Z\",\"source\":\"connector\",\"transcription\":\"model_transcribed\","
                + "\"userTurns\":[{\"index\":1,\"text\":\"q\",\"quotedText\":\"\",\"intent\":\"info_request\",\"answerOpening\":\"a\","
                + "\"answerGist\":\"g\",\"aiVerdict\":\"not_applicable\",\"correction\":\"\",\"answerKeyPoints\":[{\"point\":\"p\",\"kind\":\"fact\"}]}],"
                + "\"reviewUnits\":[{\"title\":\"t\",\"evidenceTurns\":[1],\"confusionPoints\":[]}],\"topicHint\":null,\"warnings\":[]}\n");

        List<SavedSession> sessions = new SessionStore(file, Clock.systemUTC()).list();
        assertThat(sessions.get(0).assistantSummary()).isEqualTo("요약");
        assertThat(sessions.get(0).userTurns().get(0).intent()).isNull();
        assertThat(sessions.get(1).userTurns().get(0).intent()).isEqualTo(Intent.info_request);
        assertThat(sessions.get(1).reviewUnits().get(0).title()).isEqualTo("t");
        assertThat(sessions.get(1).reviewUnits().get(0).keyPoints()).isNullOrEmpty();
    }

    @Test
    void latestReturnsMostRecentlySaved() {
        SessionStore store = store();
        store.save(input(List.of(turn(1, "first"))), List.of());
        SavedSession second = store.save(input(List.of(turn(1, "second"))), List.of());
        assertThat(store.latest()).map(SavedSession::id).contains(second.id());
    }
}
