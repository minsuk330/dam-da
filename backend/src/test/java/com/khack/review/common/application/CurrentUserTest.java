package com.khack.review.common.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.domain.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CurrentUserTest {

    @Autowired
    CurrentUser currentUser;

    @Autowired
    AppUserRepository users;

    @Test
    void createsTheDemoUserOnceAtStartup() {
        Long first = currentUser.id();
        Long second = currentUser.id();

        assertThat(first).isNotNull().isEqualTo(second);
        assertThat(users.findAll()).singleElement()
                .satisfies(user -> assertThat(user.getName()).isEqualTo("지원"));
    }
}
