package com.service;

import static com.utils.TeachingExcel.map;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.TeachingAccess;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Calls a loopback mock model and the real, separately privileged teaching reader. */
@EnabledIfEnvironmentVariable(named = "TEACHING_AI_DB_TEST", matches = "1")
class TeachingAiDatabaseTest {
    private HttpServer modelServer;
    private TeachingAiService service;
    private JdbcTemplate db;
    private MockHttpServletRequest admin;
    private final AtomicReference<String> modelOutput = new AtomicReference<>();
    private final AtomicReference<JsonNode> modelRequest = new AtomicReference<>();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void begin() throws Exception {
        db =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                System.getenv("TEACHING_TEST_DB_URL"),
                                System.getenv("TEACHING_TEST_DB_USER"),
                                System.getenv("TEACHING_TEST_DB_PASSWORD")));
        admin = new MockHttpServletRequest();
        admin.getSession().setAttribute("tableName", "users");
        admin.getSession()
                .setAttribute(
                        "userId",
                        db.queryForObject(
                                "SELECT MIN(id) FROM account WHERE role='ADMIN'", Long.class));
        modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelServer.createContext(
                "/v1/chat/completions",
                exchange -> {
                    assertEquals(
                            "Bearer local-mock-key",
                            exchange.getRequestHeaders().getFirst("Authorization"));
                    modelRequest.set(json.readTree(exchange.getRequestBody()));
                    byte[] response =
                            json.writeValueAsBytes(
                                    map(
                                            "choices",
                                            Collections.singletonList(
                                                    map(
                                                            "message",
                                                            map("content", modelOutput.get())))));
                    exchange.getResponseHeaders()
                            .set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        modelServer.start();
        service = new TeachingAiService(new TeachingAccess(db), db, json);
        ReflectionTestUtils.setField(
                service,
                "baseUrl",
                "http://127.0.0.1:" + modelServer.getAddress().getPort() + "/v1");
        ReflectionTestUtils.setField(service, "key", "local-mock-key");
        ReflectionTestUtils.setField(service, "model", "local-mock-model");
        ReflectionTestUtils.setField(
                service, "applicationDatabaseUrl", System.getenv("TEACHING_TEST_DB_URL"));
        assertEquals(true, service.status(admin).get("configured"));
    }

    @AfterEach
    void close() {
        if (modelServer != null) modelServer.stop(0);
    }

    private Map<String, Object> query(String output) {
        modelOutput.set(output);
        return service.query(admin, map("question", "验证本地教学统计"));
    }

    @Test
    void executesModelSqlAndPreservesTopTenWhileTruncatingLargeResult() {
        Map<String, Object> top =
                query(
                        "```json\n"
                            + "{\"sql\":\"SELECT t.task_code,c.course_code,"
                            + "t.course_name_snapshot AS course_name,t.class_composition,"
                            + "t.enrollment_count,t.planned_lab_hours FROM teaching_task t "
                            + "JOIN course c ON c.id=t.course_id ORDER BY c.course_code,"
                            + "t.task_code LIMIT 10\"}\n"
                            + "```");
        assertEquals(10, ((List<?>) top.get("rows")).size());
        assertEquals(
                db.queryForObject("SELECT COUNT(*) FROM teaching_task", Long.class) > 10,
                top.get("truncated"));
        String prompt = modelRequest.get().path("messages").path(0).path("content").asText();
        assertTrue(prompt.contains("course("));
        assertTrue(prompt.contains("teaching_task("));
        assertTrue(prompt.contains("课程主键"));
        assertEquals("low", modelRequest.get().path("reasoning_effort").asText());
        assertEquals(
                "json_object",
                modelRequest.get().path("response_format").path("type").asText());
        assertFalse(prompt.contains("password_hash"));
        assertFalse(prompt.contains("token varchar"));
        Map<String, Object> all =
                query(
                        "{\"sql\":\"SELECT t.task_code,c.course_code,"
                            + "t.course_name_snapshot AS course_name,t.class_composition,"
                            + "t.enrollment_count,t.planned_lab_hours FROM teaching_task t "
                            + "JOIN course c ON c.id=t.course_id ORDER BY c.course_code,"
                            + "t.task_code LIMIT 200\"}");
        assertEquals(200, ((List<?>) all.get("rows")).size());
        assertEquals(true, all.get("truncated"));
        Map<String, Object> aggregate =
                query(
                        "{\"sql\":\"SELECT COUNT(*) AS task_count FROM teaching_task LIMIT 1\"}");
        Number count =
                (Number)
                        ((Map<?, ?>) ((List<?>) aggregate.get("rows")).get(0))
                                .get("task_count");
        assertEquals(
                db.queryForObject("SELECT COUNT(*) FROM teaching_task", Long.class).longValue(),
                count.longValue());
        assertFalse(aggregate.containsKey("plan"));
    }

    @Test
    void executesModelSqlWithoutPlanMetadata() {
        Map<String, Object> answer =
                query(
                        "{\"sql\":\"SELECT c.course_code,c.course_name FROM course c "
                            + "ORDER BY c.course_code LIMIT 20\"}");
        assertTrue(answer.get("sql").toString().contains("FROM course c"));
        assertFalse(((List<?>) answer.get("rows")).isEmpty());
    }

    @Test
    void rejectsUnsafeModelOutputAndClientSqlAndUsesSelectOnlyReader() throws Exception {
        for (String sql :
                new String[] {
                    "{\"sql\":\"SELECT * FROM users\"}",
                    "{\"sql\":\"SELECT password FROM teacher\"}",
                    "{\"sql\":\"SELECT GET_LOCK('teaching-ai-test',1) FROM course\"}",
                    "{\"sql\":\"UPDATE course SET course_name='x'\"}"
                }) assertThrows(IllegalArgumentException.class, () -> query(sql), sql);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.query(admin, map("question", "测试", "sql", "SELECT * FROM course")));
        try (Connection connection =
                        DriverManager.getConnection(
                                System.getenv("TEACHING_TEST_DB_URL"),
                                System.getenv("TEACHING_AI_DB_USER"),
                                System.getenv("TEACHING_AI_DB_PASSWORD"));
                Statement statement = connection.createStatement()) {
            try (ResultSet identity = statement.executeQuery("SELECT CURRENT_USER()")) {
                assertTrue(identity.next());
                assertTrue(
                        identity.getString(1)
                                .startsWith(System.getenv("TEACHING_AI_DB_USER") + "@"));
                assertFalse(identity.getString(1).startsWith("root@"));
            }
            for (String sql :
                    new String[] {
                        "SELECT * FROM users LIMIT 0",
                        "SELECT * FROM teacher LIMIT 0",
                        "UPDATE course SET course_name=course_name WHERE 1=0"
                    }) assertThrows(SQLException.class, () -> statement.execute(sql), sql);
        }
    }

}
