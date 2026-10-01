# 커넥터·공유 링크 입력 검증 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 두 입력 경로가 실제로 동작하는지 검증한다. (1) Spring 백엔드에 붙인 MCP 서버가 Claude 커스텀 커넥터로 연결되어 `save_learning_session` 호출을 받는지, (2) ChatGPT 공유 링크에서 사용자 발화를 빠짐없이 가져올 수 있는지.

**Architecture:** Spring Boot 앱 하나(`backend/`)에 세 패키지를 둔다. `connector`는 Spring AI MCP Server(`@McpTool`)로 노출하는 저장 도구와 JSONL 저장소, `share`는 공유 링크 정적 probe와 Playwright for Java 추출기, `verify`는 두 경로가 공유하는 "기대 발화 vs 수신 발화" 비교 로직과 검증 CLI다. 기대 발화는 사용자가 미리 작성한 대화 스크립트(fixture)를 그대로 보내는 방식으로 확보해 원문 정답을 보장한다. 검증 CLI는 Spring 컨텍스트 없이 Gradle `JavaExec` 태스크로 실행한다.

**Tech Stack:** Java 21 (Temurin), Spring Boot 4.1.1, Spring AI 2.0.1 (`spring-ai-starter-mcp-server-webmvc`, MCP Java SDK 2.0.1 포함), Jackson 3(`tools.jackson`), Playwright for Java 1.63.0, JUnit 5 + AssertJ, Gradle wrapper, ngrok(설치·설정 완료 확인됨).

**Spec:** `docs/spec/ai-conversation-learning-review-platform.md` (§7.1~§7.5, §8.5 규칙 13~14, §11.1, §12.2)

## Global Constraints

- JDK 21로 빌드한다. 로컬 기본 JDK가 23이므로 모든 명령 전에 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`을 실행한다.
- MCP 서버는 저장 도구 `save_learning_session` 하나만 노출한다. 복습 조회·답변 제출 도구는 만들지 않는다 (spec §7.2).
- 도구 입력은 `userTurns: [{ index, text }]`(필수, 원문 그대로), `totalUserTurns`(필수), `assistantSummary`(필수), `topicHint`(선택)이다. spec §7.3의 snake_case 이름 대신 Java 파라미터 이름(camelCase)이 스키마 이름이 된다.
- 커넥터로 수신한 대화는 `source: "connector"`, `transcription: "model_transcribed"`로 표시한다 (spec §8.5 규칙 13~14).
- 도구 설명에 "모든 사용자 발화를 순서대로, 오타까지 수정 없이, 생략하지 말 것"을 명시한다 (spec §7.3 대응 1).
- Claude 커넥터는 Anthropic 클라우드에서 호출하므로 공개 HTTPS URL이 필요하다. 검증 단계는 ngrok 터널을 사용한다.
- 검증 단계 서버는 인증 없이 공개되므로 테스트용 대화만 사용하고, 검증이 끝나면 ngrok을 즉시 종료한다.
- 공유 링크 수집은 ChatGPT(`chatgpt.com/share/...`)만 대상으로 한다 (spec §11.1).
- fixture JSON 필드 이름은 Java record와 같은 camelCase(`userTurns`, `shareUrl`)를 쓴다.

## Review Focus

1. 모델이 보낸 `userTurns` 개수와 `totalUserTurns`가 다른 경우: 저장은 하되 `turnCountMismatch: true`로 표시하고 도구 응답에 경고를 넣어야 한다. → Task 2, Task 3 테스트
2. 빈 `userTurns`나 빈 발화 텍스트: 아무것도 저장되지 않고 오류가 반환되어야 한다. → Task 3 테스트
3. 도구 스키마의 필수 여부: `topicHint`가 required로 잡히면 모델이 주제를 지어내야 한다. `userTurns`·`totalUserTurns`·`assistantSummary`만 required여야 한다. → Task 3 테스트
4. 한국어·이모지·코드 블록·줄바꿈: 저장과 비교 과정에서 그대로 보존되어야 하고, 공백 차이만 있는 경우와 실제 내용 변경(오타 교정)을 구분해야 한다. → Task 1, Task 2 테스트
5. 삭제·차단된 공유 링크: 턴 0개를 "성공"으로 보고하지 않고 `NOT_FOUND` / `BLOCKED` / `NO_TURNS`로 분류해야 한다. → Task 6 테스트

---

## File Structure

```text
khack/
├── .gitignore
├── docs/verification/results.md                  # 수동 검증 결과 기록
└── backend/
    ├── build.gradle
    ├── settings.gradle, gradlew, gradle/          # Spring Initializr 생성
    ├── fixtures/scripts/                          # 사용자가 실제로 보낼 대화 스크립트 = 원문 정답
    ├── data/                                      # gitignore: sessions.jsonl, probe/, results/
    └── src/
        ├── main/resources/application.yml
        ├── main/java/com/khack/review/
        │   ├── ReviewApplication.java
        │   ├── HealthController.java
        │   ├── support/Json.java                  # 공용 Jackson 3 mapper
        │   ├── verify/
        │   │   ├── TurnComparison.java            # 비교 결과 record
        │   │   ├── TurnComparator.java            # 기대 vs 수신 비교 (두 경로 공용)
        │   │   ├── ConversationScript.java        # fixture 스크립트 record + 로더
        │   │   ├── LongScriptGenerator.java       # 30턴 스크립트 생성 CLI
        │   │   └── ConnectorCheckCli.java         # 저장된 세션 vs 스크립트 비교 CLI
        │   ├── connector/
        │   │   ├── UserTurn.java, SessionInput.java, SavedSession.java
        │   │   ├── SessionStore.java              # JSONL 저장소
        │   │   ├── ConnectorConfig.java           # SessionStore bean
        │   │   └── LearningSessionTools.java      # @McpTool save_learning_session
        │   └── share/
        │       ├── ProbeReport.java
        │       ├── ShareProbe.java                # 정적 HTML 분석 (순수 함수)
        │       ├── ShareProbeCli.java
        │       ├── ShareTurn.java, ShareExtraction.java, ShareStatus.java
        │       ├── ShareExtractor.java            # Playwright 추출
        │       └── ShareVerifyCli.java
        └── test/java/com/khack/review/
            ├── verify/TurnComparatorTest.java
            ├── connector/SessionStoreTest.java
            ├── connector/LearningSessionToolsIT.java
            ├── share/ShareProbeTest.java
            └── share/ShareStatusTest.java
```

---

## Phase 1 — 공용 비교 로직과 MCP 서버

### Task 1: 프로젝트 스캐폴딩과 발화 비교 로직

**Files:**
- Create: `.gitignore`, `backend/` (Initializr), `backend/src/main/resources/application.yml`
- Modify: `backend/build.gradle`
- Create: `backend/src/main/java/com/khack/review/support/Json.java`
- Create: `backend/src/main/java/com/khack/review/verify/TurnComparison.java`, `TurnComparator.java`
- Test: `backend/src/test/java/com/khack/review/verify/TurnComparatorTest.java`

**Interfaces:**
- Produces:
  - `Json.MAPPER : tools.jackson.databind.json.JsonMapper`
  - `record TurnComparison(int expectedCount, int actualCount, int exact, int normalized, List<Missing> missing, List<Extra> extra, boolean orderPreserved, Verdict verdict)`
    - `record Missing(int index, String expected, String closest)` (`closest`는 null 가능, index는 1부터)
    - `record Extra(int index, String actual)`
    - `enum Verdict { IDENTICAL, WHITESPACE_ONLY, DIVERGED }`
  - `TurnComparator.normalize(String text): String`
  - `TurnComparator.compare(List<String> expected, List<String> actual): TurnComparison`

- [ ] **Step 1: 저장소와 Spring 프로젝트 생성**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
cd /Users/wa/golbob/khack
git init
curl -s https://start.spring.io/starter.zip \
  -d type=gradle-project -d language=java -d bootVersion=4.1.1 -d javaVersion=21 \
  -d groupId=com.khack -d artifactId=review -d name=review -d packageName=com.khack.review \
  -d dependencies=web,spring-ai-mcp-server -o /tmp/review.zip
mkdir backend && unzip -q /tmp/review.zip -d backend && rm /tmp/review.zip
```

