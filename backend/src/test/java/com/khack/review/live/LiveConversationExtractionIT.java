package com.khack.review.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.collection.application.port.out.ConversationExtractor;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.RawConversation;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.SessionValidator;
import com.khack.review.collection.domain.ShareSource;
import com.khack.review.collection.domain.ShareTurn;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.collection.domain.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** 실제 OpenAI로 원문 대화에서 스키마 v5를 추출한다. 발화자가 구분된 공유 링크 형태와 발화자 표시 없는 붙여넣기 형태. */
@Tag("live")
@SpringBootTest
class LiveConversationExtractionIT {

    static final List<ShareTurn> TURNS = List.of(
            new ShareTurn("user", "InnoDB에서 일반 SELECT도 락을 걸어?"),
            new ShareTurn("assistant", "아니요. 일반 SELECT는 MVCC 스냅샷을 읽기 때문에 행에 락을 걸지 않습니다. 락을 걸려면 SELECT ... FOR UPDATE나 FOR SHARE를 써야 합니다."),
            new ShareTurn("user", "그럼 FOR UPDATE는 스캔 다 하고 나서 락 거는 거 아니야?"),
            new ShareTurn("assistant", "아닙니다, 순서가 반대예요. 스캔을 마친 뒤 한꺼번에 거는 게 아니라 스캔하면서 읽는 레코드마다 즉시 배타 락을 겁니다. "
                    + "주의: 인덱스 없이 FOR UPDATE를 쓰면 스캔한 범위 전체가 잠깁니다."),
            new ShareTurn("user", "이 대화 복습에 넣어줘"));

    @Autowired
    Environment env;

    @Autowired
    ConversationExtractor extractor;

    @BeforeEach
    void keys() {
        LiveKeys.require(env);
    }

    @Test
    void shareLinkConversationIsClassifiedPerOriginalTurn() {
        SessionInput input = extractor.extract(RawConversation.fromShareLink(ShareSource.chatgpt, "InnoDB 잠금", TURNS));

        print("share_link", input);
        assertThat(SessionValidator.validate(input).errors()).isEmpty();
        assertThat(input.userTurns()).extracting(UserTurn::text)
                .containsExactly(TURNS.get(0).text(), TURNS.get(2).text(), TURNS.get(4).text());
        assertThat(input.userTurns().get(1).effectiveVerdict()).isIn(AiVerdict.corrected, AiVerdict.partial);
        assertThat(input.userTurns().get(1).correction()).isNotBlank();
        assertThat(input.userTurns().get(2).intent()).isEqualTo(Intent.meta);
        assertThat(input.reviewUnits()).isNotEmpty();
    }

    @Test
    void pastedConversationWithoutSpeakerLabelsIsSplitIntoUserTurns() {
        String pasted = String.join("\n\n", TURNS.stream().map(ShareTurn::text).toList());

        SessionInput input = extractor.extract(RawConversation.fromPaste(pasted));

        print("paste", input);
        ValidationResult validation = SessionValidator.validate(input);
        assertThat(validation.errors()).isEmpty();
        assertThat(input.userTurns()).hasSize(3).allSatisfy(turn -> assertThat(pasted).contains(turn.text()));
        assertThat(input.reviewUnits()).isNotEmpty();
    }

    private static void print(String path, SessionInput input) {
        input.userTurns().forEach(t -> System.out.printf("[live] %s 발화 %d %s %s | %s%n", path, t.index(), t.intent(),
                t.effectiveVerdict(), t.text()));
        input.reviewUnits().forEach(u -> System.out.printf("[live] %s 단위 '%s' 핵심 %d개, 헷갈린 지점 %d개%n", path, u.title(),
                u.keyPoints().size(), u.confusions().size()));
    }
}
