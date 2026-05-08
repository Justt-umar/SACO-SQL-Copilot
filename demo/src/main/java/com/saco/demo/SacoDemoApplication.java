package com.saco.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * S.A.C.O. Web Demo — Spring Boot entry point.
 *
 * Serves the static landing page (src/main/resources/static/index.html)
 * and exposes a REST API at /api/chat that proxies to Google Gemini.
 */
@SpringBootApplication
public class SacoDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SacoDemoApplication.class, args);
    }
}
