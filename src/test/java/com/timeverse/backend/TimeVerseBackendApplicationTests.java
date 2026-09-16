package com.timeverse.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.mail.username=test-mail@gmail.com",
    "spring.mail.password=test-password"
})
class TimeVerseBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
