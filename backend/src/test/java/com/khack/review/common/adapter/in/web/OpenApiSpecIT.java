package com.khack.review.common.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 프론트엔드 API 계약 {@code frontend/openapi.json}이 현재 코드와 같은지 확인한다.
 * 다르면 파일을 새로 쓰고 실패한다. 다시 실행하면 통과하므로, 바뀐 파일을 커밋하고
 * {@code frontend}에서 {@code npm run api:types}로 타입을 다시 만든다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiSpecIT {

    static final Path SPEC = Path.of("..", "frontend", "openapi.json");

    @Value("${local.server.port}")
    int port;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlers;

    @Test
    void committedSpecMatchesCode() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        String actual = response.body().strip() + "\n";
        String committed = Files.exists(SPEC) ? Files.readString(SPEC, StandardCharsets.UTF_8) : "";
        if (!actual.equals(committed)) {
            Files.writeString(SPEC, actual, StandardCharsets.UTF_8);
            fail("API 계약이 바뀌어 %s를 다시 썼다. 확인 후 커밋하고 frontend에서 npm run api:types를 실행한다."
                    .formatted(SPEC.toAbsolutePath().normalize()));
        }
    }

    /**
     * springdoc은 스키마 이름을 단순 클래스 이름으로 만들어, 이름이 같은 요청·응답 타입을 말없이 하나로 합친다(#51).
     * 컨트롤러가 주고받는 우리 타입을 따라가며 단순 이름이 겹치는 것이 없는지 확인한다.
     */
    @Test
    void contractTypeNamesAreUnique() {
        Set<Class<?>> seen = new LinkedHashSet<>();
        handlers.getHandlerMethods().values().forEach(method -> {
            collect(method.getMethod().getGenericReturnType(), seen);
            for (MethodParameter parameter : method.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(RequestBody.class)) {
                    collect(parameter.getGenericParameterType(), seen);
                }
            }
        });
        Map<String, List<String>> duplicates = seen.stream()
                .collect(Collectors.groupingBy(Class::getSimpleName, TreeMap::new, Collectors.mapping(Class::getName, Collectors.toList())))
                .entrySet().stream().filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
        assertThat(duplicates).as("계약에서 이름이 겹치는 타입. 하나의 이름을 바꾼다").isEmpty();
    }

    private static void collect(Type type, Set<Class<?>> seen) {
        switch (type) {
            case ParameterizedType parameterized -> {
                collect(parameterized.getRawType(), seen);
                for (Type argument : parameterized.getActualTypeArguments()) {
                    collect(argument, seen);
                }
            }
            case GenericArrayType array -> collect(array.getGenericComponentType(), seen);
            case WildcardType wildcard -> {
                for (Type bound : wildcard.getUpperBounds()) {
                    collect(bound, seen);
                }
            }
            case Class<?> cls when cls.isArray() -> collect(cls.getComponentType(), seen);
            case Class<?> cls when cls.getName().startsWith("com.khack.review.") && seen.add(cls) -> {
                if (cls.isRecord()) {
                    for (RecordComponent component : cls.getRecordComponents()) {
                        collect(component.getGenericType(), seen);
                    }
                }
            }
            default -> {
            }
        }
    }
}
