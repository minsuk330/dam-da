package com.khack.review.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.khack.review.common.application.port.out.TossLoginException;
import com.khack.review.common.application.port.out.TossLoginPort;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 앱인토스 서버에 mTLS로 붙는지 확인한다(#149). 진짜 인가 코드는 토스 앱에서만 나오므로 가짜 코드를 보내고,
 * TLS 연결이 아니라 토스가 코드를 거절해서 실패하는지 본다. backend/.env에 TOSS_MTLS_CERT·TOSS_MTLS_KEY(PEM 경로)가 필요하다.
 */
@Tag("live")
@SpringBootTest
class LiveTossLoginIT {

    @Autowired
    TossLoginPort toss;

    @Test
    void mtlsHandshakeSucceedsAndTossRejectsAFakeCode() {
        TossLoginException e = catchThrowableOfType(TossLoginException.class, () -> toss.login("not-a-real-code", "SANDBOX"));

        assertThat(e).as("가짜 코드는 거절돼야 한다").isNotNull();
        assertThat(e.getMessage()).doesNotContain("TOSS_MTLS_CERT");
        Throwable cause = e;
        while (cause != null) {
            assertThat(cause).as("TLS 연결 실패: " + e.getMessage()).isNotInstanceOf(SSLException.class);
            cause = cause.getCause();
        }
        System.out.println("[live-toss] " + e.getMessage());
    }

    @Test
    void anonymousKeyExchangeReachesTossAndRejectsAFakeCode() {
        TossLoginException e = catchThrowableOfType(TossLoginException.class, () -> toss.anonymousKey("not-a-real-code"));

        assertThat(e).as("가짜 코드는 거절돼야 한다").isNotNull();
        Throwable cause = e;
        while (cause != null) {
            assertThat(cause).as("TLS 연결 실패: " + e.getMessage()).isNotInstanceOf(SSLException.class);
            cause = cause.getCause();
        }
        System.out.println("[live-toss-anon] " + e.getMessage());
    }
}