생성된 메인 클래스 이름이 `ReviewApplication`인지 확인한다: `ls backend/src/main/java/com/khack/review/`

`.gitignore` (khack 루트):

```text
backend/build/
backend/.gradle/
backend/data/
.idea/
```

- [ ] **Step 2: build.gradle에 Playwright와 CLI 태스크 추가**

`backend/build.gradle`의 `dependencies` 블록에 추가:

```groovy
	implementation 'com.microsoft.playwright:playwright:1.63.0'
```

파일 끝에 추가:

```groovy
def cli = { String taskName, String main ->
	tasks.register(taskName, JavaExec) {
		group = 'verification cli'
		classpath = sourceSets.main.runtimeClasspath
		mainClass = main
		workingDir = projectDir
		args = ((project.findProperty('args') ?: '') as String).tokenize()
	}
}
cli('longScript', 'com.khack.review.verify.LongScriptGenerator')
cli('connectorCheck', 'com.khack.review.verify.ConnectorCheckCli')
cli('shareProbe', 'com.khack.review.share.ShareProbeCli')
cli('shareVerify', 'com.khack.review.share.ShareVerifyCli')
```

`backend/src/main/resources/application.properties`를 지우고 `application.yml` 작성:

```yaml
server:
  port: 8080
spring:
  application:
    name: review
  ai:
    mcp:
      server:
        name: review-connector
        version: 0.1.0
        type: SYNC
        protocol: STREAMABLE
        streamable-http:
          mcp-endpoint: /mcp
review:
  sessions-file: data/sessions.jsonl
logging:
  level:
    io.modelcontextprotocol: INFO
```

`backend/src/main/java/com/khack/review/support/Json.java`:

```java
package com.khack.review.support;

import tools.jackson.databind.json.JsonMapper;

public final class Json {

    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }
}
```

- [ ] **Step 3: 실패하는 테스트 작성**

`backend/src/test/java/com/khack/review/verify/TurnComparatorTest.java`:

```java
package com.khack.review.verify;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.verify.TurnComparison.Missing;
import com.khack.review.verify.TurnComparison.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnComparatorTest {

    @Test
    void normalizeCollapsesWhitespaceAndTrims() {
        assertThat(TurnComparator.normalize("  TCP랑\n\nUDP   차이 ")).isEqualTo("TCP랑 UDP 차이");
    }

    @Test
    void identicalWhenEveryTurnMatchesExactly() {
        List<String> turns = List.of("TCP가 뭐야?", "그럼 UDP는 왜 빨라? 🤔", "```js\nconsole.log(1)\n```");
        TurnComparison r = TurnComparator.compare(turns, List.copyOf(turns));
        assertThat(r.verdict()).isEqualTo(Verdict.IDENTICAL);
        assertThat(r.exact()).isEqualTo(3);
        assertThat(r.missing()).isEmpty();
        assertThat(r.extra()).isEmpty();
    }

    @Test
    void whitespaceOnlyWhenOnlySpacingDiffers() {
        TurnComparison r = TurnComparator.compare(List.of("TCP가 뭐야?\n"), List.of("TCP가  뭐야?"));
        assertThat(r.verdict()).isEqualTo(Verdict.WHITESPACE_ONLY);
        assertThat(r.exact()).isZero();
        assertThat(r.normalized()).isEqualTo(1);
    }

    @Test
    void divergedWithClosestTextWhenModelFixedTypo() {
        TurnComparison r = TurnComparator.compare(List.of("핸드쉐잌은 몇번 해?"), List.of("핸드셰이크는 몇 번 해?"));
        assertThat(r.verdict()).isEqualTo(Verdict.DIVERGED);
        assertThat(r.missing()).containsExactly(new Missing(1, "핸드쉐잌은 몇번 해?", "핸드셰이크는 몇 번 해?"));
        assertThat(r.extra()).containsExactly(new TurnComparison.Extra(1, "핸드셰이크는 몇 번 해?"));
    }

    @Test
    void detectsOmittedShortAcknowledgement() {
        TurnComparison r = TurnComparator.compare(
                List.of("TCP가 뭐야?", "아 네", "그럼 UDP는?"),
                List.of("TCP가 뭐야?", "그럼 UDP는?"));
        assertThat(r.verdict()).isEqualTo(Verdict.DIVERGED);
        assertThat(r.missing()).extracting(Missing::index).containsExactly(2);
        assertThat(r.orderPreserved()).isTrue();
        assertThat(r.actualCount()).isEqualTo(2);
    }

    @Test
    void flagsReorderedTurns() {
        TurnComparison r = TurnComparator.compare(List.of("A 질문", "B 질문"), List.of("B 질문", "A 질문"));
        assertThat(r.normalized()).isEqualTo(2);
        assertThat(r.orderPreserved()).isFalse();
        assertThat(r.verdict()).isEqualTo(Verdict.DIVERGED);
    }

    @Test
    void detectsMergedTurns() {
        TurnComparison r = TurnComparator.compare(List.of("A 질문", "B 질문"), List.of("A 질문 B 질문"));
        assertThat(r.verdict()).isEqualTo(Verdict.DIVERGED);
        assertThat(r.missing()).hasSize(2);
        assertThat(r.extra()).hasSize(1);
    }
}
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests 'com.khack.review.verify.TurnComparatorTest'`
Expected: FAIL — `cannot find symbol: class TurnComparator` 컴파일 오류

- [ ] **Step 5: 최소 구현**

`backend/src/main/java/com/khack/review/verify/TurnComparison.java`:

```java
package com.khack.review.verify;

import java.util.List;

public record TurnComparison(
        int expectedCount,
        int actualCount,
        int exact,
        int normalized,
        List<Missing> missing,
        List<Extra> extra,
        boolean orderPreserved,
        Verdict verdict) {

    public record Missing(int index, String expected, String closest) {
    }

    public record Extra(int index, String actual) {
    }

    public enum Verdict {
        IDENTICAL, WHITESPACE_ONLY, DIVERGED
    }
}
```

`backend/src/main/java/com/khack/review/verify/TurnComparator.java`:

