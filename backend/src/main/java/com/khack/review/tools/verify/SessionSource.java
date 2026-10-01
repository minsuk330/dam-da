package com.khack.review.tools.verify;

import com.khack.review.collection.domain.SavedSession;
import com.khack.review.common.json.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.type.TypeReference;

/**
 * tools CLI가 저장된 학습 대화를 읽는 곳. 대화는 DB에 있으므로 실행 중인 서버의 `/dev/sessions.json`에서 받는다.
 * 서버 주소는 Gradle 속성 `-Pserver`(기본 http://localhost:8080). 원격 서버면 환경 변수 `DEV_TOOLS_TOKEN`을 헤더로 보낸다.
 */
public final class SessionSource {

    private static final TypeReference<List<SavedSession>> TYPE = new TypeReference<>() {
    };

    private SessionSource() {
    }

    public static List<SavedSession> list() {
        String server = System.getProperty("review.server", "http://localhost:8080");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server + "/dev/sessions.json"));
        String token = System.getenv("DEV_TOOLS_TOKEN");
        if (token != null && !token.isBlank()) {
            request.header("X-Dev-Token", token);
        }
        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(server + "에 연결할 수 없습니다. 서버(bootRun)를 먼저 띄우세요.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException(server + "/dev/sessions.json 응답 " + response.statusCode()
                    + ". DEV_TOOLS_ENABLED=true인지, 원격이면 DEV_TOOLS_TOKEN이 맞는지 확인하세요.");
        }
        return Json.MAPPER.readValue(response.body(), TYPE);
    }

    /** id가 있으면 그 대화, 없으면 가장 최근 대화. */
    public static Optional<SavedSession> find(String[] args, int idPosition) {
        List<SavedSession> sessions = list();
        if (args.length > idPosition) {
            return sessions.stream().filter(s -> s.id().equals(args[idPosition])).findFirst();
        }
        return sessions.isEmpty() ? Optional.empty() : Optional.of(sessions.get(sessions.size() - 1));
    }
}
