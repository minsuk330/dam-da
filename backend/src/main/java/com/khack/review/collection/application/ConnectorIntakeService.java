package com.khack.review.collection.application;

import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.SessionStore;
import com.khack.review.collection.domain.SessionValidator;
import com.khack.review.collection.domain.ValidationResult;
import org.springframework.stereotype.Service;

/**
 * 커넥터 입력을 검증해 저장한다. 구조 오류는 거부하고, 의미상 이상은 경고로 함께 저장한다.
 */
@Service
public class ConnectorIntakeService {

    private final SessionStore store;

    public ConnectorIntakeService(SessionStore store) {
        this.store = store;
    }

    public SavedSession intake(SessionInput input) {
        ValidationResult result = SessionValidator.validate(input);
        if (!result.errors().isEmpty()) {
            throw new SessionRejectedException(result.errors());
        }
        return store.save(input, result.warnings());
    }
}