```java
package com.khack.review.verify;

import com.khack.review.verify.TurnComparison.Extra;
import com.khack.review.verify.TurnComparison.Missing;
import com.khack.review.verify.TurnComparison.Verdict;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TurnComparator {

    private TurnComparator() {
    }

    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll("\\s+", " ").strip();
    }

    public static TurnComparison compare(List<String> expected, List<String> actual) {
        Set<Integer> used = new HashSet<>();
        List<Integer> matchedPositions = new ArrayList<>();
        List<Integer> unmatchedExpected = new ArrayList<>();
        int exact = 0;
        int normalized = 0;

        for (int i = 0; i < expected.size(); i++) {
            String text = expected.get(i);
            int j = indexOf(actual, used, a -> a.equals(text));
            if (j >= 0) {
                exact++;
            } else {
                String target = normalize(text);
                j = indexOf(actual, used, a -> normalize(a).equals(target));
            }
            if (j >= 0) {
                normalized++;
                used.add(j);
                matchedPositions.add(j);
            } else {
                unmatchedExpected.add(i);
            }
        }

        List<Missing> missing = new ArrayList<>();
        for (int i : unmatchedExpected) {
            String closest = i < actual.size() && !used.contains(i) ? actual.get(i) : null;
            missing.add(new Missing(i + 1, expected.get(i), closest));
        }
        List<Extra> extra = new ArrayList<>();
        for (int k = 0; k < actual.size(); k++) {
            if (!used.contains(k)) {
                extra.add(new Extra(k + 1, actual.get(k)));
            }
        }
        boolean orderPreserved = true;
        for (int i = 1; i < matchedPositions.size(); i++) {
            if (matchedPositions.get(i) <= matchedPositions.get(i - 1)) {
                orderPreserved = false;
                break;
            }
        }
        boolean sameCount = expected.size() == actual.size();

        Verdict verdict = Verdict.DIVERGED;
        if (sameCount && orderPreserved && exact == expected.size()) {
            verdict = Verdict.IDENTICAL;
        } else if (sameCount && orderPreserved && normalized == expected.size()) {
            verdict = Verdict.WHITESPACE_ONLY;
        }

        return new TurnComparison(expected.size(), actual.size(), exact, normalized,
                List.copyOf(missing), List.copyOf(extra), orderPreserved, verdict);
    }

    private static int indexOf(List<String> actual, Set<Integer> used, java.util.function.Predicate<String> match) {
        for (int k = 0; k < actual.size(); k++) {
            if (!used.contains(k) && match.test(actual.get(k))) {
                return k;
            }
        }
        return -1;
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.khack.review.verify.TurnComparatorTest'`
Expected: 7 tests PASS

- [ ] **Step 7: Commit**

```bash
cd /Users/wa/golbob/khack
git add .gitignore backend
git commit -m "chore: scaffold Spring backend with turn comparison"
```

---

### Task 2: 커넥터 세션 저장소

**Files:**
- Create: `backend/src/main/java/com/khack/review/connector/UserTurn.java`, `SessionInput.java`, `SavedSession.java`, `SessionStore.java`
- Test: `backend/src/test/java/com/khack/review/connector/SessionStoreTest.java`

