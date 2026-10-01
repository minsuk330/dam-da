package com.khack.review.common.adapter.out.typesafe;

import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevQuestion;
import com.khack.review.common.application.port.out.JevResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * TypeSafe System One API(POST /v1/systemone)로 Jev를 호출한다. 재시도는 하지 않는다.
 * API 문서: https://docs.typesafe.ai/api, 사용 규칙: docs/jev.md
 */
@Component
public class TypeSafeJevAdapter implements JevPort {

	private static final Logger log = LoggerFactory.getLogger(TypeSafeJevAdapter.class);

	private final RestClient restClient;
	private final String apiKey;
	private final String model;

	public TypeSafeJevAdapter(RestClient.Builder restClientBuilder,
			@Value("${review.jev.api-key}") String apiKey,
			@Value("${review.jev.base-url}") String baseUrl,
			@Value("${review.jev.model}") String model) {
		this.restClient = restClientBuilder.clone().baseUrl(baseUrl).build();
		this.apiKey = apiKey;
		this.model = model;
	}

	@Override
	public JevResult evaluate(Object state, Map<String, JevQuestion> questions) {
		if (apiKey.isBlank()) {
			throw new IllegalStateException("TYPESAFE_API_KEY가 설정되지 않았습니다 (backend/.env)");
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("state", state);
		body.put("model", model);
		Map<String, Object> questionBodies = new LinkedHashMap<>();
		questions.forEach((name, question) -> questionBodies.put(name, toBody(question)));
		body.put("questions", questionBodies);

		JsonNode response;
		long started = System.nanoTime();
		try {
			response = restClient.post()
				.uri("/v1/systemone")
				.headers(headers -> headers.setBearerAuth(apiKey))
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(JsonNode.class);
		}
		catch (RestClientResponseException e) {
			throw new JevCallException(e.getStatusCode().value(),
					"Jev 호출 실패 " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
		}
		catch (RestClientException e) {
			throw new JevCallException(0, "Jev 호출 실패: " + e.getMessage(), e);
		}
		log.info("[jev] {} {}ms", questions.keySet(), (System.nanoTime() - started) / 1_000_000);
		return toResult(response, questions);
	}

	private static Map<String, Object> toBody(JevQuestion question) {
		Map<String, Object> body = new LinkedHashMap<>();
		switch (question) {
			case JevQuestion.Noul noul -> {
				body.put("type", "noul");
				body.put("instructions", noul.instructions());
				Map<String, String> criteria = new LinkedHashMap<>();
				if (noul.whenTrue() != null) {
					criteria.put("true", noul.whenTrue());
				}
				if (noul.whenFalse() != null) {
					criteria.put("false", noul.whenFalse());
				}
				if (!criteria.isEmpty()) {
					body.put("criteria", criteria);
				}
			}
			case JevQuestion.Choice choice -> {
				body.put("type", "choice");
				body.put("instructions", choice.instructions());
				body.put("criteria", choice.options());
			}
			case JevQuestion.Score score -> {
				body.put("type", "score");
				body.put("instructions", score.instructions());
				body.put("criteria", score.levels());
			}
		}
		return body;
	}

	private static JevResult toResult(JsonNode response, Map<String, JevQuestion> questions) {
		JsonNode answers = required(response, "answers");
		Map<String, JevAnswer> mapped = new LinkedHashMap<>();
		for (String name : questions.keySet()) {
			JsonNode answer = required(answers, name);
			String type = required(answer, "type").asString();
			mapped.put(name, switch (type) {
				case "noul" -> new JevAnswer.Noul(number(answer, "noul"));
				case "choice" -> new JevAnswer.Choice(required(answer, "choice").asString(),
						numbers(answer, "probabilities"), number(answer, "confidence"));
				case "score" -> new JevAnswer.Score(number(answer, "score"), strings(answer, "legend"),
						numbers(answer, "probabilities"), number(answer, "confidence"));
				default -> throw malformed("알 수 없는 답 타입 " + type + " (" + name + ")");
			});
		}
		return new JevResult(required(response, "model").asString(), mapped);
	}

	private static JsonNode required(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			throw malformed("응답에 " + field + " 없음");
		}
		return value;
	}

	private static double number(JsonNode node, String field) {
		JsonNode value = required(node, field);
		if (!value.isNumber()) {
			throw malformed(field + "이(가) 숫자가 아님");
		}
		return value.asDouble();
	}

	private static Map<String, Double> numbers(JsonNode node, String field) {
		Map<String, Double> values = new LinkedHashMap<>();
		JsonNode object = required(node, field);
		for (String key : object.propertyNames()) {
			values.put(key, number(object, key));
		}
		return values;
	}

	private static Map<String, String> strings(JsonNode node, String field) {
		Map<String, String> values = new LinkedHashMap<>();
		JsonNode object = required(node, field);
		for (String key : object.propertyNames()) {
			values.put(key, required(object, key).asString());
		}
		return values;
	}

	private static JevCallException malformed(String message) {
		return new JevCallException(0, "Jev 응답 형식 오류: " + message, null);
	}

}
