package com.example.yfin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class ApiControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @Test
    void swaggerUiLoads() {
        webTestClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk();
    }
}


