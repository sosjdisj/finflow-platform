package org.example.finflowbackend;

import org.example.finflowbackend.support.MockMapperConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MockMapperConfig.class)
class FinflowBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
