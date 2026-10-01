package com.khack.review.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.port.out.FakeJevPort;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionFieldClassifierTest {

    static final SessionFieldPolicy POLICY = new SessionFieldPolicy(true, 0.5, 3, Duration.ofSeconds(1));
    static final SessionFieldState STATE = new SessionFieldState("InnoDB", List.of("MVCC 읽기", "잠금 읽기"), null);

    final FakeJevPort jev = new FakeJevPort();
    final List<Duration> sleeps = new ArrayList<>();
    final SessionFieldClassifier classifier = new SessionFieldClassifier(jev, new FieldTaxonomy(), POLICY, sleeps::add);

    static JevResult field(String choice, double confidence) {
        return new JevResult("fake", Map.of(SessionFieldQuestions.FIELD, new JevAnswer.Choice(choice, Map.of(), confidence)));
    }

    static JevResult subfield(String choice, double confidence) {
        return new JevResult("fake", Map.of(SessionFieldQuestions.SUBFIELD, new JevAnswer.Choice(choice, Map.of(), confidence)));
    }

    @Test
    void picksFieldThenSubfield() {
        jev.willRespond(field("cs", 0.9), subfield("cs.db", 0.8));

        SessionFieldOutcome outcome = classifier.classify(STATE);

        assertThat(outcome.code()).isEqualTo("cs.db");
        assertThat(jev.calls()).hasSize(2);
        assertThat(jev.calls().get(0).questions()).containsOnlyKeys(SessionFieldQuestions.FIELD);
        assertThat(jev.calls().get(1).questions()).containsOnlyKeys(SessionFieldQuestions.SUBFIELD);
        assertThat(jev.calls().get(1).state()).isEqualTo(STATE.withField("컴퓨터·IT"));
    }

    @Test
    void lowFieldConfidenceLeavesItUnclassified() {
        jev.willRespond(field("cs", 0.4));

        assertThat(classifier.classify(STATE).code()).isEqualTo(FieldTaxonomy.UNCLASSIFIED);
        assertThat(jev.calls()).hasSize(1);
    }

    @Test
    void lowSubfieldConfidenceFallsBackToTheFieldsEtc() {
        jev.willRespond(field("cs", 0.9), subfield("cs.db", 0.3));

        assertThat(classifier.classify(STATE).code()).isEqualTo("cs.etc");
    }

    @Test
    void subfieldOutsideTheChosenFieldFallsBack() {
        jev.willRespond(field("cs", 0.9), subfield("biz.marketing", 0.9));

        assertThat(classifier.classify(STATE).code()).isEqualTo("cs.etc");
    }

    @Test
    void failuresDoNotBlockTheSession() {
        jev.willFail(new JevCallException(401, "unauthorized", null));
        SessionFieldOutcome outcome = classifier.classify(STATE);
        assertThat(outcome.code()).isEqualTo(FieldTaxonomy.UNCLASSIFIED);
        assertThat(outcome.reason()).contains("실패");

        jev.willRespond(field("cs", 0.9), new JevCallException(401, "unauthorized", null));
        assertThat(classifier.classify(STATE).code()).isEqualTo("cs.etc");
    }

    @Test
    void retriesOverload() {
        jev.willRespond(new JevCallException(529, "overloaded", null), field("biz", 0.9), subfield("biz.finance", 0.9));

        assertThat(classifier.classify(STATE).code()).isEqualTo("biz.finance");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
    }
}
