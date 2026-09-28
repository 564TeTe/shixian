package com.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.TeachingAccess;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class TeachingAiModelRequestTest {
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"deepseek-flash", "deepseek-v4-pro"})
    void deepSeekSqlRequestsUseNonThinkingModeWithoutLegacyEffort(String model) throws Exception {
        AtomicReference<JsonNode> captured = new AtomicReference<>();
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/chat/completions", exchange -> {
            JsonNode request = json.readTree(exchange.getRequestBody());
            captured.set(request);
            boolean compatible = "disabled".equals(request.path("thinking").path("type").asText())
                    && !request.has("reasoning_effort");
            byte[] response = (compatible
                    ? "{\"choices\":[{\"message\":{\"content\":\"{\\\"sql\\\":\\\"SELECT COUNT(*) FROM course\\\"}\"}}]}"
                    : "{\"error\":{\"message\":\"SQL demo requires non-thinking mode without legacy effort\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(compatible ? 200 : 400, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        provider.start();
        try {
            TeachingAiService service = service(provider, model);
            String result = ReflectionTestUtils.invokeMethod(
                    service, "model", "Return SQL as JSON", "Count courses", null, null);
            assertEquals("SELECT COUNT(*) FROM course", json.readTree(result).path("sql").asText());
            assertEquals(model, captured.get().path("model").asText());
            assertEquals("json_object", captured.get().path("response_format").path("type").asText());
        } finally {
            provider.stop(0);
        }
    }

    @Test
    void otherProvidersKeepTheirExistingRequestAndCorrectionMessages() throws Exception {
        AtomicReference<JsonNode> captured = new AtomicReference<>();
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/chat/completions", exchange -> {
            captured.set(json.readTree(exchange.getRequestBody()));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        provider.start();
        try {
            TeachingAiService service = service(provider, "existing-provider-model");
            String result = ReflectionTestUtils.invokeMethod(
                    service, "model", "Return SQL as JSON", "Count courses", "old SQL", "Fix the SQL");
            assertEquals("ok", result);
            assertFalse(captured.get().has("thinking"));
            assertEquals("low", captured.get().path("reasoning_effort").asText());
            assertEquals("old SQL", captured.get().path("messages").path(2).path("content").asText());
            assertEquals("Fix the SQL", captured.get().path("messages").path(3).path("content").asText());
        } finally {
            provider.stop(0);
        }
    }

    private TeachingAiService service(HttpServer provider, String model) {
        TeachingAiService service = new TeachingAiService(
                mock(TeachingAccess.class), mock(JdbcTemplate.class), json);
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + provider.getAddress().getPort());
        ReflectionTestUtils.setField(service, "key", "test-only-key");
        ReflectionTestUtils.setField(service, "model", model);
        return service;
    }
}
