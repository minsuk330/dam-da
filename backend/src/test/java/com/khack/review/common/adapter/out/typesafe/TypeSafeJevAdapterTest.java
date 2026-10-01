package com.khack.review.common.adapter.out.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevQuestion;
import com.khack.review.common.application.port.out.JevResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TypeSafeJevAdapterTest {

	MockRestServiceServer server;
	TypeSafeJevAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new TypeSafeJevAdapter(builder, "test-key", "https://jev.test", "jev-latest");
	}

	@Test
	void sendsTypedQuestionsAndMapsAnswers() {
		server.expect(requestTo("https://jev.test/v1/systemone"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer test-key"))
			.andExpect(content().json("""
					{
					  "state": "답변 텍스트",
					  "model": "jev-latest",
					  "questions": {
					    "grounded": {"type": "noul", "instructions": "근거가 있는가?",
					                 "criteria": {"true": "원문에 있음", "false": "원문에 없음"}},
					    "status": {"type": "choice", "instructions": "답변 상태는?",
					               "criteria": {"correct": "정답", "partial": null}},
					    "clarity": {"type": "score", "instructions": "명확한가?",
					                "criteria": ["모호", "보통", "명확"]}
					  }
					}""", JsonCompareMode.STRICT))
			.andRespond(withSuccess("""
					{
					  "model": "jev-1.13.0",
					  "answers": {
					    "grounded": {"type": "noul", "noul": 0.95},
					    "status": {"type": "choice", "choice": "partial",
					               "probabilities": {"correct": 0.2, "partial": 0.8}, "confidence": 0.7},
					    "clarity": {"type": "score", "score": 1.6,
					                "legend": {"0": "모호", "1": "보통", "2": "명확"},
					                "probabilities": {"0": 0.0, "1": 0.4, "2": 0.6}, "confidence": 0.5}
					  },
					  "usage": {"input_tokens": 300, "output_tokens": 30}
					}""", MediaType.APPLICATION_JSON));

		Map<String, String> options = new LinkedHashMap<>();
		options.put("correct", "정답");
		options.put("partial", null);
		JevResult result = adapter.evaluate("답변 텍스트", Map.of(
				"grounded", JevQuestion.noul("근거가 있는가?", "원문에 있음", "원문에 없음"),
				"status", JevQuestion.choice("답변 상태는?", options),
				"clarity", JevQuestion.score("명확한가?", List.of("모호", "보통", "명확"))));

		server.verify();
		assertThat(result.model()).isEqualTo("jev-1.13.0");
		assertThat(result.noul("grounded").probability()).isEqualTo(0.95);
		assertThat(result.choice("status").choice()).isEqualTo("partial");
		assertThat(result.choice("status").confidence()).isEqualTo(0.7);
		assertThat(result.score("clarity").score()).isEqualTo(1.6);
		assertThat(result.score("clarity").levels()).containsEntry("2", "명확");
	}

	@Test
	void wrapsHttpErrorWithStatus() {
		server.expect(requestTo("https://jev.test/v1/systemone"))
			.andRespond(withStatus(HttpStatusCode.valueOf(529)).body("{\"error\":\"overloaded\"}"));

		assertThatThrownBy(() -> adapter.evaluate("x", Map.of("q", JevQuestion.noul("?"))))
			.isInstanceOfSatisfying(JevCallException.class, e -> {
				assertThat(e.status()).isEqualTo(529);
				assertThat(e.retryable()).isTrue();
			});
	}

	@Test
	void rejectsAnswerMissingRequiredNumber() {
		server.expect(requestTo("https://jev.test/v1/systemone"))
			.andRespond(withSuccess("""
					{"model": "jev-1.13.0", "answers": {"q": {"type": "noul"}}}""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.evaluate("x", Map.of("q", JevQuestion.noul("?"))))
			.isInstanceOf(JevCallException.class)
			.hasMessageContaining("noul");
	}

	@Test
	void failsFastWithoutApiKey() {
		var noKey = new TypeSafeJevAdapter(RestClient.builder(), "", "https://jev.test", "jev-latest");

		assertThatThrownBy(() -> noKey.evaluate("x", Map.of("q", JevQuestion.noul("?"))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TYPESAFE_API_KEY");
	}

}
