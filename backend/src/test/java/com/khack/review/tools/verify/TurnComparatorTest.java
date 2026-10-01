package com.khack.review.tools.verify;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.tools.verify.TurnComparison.Missing;
import com.khack.review.tools.verify.TurnComparison.Verdict;
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
