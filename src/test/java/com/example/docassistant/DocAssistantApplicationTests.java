package com.example.docassistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DocAssistantApplicationTests {

    @Test
    @DisplayName("Application context loads successfully without a live Ollama connection")
    void contextLoads() {
    }
}