**Interfaces:**
- Consumes: `Json.MAPPER` (Task 1)
- Produces:
  - `record UserTurn(int index, String text)`
  - `record SessionInput(List<UserTurn> userTurns, int totalUserTurns, String assistantSummary, String topicHint)`
  - `record SavedSession(String id, String receivedAt, String source, String transcription, List<UserTurn> userTurns, int totalUserTurns, String assistantSummary, String topicHint, boolean turnCountMismatch)`
  - `class SessionStore { SessionStore(Path file); SavedSession save(SessionInput input); List<SavedSession> list(); Optional<SavedSession> latest() }`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/khack/review/connector/SessionStoreTest.java`:

```java
package com.khack.review.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionStoreTest {

    @TempDir
    Path dir;

    private SessionStore store() {
        return new SessionStore(dir.resolve("nested").resolve("sessions.jsonl"));
    }

    @Test
    void emptyBeforeAnythingIsSaved() {
        assertThat(store().list()).isEmpty();
        assertThat(store().latest()).isEmpty();
    }

    @Test
    void roundTripsKoreanEmojiCodeAndNewlines() {
        String text = "TCP 핸드쉐잌 🤝\n```js\nconst a = \"x\";\n```";
        SavedSession saved = store().save(new SessionInput(List.of(new UserTurn(1, text)), 1, "3-way handshake 설명", null));

        List<SavedSession> reloaded = store().list();
        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.get(0).userTurns().get(0).text()).isEqualTo(text);
        assertThat(reloaded.get(0).id()).isEqualTo(saved.id());
        assertThat(reloaded.get(0).source()).isEqualTo("connector");
        assertThat(reloaded.get(0).transcription()).isEqualTo("model_transcribed");
    }

    @Test
    void flagsMismatchBetweenSentTurnsAndReportedTotal() {
        SavedSession saved = store().save(new SessionInput(
                List.of(new UserTurn(1, "A"), new UserTurn(2, "B")), 5, "s", null));
        assertThat(saved.turnCountMismatch()).isTrue();
    }

    @Test
    void latestReturnsMostRecentlySaved() {
        SessionStore store = store();
        store.save(new SessionInput(List.of(new UserTurn(1, "first")), 1, "s", null));
        SavedSession second = store.save(new SessionInput(List.of(new UserTurn(1, "second")), 1, "s", null));
        assertThat(store.latest()).map(SavedSession::id).contains(second.id());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests 'com.khack.review.connector.SessionStoreTest'`
Expected: FAIL — `cannot find symbol: class SessionStore` 컴파일 오류

- [ ] **Step 3: 최소 구현**

`backend/src/main/java/com/khack/review/connector/UserTurn.java`:

```java
package com.khack.review.connector;

public record UserTurn(int index, String text) {
}
```

`backend/src/main/java/com/khack/review/connector/SessionInput.java`:

```java
package com.khack.review.connector;

import java.util.List;

public record SessionInput(List<UserTurn> userTurns, int totalUserTurns, String assistantSummary, String topicHint) {
}
```

`backend/src/main/java/com/khack/review/connector/SavedSession.java`:

```java
package com.khack.review.connector;

import java.util.List;

public record SavedSession(
        String id,
        String receivedAt,
        String source,
        String transcription,
        List<UserTurn> userTurns,
        int totalUserTurns,
        String assistantSummary,
        String topicHint,
        boolean turnCountMismatch) {
}
```

`backend/src/main/java/com/khack/review/connector/SessionStore.java`:

```java
package com.khack.review.connector;

import com.khack.review.support.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SessionStore {

    private final Path file;

    public SessionStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    public synchronized SavedSession save(SessionInput input) {
        SavedSession session = new SavedSession(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                "connector",
                "model_transcribed",
                input.userTurns(),
                input.totalUserTurns(),
                input.assistantSummary(),
                input.topicHint(),
                input.userTurns().size() != input.totalUserTurns());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.MAPPER.writeValueAsString(session) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return session;
    }

    public synchronized List<SavedSession> list() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(line -> Json.MAPPER.readValue(line, SavedSession.class))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<SavedSession> latest() {
        List<SavedSession> all = list();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.khack.review.connector.SessionStoreTest'`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
cd /Users/wa/golbob/khack
git add backend/src/main/java/com/khack/review/connector backend/src/test/java/com/khack/review/connector/SessionStoreTest.java
git commit -m "feat: add JSONL store for connector sessions"
```

---

### Task 3: MCP 도구 (`@McpTool`)와 통합 테스트

**Files:**
- Create: `backend/src/main/java/com/khack/review/connector/ConnectorConfig.java`, `LearningSessionTools.java`
- Create: `backend/src/main/java/com/khack/review/HealthController.java`
- Test: `backend/src/test/java/com/khack/review/connector/LearningSessionToolsIT.java`

**Interfaces:**
- Consumes: `SessionStore`, `SessionInput`, `SavedSession`, `UserTurn` (Task 2)
- Produces:
  - `LearningSessionTools.TOOL_NAME = "save_learning_session"`
  - `LearningSessionTools.saveLearningSession(List<UserTurn> userTurns, int totalUserTurns, String assistantSummary, String topicHint): String`
  - Bean `SessionStore` (property `review.sessions-file`)
  - `GET /healthz` → `{"ok":true}`
  - MCP endpoint `POST /mcp` (Streamable HTTP)

- [ ] **Step 1: 실패하는 통합 테스트 작성**

MCP Java SDK의 클라이언트(`mcp-core`, 서버 스타터에 포함됨)로 실제 HTTP를 통해 도구를 호출한다.

`backend/src/test/java/com/khack/review/connector/LearningSessionToolsIT.java`:

```java
package com.khack.review.connector;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LearningSessionToolsIT {

    static final Path SESSIONS = createTempSessionsFile();

    static Path createTempSessionsFile() {
        try {
            return Files.createTempDirectory("sessions-").resolve("sessions.jsonl");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("review.sessions-file", SESSIONS::toString);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    SessionStore store;

    McpSyncClient client;

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                .requestTimeout(Duration.ofSeconds(10))
                .build();
        client.initialize();
    }

    @AfterEach
    void close() {
        client.closeGracefully();
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    @Test
    void exposesOnlySaveLearningSessionAsWriteTool() {
        List<Tool> tools = client.listTools().tools();
        assertThat(tools).extracting(Tool::name).containsExactly("save_learning_session");
        Tool tool = tools.get(0);
        assertThat(tool.annotations().readOnlyHint()).isFalse();
        assertThat(tool.description()).contains("오타까지 수정 없이");
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyTopicHintIsOptional() {
        Tool tool = client.listTools().tools().get(0);
        List<String> required = (List<String>) tool.inputSchema().get("required");
        assertThat(required).containsExactlyInAnyOrder("userTurns", "totalUserTurns", "assistantSummary");
        Map<String, Object> properties = (Map<String, Object>) tool.inputSchema().get("properties");
        assertThat(properties).containsKeys("userTurns", "totalUserTurns", "assistantSummary", "topicHint");
    }

    @Test
    void storesCallAndAnswersWithSessionId() {
        CallToolResult result = client.callTool(new CallToolRequest("save_learning_session", Map.of(
                "userTurns", List.of(Map.of("index", 1, "text", "TCP 핸드쉐잌이 뭐야?")),
                "totalUserTurns", 1,
                "assistantSummary", "3-way handshake 설명",
                "topicHint", "네트워크")));

        SavedSession saved = store.latest().orElseThrow();
        assertThat(saved.userTurns().get(0).text()).isEqualTo("TCP 핸드쉐잌이 뭐야?");
        assertThat(saved.topicHint()).isEqualTo("네트워크");
        assertThat(text(result)).contains(saved.id());
    }

    @Test
    void warnsWhenReportedTotalDoesNotMatch() {
        CallToolResult result = client.callTool(new CallToolRequest("save_learning_session", Map.of(
                "userTurns", List.of(Map.of("index", 1, "text", "A")),
                "totalUserTurns", 3,
                "assistantSummary", "s")));

        assertThat(text(result)).contains("주의");
        assertThat(store.latest().orElseThrow().turnCountMismatch()).isTrue();
    }

    @Test
    void rejectsEmptyUserTurnsWithoutSaving() {
        int before = store.list().size();
        boolean rejected;
        try {
            CallToolResult result = client.callTool(new CallToolRequest("save_learning_session", Map.of(
                    "userTurns", List.of(),
                    "totalUserTurns", 1,
                    "assistantSummary", "s")));
            rejected = Boolean.TRUE.equals(result.isError());
        } catch (RuntimeException e) {
            rejected = true;
        }
        assertThat(rejected).isTrue();
        assertThat(store.list()).hasSize(before);
    }

    @Test
    void healthEndpointAnswersOk() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/healthz")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"ok\":true");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests 'com.khack.review.connector.LearningSessionToolsIT'`
Expected: FAIL — `SessionStore` bean이 없어 컨텍스트 로딩 실패 (`NoSuchBeanDefinitionException`)

- [ ] **Step 3: 도구와 설정 구현**

`backend/src/main/java/com/khack/review/connector/ConnectorConfig.java`:

```java
package com.khack.review.connector;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ConnectorConfig {

    @Bean
    SessionStore sessionStore(@Value("${review.sessions-file}") String file) {
        return new SessionStore(Path.of(file));
    }
}
```

`backend/src/main/java/com/khack/review/connector/LearningSessionTools.java`:

```java
package com.khack.review.connector;

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
            사용자가 "복습에 넣어줘"처럼 현재 학습 대화를 복습 앱에 저장해 달라고 요청할 때 호출한다. \
            userTurns에는 이 대화의 모든 사용자 발화를 순서대로, 오타까지 수정 없이, 생략하지 말고 원문 그대로 넣는다. \
            "아 네", "그럼 같은 거죠?" 같은 짧은 확인 발화도 반드시 포함한다. 요약·번역·병합하지 않는다. \
            대화가 길어 한 번에 담기 어려우면 index를 이어서 여러 번 나눠 호출한다. \
            AI 답변은 원문 대신 assistantSummary에 요약한다.""";

    private static final Logger log = LoggerFactory.getLogger(LearningSessionTools.class);

    private final SessionStore store;

    public LearningSessionTools(SessionStore store) {
        this.store = store;
    }

    @McpTool(name = TOOL_NAME, title = "학습 대화를 복습에 저장", description = DESCRIPTION,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, openWorldHint = false))
    public String saveLearningSession(
            @McpToolParam(required = true, description = "모든 사용자 발화. 순서대로, 오타까지 수정 없이 원문 그대로. index는 1부터")
            List<UserTurn> userTurns,
            @McpToolParam(required = true, description = "이 대화 전체의 사용자 발화 개수")
            int totalUserTurns,
            @McpToolParam(required = true, description = "AI 답변 내용 요약")
            String assistantSummary,
            @McpToolParam(required = false, description = "학습 주제 (선택)")
            String topicHint) {
        if (userTurns == null || userTurns.isEmpty()) {
            throw new IllegalArgumentException("userTurns must not be empty");
        }
        if (userTurns.stream().anyMatch(t -> t.text() == null || t.text().isBlank())) {
            throw new IllegalArgumentException("every user turn needs text");
        }

        SavedSession saved = store.save(new SessionInput(userTurns, totalUserTurns, assistantSummary, topicHint));
        log.info("[{}] {} turns={}/{}", TOOL_NAME, saved.id(), saved.userTurns().size(), saved.totalUserTurns());

        String warning = saved.turnCountMismatch()
                ? " 주의: 전달된 발화 수(%d)가 totalUserTurns(%d)와 다릅니다.".formatted(saved.userTurns().size(), saved.totalUserTurns())
                : "";
        return "복습 저장 접수됨 (id: %s, 사용자 발화 %d개).%s 앱에서 확인 후 복습이 시작됩니다."
                .formatted(saved.id(), saved.userTurns().size(), warning);
    }
}
```

`backend/src/main/java/com/khack/review/HealthController.java`:

```java
package com.khack.review;

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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test`
Expected: 전체 17 tests PASS (Task 1: 7, Task 2: 4, Task 3: 6)

`onlyTopicHintIsOptional`이 실패하면 `./gradlew test --info`로 실제 `inputSchema`를 출력해 확인한다. 파라미터 이름이 `arg0` 등으로 나오면 컴파일 옵션 `-parameters`가 빠진 것이므로 `build.gradle`에 `tasks.withType(JavaCompile).configureEach { options.compilerArgs << '-parameters' }`를 추가한다.

- [ ] **Step 5: 로컬 스모크 확인**

```bash
./gradlew bootRun &
sleep 15
curl -s http://localhost:8080/healthz
kill %1
```

Expected: `{"ok":true}`

- [ ] **Step 6: Commit**

```bash
cd /Users/wa/golbob/khack
git add backend/src
git commit -m "feat: expose save_learning_session via Spring AI MCP server"
```

---

### Task 4: Claude 커넥터 연결 검증 (수동)

사람이 Claude 화면에서 진행하는 검증이다. 스크립트를 미리 작성해 두고 그대로 입력하므로 원문 정답이 보장된다.

**Files:**
- Create: `backend/src/main/java/com/khack/review/verify/ConversationScript.java`, `LongScriptGenerator.java`, `ConnectorCheckCli.java`
- Create: `backend/fixtures/scripts/c1-short.json`, `c2-typos.json`, `c4-short-acks.json`
- Create: `docs/verification/results.md`

**Interfaces:**
- Consumes: `TurnComparator`, `TurnComparison` (Task 1), `SessionStore`, `SavedSession`, `UserTurn` (Task 2), `Json.MAPPER`
- Produces:
  - `record ConversationScript(String name, String platform, List<String> userTurns, String shareUrl)` + `static ConversationScript load(Path path)`
  - Gradle `./gradlew -q longScript -Pargs="<name> <platform>"` → `fixtures/scripts/<name>.json`
  - Gradle `./gradlew -q connectorCheck -Pargs="<script.json> [sessionId]"`

- [ ] **Step 1: 스크립트 로더·생성기·비교 CLI 작성**

`backend/src/main/java/com/khack/review/verify/ConversationScript.java`:

```java
package com.khack.review.verify;

import com.khack.review.support.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record ConversationScript(String name, String platform, List<String> userTurns, String shareUrl) {

    public static ConversationScript load(Path path) {
        try {
            ConversationScript script = Json.MAPPER.readValue(Files.readString(path), ConversationScript.class);
            if (script.userTurns() == null || script.userTurns().isEmpty()) {
                throw new IllegalArgumentException(path + ": userTurns must be a non-empty array");
            }
            return script;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean hasShareUrl() {
        return shareUrl != null && !shareUrl.isBlank();
    }
}
```

`backend/src/main/java/com/khack/review/verify/LongScriptGenerator.java`:

```java
package com.khack.review.verify;

import com.khack.review.support.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LongScriptGenerator {

    private static final List<String> TOPICS = List.of("TCP", "UDP", "HTTP/2", "TLS", "DNS", "CDN", "로드 밸런서", "WebSocket", "QUIC", "NAT");
    private static final List<String> FOLLOW_UPS = List.of("가 정확히 뭐야?", " 예시 하나만 들어줘", "랑 앞에서 말한 거랑 뭐가 달라?");

    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "c3-long";
        String platform = args.length > 1 ? args[1] : "claude";

        List<String> turns = new ArrayList<>();
        for (int i = 0; i < TOPICS.size(); i++) {
            for (int j = 0; j < FOLLOW_UPS.size(); j++) {
                turns.add("%d번 질문: %s%s".formatted(i * 3 + j + 1, TOPICS.get(i), FOLLOW_UPS.get(j)));
            }
        }
        if (platform.equals("claude")) {
            turns.add("복습에 넣어줘");
        }

        Path out = Path.of("fixtures/scripts", name + ".json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, Json.MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(new ConversationScript(name, platform, turns, "")) + "\n");
        System.out.println(out + ": " + turns.size() + " turns");
    }
}
```

`backend/src/main/java/com/khack/review/verify/ConnectorCheckCli.java`:

```java
package com.khack.review.verify;

import com.khack.review.connector.SavedSession;
import com.khack.review.connector.SessionStore;
import com.khack.review.connector.UserTurn;
import com.khack.review.support.Json;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConnectorCheckCli {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: ./gradlew -q connectorCheck -Pargs=\"<script.json> [sessionId]\"");
            System.exit(1);
        }
        ConversationScript script = ConversationScript.load(Path.of(args[0]));
        SessionStore store = new SessionStore(Path.of(System.getProperty("review.sessions-file", "data/sessions.jsonl")));
        Optional<SavedSession> session = args.length > 1
                ? store.list().stream().filter(s -> s.id().equals(args[1])).findFirst()
                : store.latest();
        if (session.isEmpty()) {
            System.err.println("No saved connector session found.");
            System.exit(1);
        }

        List<String> actual = session.get().userTurns().stream()
                .sorted(Comparator.comparingInt(UserTurn::index))
                .map(UserTurn::text)
                .toList();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("script", script.name());
        report.put("sessionId", session.get().id());
        report.put("turnCountMismatch", session.get().turnCountMismatch());
        report.put("comparison", TurnComparator.compare(script.userTurns(), actual));
        System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
```

- [ ] **Step 2: 검증용 대화 스크립트 작성**

`backend/fixtures/scripts/c1-short.json`:

```json
{
  "name": "c1-short",
  "platform": "claude",
  "userTurns": [
    "TCP랑 UDP 차이를 쉽게 설명해줘",
    "그럼 TCP가 느린 이유가 3-way handshake 때문이야?",
    "handshake는 연결할 때 한 번만 하는 거지?",
    "UDP는 순서가 안 맞으면 어떻게 돼?",
    "이 내용 복습에 넣어줘"
  ],
  "shareUrl": ""
}
```

`backend/fixtures/scripts/c2-typos.json` (오타·구어체 보존 확인용, 오타를 고치지 말 것):

```json
{
  "name": "c2-typos",
  "platform": "claude",
  "userTurns": [
    "재귀함수가 머야 잘 모르겟어",
    "스택오버플로우는 왜 나는거임?",
    "꼬리재귀면 스택이 안싸이는거 맞지??",
    "파이썬도 꼬리재귀 최적화 해줌?",
    "복습에 넣어줘"
  ],
  "shareUrl": ""
}
```

`backend/fixtures/scripts/c4-short-acks.json` (짧은 확인성 턴 생략 여부 확인용):

```json
{
  "name": "c4-short-acks",
  "platform": "claude",
  "userTurns": [
    "HTTP 캐시에서 ETag가 뭐야?",
    "아 네",
    "그럼 Last-Modified랑 같은 거죠?",
    "ㅇㅋ",
    "둘 다 있으면 뭐가 우선이야?",
    "복습에 넣어줘"
  ],
  "shareUrl": ""
}
```

긴 대화 스크립트 생성:

```bash
cd backend
./gradlew -q longScript -Pargs="c3-long claude"
```

Expected: `fixtures/scripts/c3-long.json: 31 turns` (질문 30턴 + 마지막 "복습에 넣어줘" 1턴)

- [ ] **Step 3: 결과 기록 문서 작성**

`docs/verification/results.md`:

```markdown
# 입력 경로 검증 결과

## 1. Claude 커넥터

| ID | 시나리오 | 계정 | 연결 | 도구 호출 | 승인 UX | verdict | exact/normalized/expected | 누락·변경 메모 |
|---|---|---|---|---|---|---|---|---|
| C1 | 짧은 대화 5턴 | | | | | | | |
| C2 | 오타·구어체 | | | | | | | |
| C3 | 긴 대화 31턴 | | | | | | | |
| C4 | 짧은 확인성 턴 | | | | | | | |
| C5 | Free 계정 연결 | | | | | | | |

## 2. ChatGPT 공유 링크

| ID | 시나리오 | probe status | staticViable | extract status | verdict | exact/normalized/expected | 메모 |
|---|---|---|---|---|---|---|---|
| S1 | 짧은 한국어 대화 | | | | | | |
| S2 | 긴 대화 30턴 | | | | | | |
| S3 | 코드 블록·마크다운 | | | | | | |
| S4 | 이미지·파일 첨부 | | | | | | |
| S5 | 공유 후 대화 이어감 | | | | | | |
| S6 | 삭제된 공유 링크 | | | | | | |
| S7 | Free 계정 생성 링크 | | | | | | |

## 3. 결론

- 커넥터:
- 공유 링크:
- spec 반영 필요 사항:
```

- [ ] **Step 4: 서버와 터널 실행**

터미널 A:

```bash
ngrok http 8080
```

출력의 `Forwarding https://<서브도메인>.ngrok-free.app`을 복사한다.

터미널 B:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
cd /Users/wa/golbob/khack/backend
./gradlew bootRun
```

터미널 C:

```bash
curl -s https://<서브도메인>.ngrok-free.app/healthz
```

Expected: `{"ok":true}`

- [ ] **Step 5: Claude에 커스텀 커넥터 등록**

1. claude.ai → 설정 → Connectors → "Add custom connector"
2. 이름: `review-connector-test`, URL: `https://<서브도메인>.ngrok-free.app/mcp`, OAuth 항목은 비워 둔다.
3. 연결 후 도구 목록에 `save_learning_session` 하나만 보이는지 확인한다.
4. 결과 문서 C1 행의 "연결" 칸에 성공 여부를 기록한다.

실패 시 확인 순서:
- 터미널 B 로그에 `/mcp` 요청이 찍히는지 확인한다.
- 찍히지 않으면 ngrok 무료 도메인의 브라우저 경고 페이지가 원인일 수 있다. `brew install cloudflared && cloudflared tunnel --url http://localhost:8080`로 터널을 바꾼다.
- 요청은 찍히는데 403이면 MCP SDK의 Origin 검증 로그를 확인하고 결과 문서에 기록한다.
- 서버를 재시작한 뒤 도구 호출이 실패하면 Claude가 이전 MCP 세션 ID를 쓰는 것이다. 커넥터를 끊었다 다시 연결하고, 결과 문서 메모에 기록한다.

- [ ] **Step 6: 시나리오 C1~C4 실행**

각 시나리오마다:

1. Claude 새 대화를 열고 커넥터를 켠다.
2. 스크립트의 `userTurns`를 **한 줄씩 복사해 그대로** 보낸다 (고치지 않는다).
3. 마지막 "복습에 넣어줘"에서 도구 호출 승인 화면이 뜨면 전송될 인자를 캡처해 두고 승인한다.
4. 터미널 B에 `[save_learning_session] <id> turns=N/M` 로그가 찍히는지 확인한다.
5. 비교 실행:

```bash
./gradlew -q connectorCheck -Pargs="fixtures/scripts/c1-short.json"
```

6. 출력의 `verdict`, `exact`/`normalized`/`expectedCount`, `missing`을 결과 문서에 기록한다. 마지막 저장 요청 턴("복습에 넣어줘")만 누락된 경우는 학습 발화 누락과 구분해 메모한다.

- [ ] **Step 7: 시나리오 C5 (Free 계정)**

Claude Free 계정에서 Step 5를 반복해 커스텀 커넥터 1개 등록과 C1 실행이 가능한지 기록한다.

- [ ] **Step 8: 터널 종료와 Commit**

ngrok과 서버를 종료한다 (Ctrl+C). `backend/data/`는 커밋하지 않는다.

```bash
cd /Users/wa/golbob/khack
git add backend/src/main/java/com/khack/review/verify backend/fixtures docs/verification/results.md
git commit -m "test: verify Claude custom connector end to end"
```

**Phase 1 통과 기준:**
- 커넥터 등록 후 도구 목록에 `save_learning_session`만 보인다.
- C1에서 도구 호출이 서버에 도착하고 `backend/data/sessions.jsonl`에 저장된다.
- C1~C4 모두 비교 결과가 기록되어 있다. 일치율은 합격 기준이 아니라 측정값이며, spec §7.3 대응 5와 §12.2 지표에 반영한다.

---

## Phase 2 — ChatGPT 공유 링크

### Task 5: 정적 HTML probe

공유 페이지를 브라우저 없이 가져왔을 때 대화 내용이 HTML에 들어 있는지 확인한다. 들어 있으면 제품에서는 `HttpClient` 기반 파서를, 없으면 headless 브라우저를 써야 한다.

**Files:**
- Create: `backend/src/main/java/com/khack/review/share/ProbeReport.java`, `ShareProbe.java`, `ShareProbeCli.java`
- Test: `backend/src/test/java/com/khack/review/share/ShareProbeTest.java`

**Interfaces:**
- Consumes: `ConversationScript` (Task 4), `Json.MAPPER`
- Produces:
  - `record ProbeReport(int status, String contentType, int bytes, boolean cloudflare, boolean hasRoleAttr, List<String> needlesFound, List<String> needlesMissing, boolean staticViable)`
  - `ShareProbe.pickNeedles(List<String> userTurns): List<String>`
  - `ShareProbe.analyze(int status, Map<String, String> headers, String body, List<String> needles): ProbeReport`
  - Gradle `./gradlew -q shareProbe -Pargs="<script.json>"` → `data/probe/<name>.html`, 보고서 출력

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/khack/review/share/ShareProbeTest.java`:

```java
package com.khack.review.share;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShareProbeTest {

    private static final Map<String, String> HTML = Map.of("Content-Type", "text/html; charset=utf-8");

    @Test
    void pickNeedlesUsesFirst20CharactersOfFirstAndLastTurn() {
        assertThat(ShareProbe.pickNeedles(List.of("TCP랑 UDP 차이를 쉽게 설명해줘 제발", "중간", "마지막 질문")))
                .containsExactly("TCP랑 UDP 차이를 쉽게 설명해줘", "마지막 질문");
    }

    @Test
    void findsPlainTextNeedlesAndMarksStaticViable() {
        ProbeReport r = ShareProbe.analyze(200, HTML,
                "<div data-message-author-role=\"user\">TCP랑 UDP</div>", List.of("TCP랑 UDP"));
        assertThat(r.staticViable()).isTrue();
        assertThat(r.hasRoleAttr()).isTrue();
        assertThat(r.needlesMissing()).isEmpty();
    }

    @Test
    void findsNeedlesJsonEscapedInsideEmbeddedScriptData() {
        String body = "<script>window.__data=\"TCP\\ub791 \\\"UDP\\\"\"</script>";
        ProbeReport r = ShareProbe.analyze(200, HTML, body, List.of("TCP랑 \"UDP\""));
        assertThat(r.needlesFound()).containsExactly("TCP랑 \"UDP\"");
        assertThat(r.staticViable()).isTrue();
    }

    @Test
    void reportsCloudflareChallengeAsNotViable() {
        ProbeReport r = ShareProbe.analyze(403,
                Map.of("Content-Type", "text/html", "CF-Ray", "abc", "Server", "cloudflare"),
                "Just a moment...", List.of("TCP"));
        assertThat(r.cloudflare()).isTrue();
        assertThat(r.staticViable()).isFalse();
        assertThat(r.needlesMissing()).containsExactly("TCP");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests 'com.khack.review.share.ShareProbeTest'`
Expected: FAIL — `cannot find symbol: class ShareProbe` 컴파일 오류

- [ ] **Step 3: 최소 구현**

`backend/src/main/java/com/khack/review/share/ProbeReport.java`:

```java
package com.khack.review.share;

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
```

`backend/src/main/java/com/khack/review/share/ShareProbe.java`:

```java
package com.khack.review.share;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShareProbe {

    private ShareProbe() {
    }

    public static List<String> pickNeedles(List<String> userTurns) {
        List<String> ends = userTurns.size() > 1
                ? List.of(userTurns.get(0), userTurns.get(userTurns.size() - 1))
                : List.of(userTurns.get(0));
        return ends.stream().map(t -> {
            String s = t.strip();
            return s.substring(0, Math.min(20, s.length())).strip();
        }).toList();
    }

    public static ProbeReport analyze(int status, Map<String, String> headers, String body, List<String> needles) {
        Map<String, String> h = new HashMap<>();
        headers.forEach((k, v) -> h.put(k.toLowerCase(Locale.ROOT), v));
        String lowerBody = body.toLowerCase(Locale.ROOT);

        List<String> found = needles.stream()
                .filter(n -> variants(n).stream().anyMatch(v -> body.contains(v) || lowerBody.contains(v.toLowerCase(Locale.ROOT))))
                .toList();
        List<String> missing = needles.stream().filter(n -> !found.contains(n)).toList();

        return new ProbeReport(
                status,
                h.getOrDefault("content-type", ""),
                body.getBytes(StandardCharsets.UTF_8).length,
                h.containsKey("cf-ray") || h.getOrDefault("server", "").contains("cloudflare"),
                body.contains("data-message-author-role"),
                found,
                missing,
                status == 200 && missing.isEmpty());
    }

    private static List<String> variants(String needle) {
        String json = needle.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        StringBuilder unicode = new StringBuilder();
        for (char c : json.toCharArray()) {
            unicode.append(c > 0x7F ? "\\u%04x".formatted((int) c) : String.valueOf(c));
        }
        String html = needle.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        return List.of(needle, json, unicode.toString(), html);
    }
}
```

`backend/src/main/java/com/khack/review/share/ShareProbeCli.java`:

```java
package com.khack.review.share;

import com.khack.review.support.Json;
import com.khack.review.verify.ConversationScript;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShareProbeCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: ./gradlew -q shareProbe -Pargs=\"<script.json>\"");
            System.exit(1);
        }
        ConversationScript script = ConversationScript.load(Path.of(args[0]));
        if (!script.hasShareUrl()) {
            System.err.println(args[0] + ": shareUrl is empty");
            System.exit(1);
        }

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(script.shareUrl()))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36")
                .header("Accept-Language", "ko,en;q=0.8")
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Path out = Path.of("data/probe", script.name() + ".html");
        Files.createDirectories(out.getParent());
        Files.writeString(out, response.body());

        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((k, v) -> headers.put(k, String.join(",", v)));
        ProbeReport report = ShareProbe.analyze(response.statusCode(), headers, response.body(),
                ShareProbe.pickNeedles(script.userTurns()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("script", script.name());
        result.put("url", script.shareUrl());
        result.put("report", report);
        System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.khack.review.share.ShareProbeTest'`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
cd /Users/wa/golbob/khack
git add backend/src/main/java/com/khack/review/share backend/src/test/java/com/khack/review/share/ShareProbeTest.java
git commit -m "feat: add static probe for ChatGPT share pages"
```

---

### Task 6: Playwright 추출기와 검증 CLI

**Files:**
- Create: `backend/src/main/java/com/khack/review/share/ShareTurn.java`, `ShareExtraction.java`, `ShareStatus.java`, `ShareExtractor.java`, `ShareVerifyCli.java`
- Test: `backend/src/test/java/com/khack/review/share/ShareStatusTest.java`

**Interfaces:**
- Consumes: `TurnComparator` (Task 1), `ConversationScript` (Task 4), `Json.MAPPER`
- Produces:
  - `record ShareTurn(String role, String text)`
  - `record ShareExtraction(String url, int httpStatus, String title, List<ShareTurn> turns)` + `List<String> userTurnTexts()`
  - `enum ShareStatus { OK, NOT_FOUND, BLOCKED, NO_TURNS; static ShareStatus classify(ShareExtraction x) }`
  - `ShareExtractor.extract(String url, boolean headed, String dumpName): ShareExtraction`
  - Gradle `./gradlew -q shareVerify -Pargs="<script.json> [--headed]"` → `data/results/<name>.json`

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/khack/review/share/ShareStatusTest.java`:

```java
package com.khack.review.share;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShareStatusTest {

    private static ShareExtraction page(int status, List<ShareTurn> turns) {
        return new ShareExtraction("https://chatgpt.com/share/x", status, "t", turns);
    }

    @Test
    void notFoundFor404InsteadOfEmptySuccess() {
        assertThat(ShareStatus.classify(page(404, List.of()))).isEqualTo(ShareStatus.NOT_FOUND);
    }

    @Test
    void blockedFor403And429() {
        assertThat(ShareStatus.classify(page(403, List.of()))).isEqualTo(ShareStatus.BLOCKED);
        assertThat(ShareStatus.classify(page(429, List.of()))).isEqualTo(ShareStatus.BLOCKED);
    }

    @Test
    void noTurnsFor200PageWithoutMessages() {
        assertThat(ShareStatus.classify(page(200, List.of()))).isEqualTo(ShareStatus.NO_TURNS);
    }

    @Test
    void okWhenMessagesExist() {
        assertThat(ShareStatus.classify(page(200, List.of(new ShareTurn("user", "hi"))))).isEqualTo(ShareStatus.OK);
    }

    @Test
    void userTurnTextsKeepsOnlyUserTurnsInOrder() {
        ShareExtraction x = page(200, List.of(
                new ShareTurn("user", "Q1"), new ShareTurn("assistant", "A1"), new ShareTurn("user", "Q2")));
        assertThat(x.userTurnTexts()).containsExactly("Q1", "Q2");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew test --tests 'com.khack.review.share.ShareStatusTest'`
Expected: FAIL — `cannot find symbol: class ShareExtraction` 컴파일 오류

- [ ] **Step 3: 결과 타입 구현**

`backend/src/main/java/com/khack/review/share/ShareTurn.java`:

```java
package com.khack.review.share;

public record ShareTurn(String role, String text) {
}
```

`backend/src/main/java/com/khack/review/share/ShareExtraction.java`:

```java
package com.khack.review.share;

import java.util.List;

public record ShareExtraction(String url, int httpStatus, String title, List<ShareTurn> turns) {

    public List<String> userTurnTexts() {
        return turns.stream().filter(t -> "user".equals(t.role())).map(ShareTurn::text).toList();
    }
}
```

`backend/src/main/java/com/khack/review/share/ShareStatus.java`:

```java
package com.khack.review.share;

public enum ShareStatus {
    OK, NOT_FOUND, BLOCKED, NO_TURNS;

    public static ShareStatus classify(ShareExtraction x) {
        return switch (x.httpStatus()) {
            case 404, 410 -> NOT_FOUND;
            case 403, 429, 503 -> BLOCKED;
            default -> x.turns().isEmpty() ? NO_TURNS : OK;
        };
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test`
Expected: 전체 26 tests PASS (Task 5: 4, Task 6: 5 추가)

- [ ] **Step 5: Playwright 추출기와 CLI 구현**

`data-message-author-role`은 ChatGPT 화면이 메시지 역할을 표시하는 속성이다. 공유 페이지에서도 쓰이는지는 Step 7에서 확인한다. Playwright for Java는 첫 실행 때 Chromium을 자동으로 내려받는다.

`backend/src/main/java/com/khack/review/share/ShareExtractor.java`:

```java
package com.khack.review.share;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ShareExtractor {

    static final String MESSAGE_SELECTOR = "[data-message-author-role]";

    private ShareExtractor() {
    }

    public static ShareExtraction extract(String url, boolean headed, String dumpName) {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(!headed))) {
            Page page = browser.newPage(new Browser.NewPageOptions().setLocale("ko-KR"));
            Response response = page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(45_000));
            try {
                page.waitForSelector(MESSAGE_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(20_000));
            } catch (TimeoutError e) {
                // no message elements rendered; classified as NO_TURNS or BLOCKED by the caller
            }

            int previous = -1;
            for (int i = 0; i < 20; i++) {
                int count = page.locator(MESSAGE_SELECTOR).count();
                if (count == previous) {
                    break;
                }
                previous = count;
                page.keyboard().press("End");
                page.mouse().wheel(0, 20_000);
                page.waitForTimeout(700);
            }

            List<ShareTurn> turns = page.locator(MESSAGE_SELECTOR).all().stream()
                    .map(el -> new ShareTurn(
                            Objects.requireNonNullElse(el.getAttribute("data-message-author-role"), "unknown"),
                            el.innerText()))
                    .toList();

            if (dumpName != null) {
                Path dir = Path.of("data/results");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(dumpName + ".html"), page.content());
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve(dumpName + ".png")).setFullPage(true));
            }

            return new ShareExtraction(url, response == null ? 0 : response.status(), page.title(), turns);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

`backend/src/main/java/com/khack/review/share/ShareVerifyCli.java`:

```java
package com.khack.review.share;

import com.khack.review.support.Json;
import com.khack.review.verify.ConversationScript;
import com.khack.review.verify.TurnComparator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShareVerifyCli {

    public static void main(String[] args) throws Exception {
        String scriptPath = Arrays.stream(args).filter(a -> !a.startsWith("--")).findFirst().orElse(null);
        boolean headed = Arrays.asList(args).contains("--headed");
        if (scriptPath == null) {
            System.err.println("usage: ./gradlew -q shareVerify -Pargs=\"<script.json> [--headed]\"");
            System.exit(1);
        }
        ConversationScript script = ConversationScript.load(Path.of(scriptPath));
        if (!script.hasShareUrl()) {
            System.err.println(scriptPath + ": shareUrl is empty");
            System.exit(1);
        }

        ShareExtraction extraction = ShareExtractor.extract(script.shareUrl(), headed, script.name());
        ShareStatus status = ShareStatus.classify(extraction);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("script", script.name());
        report.put("url", script.shareUrl());
        report.put("httpStatus", extraction.httpStatus());
        report.put("status", status);
        report.put("totalTurns", extraction.turns().size());
        report.put("roles", extraction.turns().stream().map(ShareTurn::role).distinct().toList());
        report.put("comparison", status == ShareStatus.OK
                ? TurnComparator.compare(script.userTurns(), extraction.userTurnTexts())
                : null);

        Path out = Path.of("data/results", script.name() + ".json");
        Files.createDirectories(out.getParent());
        Map<String, Object> full = new LinkedHashMap<>(report);
        full.put("extraction", extraction);
        Files.writeString(out, Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(full));
        System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
```

- [ ] **Step 6: 빌드 확인과 Commit**

Run: `./gradlew test`
Expected: 26 tests PASS, 컴파일 오류 없음

```bash
cd /Users/wa/golbob/khack
git add backend/src/main/java/com/khack/review/share backend/src/test/java/com/khack/review/share/ShareStatusTest.java
git commit -m "feat: add Playwright extractor for ChatGPT share links"
```

- [ ] **Step 7: 선택자 확인 (첫 실제 링크)**

Task 7의 S1 링크를 만든 뒤 먼저 실행한다.

```bash
cd backend
./gradlew -q shareVerify -Pargs="fixtures/scripts/s1-short.json"
```

- `status: "OK"`이고 `roles`에 `user`, `assistant`가 있으면 선택자가 맞다.
- `status: "NO_TURNS"`이면 `data/results/s1-short.png`와 `.html`을 열어 메시지 요소의 실제 속성을 확인한다. `ShareExtractor.MESSAGE_SELECTOR`와 역할 추출 코드를 그 속성으로 바꾼 뒤 다시 실행하고, 바꾼 선택자를 결과 문서 메모에 기록한다.
- `status: "BLOCKED"`이면 `-Pargs="fixtures/scripts/s1-short.json --headed"`로 다시 실행한다. headed에서만 성공하면 결과 문서에 "headless 차단"으로 기록한다.

---

### Task 7: 공유 링크 시나리오 검증 (수동)

**Files:**
- Create: `backend/fixtures/scripts/s1-short.json`, `s3-code.json`, `s2-long.json`(생성), `s4-attachment.json`, `s5-continued.json`, `s6-deleted.json`, `s7-free.json`
- Modify: `docs/verification/results.md`

**Interfaces:**
- Consumes: `shareProbe`, `shareVerify`, `longScript` Gradle 태스크 (Task 4, 5, 6)

- [ ] **Step 1: 스크립트 작성**

`backend/fixtures/scripts/s1-short.json`:

```json
{
  "name": "s1-short",
  "platform": "chatgpt",
  "userTurns": [
    "DNS가 어떻게 동작하는지 쉽게 설명해줘",
    "재귀 리졸버랑 권한 있는 네임서버는 뭐가 달라?",
    "TTL이 짧으면 뭐가 안 좋아?"
  ],
  "shareUrl": ""
}
```

`backend/fixtures/scripts/s3-code.json`:

```json
{
  "name": "s3-code",
  "platform": "chatgpt",
  "userTurns": [
    "이 코드 왜 undefined 나와?\n```js\nconst obj = { a: 1 };\nconsole.log(obj.b?.c);\n```",
    "`?.`랑 `&&` 차이가 뭐야?",
    "표로 정리해줘"
  ],
  "shareUrl": ""
}
```

긴 대화:

```bash
cd backend
./gradlew -q longScript -Pargs="s2-long chatgpt"
```

S4·S5·S7은 S1의 `userTurns`를 복사해 `name`만 `s4-attachment`, `s5-continued`, `s7-free`로 바꾼 파일을 만든다. S4는 첫 발화와 함께 이미지 1장을 첨부한다. S6는 S1 링크를 복사해 둔 뒤 ChatGPT에서 공유 링크를 삭제하고, 그 URL을 `shareUrl`에 넣은 `s6-deleted.json`으로 만든다.

- [ ] **Step 2: 대화 생성과 공유 링크 발급**

각 스크립트마다:

1. ChatGPT 새 대화에서 `userTurns`를 한 줄씩 **그대로** 보낸다.
2. 공유 → 링크 생성 → URL을 스크립트의 `shareUrl`에 넣는다.
3. S5는 링크를 만든 뒤 발화 2개("추가 질문 1", "추가 질문 2")를 더 보낸다. 스크립트는 링크 생성 시점까지의 발화만 유지한다.
4. S7은 ChatGPT Free 계정에서 진행한다.

- [ ] **Step 3: 시나리오별 probe와 추출 실행**

```bash
for s in s1-short s2-long s3-code s4-attachment s5-continued s6-deleted s7-free; do
  ./gradlew -q shareProbe -Pargs="fixtures/scripts/$s.json"
  ./gradlew -q shareVerify -Pargs="fixtures/scripts/$s.json"
done
```

각 결과의 probe `status`·`staticViable`, 추출 `status`·`comparison.verdict`·`exact/normalized/expectedCount`를 결과 문서에 기록한다.

기대 결과:
- S1·S2·S3·S7: `status: OK`, `verdict`가 `IDENTICAL` 또는 `WHITESPACE_ONLY`. S3에서 `DIVERGED`가 나오면 `data/results/s3-code.json`의 추출 텍스트를 보고 코드 블록이 어떻게 렌더링되는지 메모한다.
- S2: `expectedCount`와 `actualCount`가 30으로 같아야 한다. 적으면 지연 로딩 문제이므로 메모한다.
- S4: 첨부가 있어도 사용자 발화 텍스트 3개가 모두 추출되는지 기록한다.
- S5: `actualCount`가 3이면 링크 생성 시점 스냅샷, 5면 최신 대화까지 반영된 것이다.
- S6: `status`가 `NOT_FOUND` 또는 `NO_TURNS`이고 `OK`가 아니어야 한다.

- [ ] **Step 4: 결론 작성과 Commit**

결과 문서 "3. 결론"에 다음을 채운다.
- 커넥터: 연결 성공 여부, 평균 일치율, 자주 발생한 변형(오타 교정, 짧은 턴 생략 등)
- 공유 링크: 정적 fetch로 충분한지(`staticViable`) 또는 headless 브라우저가 필요한지, 차단 여부, 사용한 최종 선택자
- spec 반영 필요 사항: §7.3 대응, §7.5 공유 링크 주의점, §12.2 지표 기준값

```bash
cd /Users/wa/golbob/khack
git add backend/fixtures docs/verification/results.md backend/src/main/java/com/khack/review/share/ShareExtractor.java
git commit -m "test: verify ChatGPT share link extraction"
```

**Phase 2 통과 기준:**
- S1·S2·S3에서 모든 사용자 발화가 `normalized` 기준 100% 추출된다.
- S6가 `OK`로 보고되지 않는다.
- 정적 fetch 가능 여부와 headless 필요 여부가 결론에 기록된다.
