package com.teaching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static com.teaching.TeachingExcel.map;

/** Calls a loopback mock model and the real, separately privileged teaching reader. */
@EnabledIfEnvironmentVariable(named="TEACHING_AI_DB_TEST",matches="1")
class TeachingAiDatabaseTest {
    private HttpServer modelServer; private TeachingAiService service; private JdbcTemplate db;
    private MockHttpServletRequest admin; private final AtomicReference<String> modelSql=new AtomicReference<>();
    private final AtomicReference<JsonNode> modelRequest=new AtomicReference<>();
    private final ObjectMapper json=new ObjectMapper();
    @BeforeEach void begin() throws Exception {
        db=new JdbcTemplate(new DriverManagerDataSource(System.getenv("TEACHING_TEST_DB_URL"),System.getenv("TEACHING_TEST_DB_USER"),System.getenv("TEACHING_TEST_DB_PASSWORD")));
        admin=new MockHttpServletRequest();admin.getSession().setAttribute("tableName","users");admin.getSession().setAttribute("userId",db.queryForObject("SELECT MIN(id) FROM users",Long.class));
        modelServer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        modelServer.createContext("/v1/chat/completions",exchange->{
            assertEquals("Bearer local-mock-key",exchange.getRequestHeaders().getFirst("Authorization"));
            modelRequest.set(json.readTree(exchange.getRequestBody()));
            byte[] response=json.writeValueAsBytes(map("choices",Collections.singletonList(map("message",map("content",modelSql.get())))));
            exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8"); exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response);exchange.close();
        });
        modelServer.start();
        service=new TeachingAiService(new TeachingAccess(db),db,json);
        ReflectionTestUtils.setField(service,"baseUrl","http://127.0.0.1:"+modelServer.getAddress().getPort()+"/v1");
        ReflectionTestUtils.setField(service,"key","local-mock-key"); ReflectionTestUtils.setField(service,"model","local-mock-model");
        ReflectionTestUtils.setField(service,"applicationDatabaseUrl",System.getenv("TEACHING_TEST_DB_URL"));
        assertEquals(true,service.status(admin).get("configured"));
    }
    @AfterEach void close() { if(modelServer!=null) modelServer.stop(0); }
    private Map<String,Object> query(String sql) { modelSql.set(sql); return service.query(admin,map("question","验证本地教学统计")); }
    @Test void executesModelSqlAndPreservesTopTenWhileTruncatingLargeResult() {
        Map<String,Object> top=query("```sql\nSELECT id,course_name_snapshot FROM teaching_task ORDER BY id LIMIT 10\n```");
        assertEquals(10,((List<?>)top.get("rows")).size()); assertEquals(false,top.get("truncated"));
        String prompt=modelRequest.get().path("messages").path(0).path("content").asText();
        assertTrue(prompt.contains("schedule_detail("));assertTrue(prompt.contains("laboratory("));
        assertFalse(prompt.contains("password"));assertFalse(prompt.contains("teacher("));assertFalse(prompt.contains("users("));
        Map<String,Object> all=query("SELECT id FROM teaching_task ORDER BY id");
        assertEquals(200,((List<?>)all.get("rows")).size());assertEquals(true,all.get("truncated"));
        Map<String,Object> aggregate=query("SELECT COUNT(*) AS tasks FROM teaching_task");
        Number count=(Number)((Map<?,?>)((List<?>)aggregate.get("rows")).get(0)).get("tasks");
        assertEquals(db.queryForObject("SELECT COUNT(*) FROM teaching_task",Long.class).longValue(),count.longValue());
    }
    @Test void duplicateResultAliasesRemainDistinctWithoutLosingValues() {
        Map<String,Object> answer=query("SELECT 1 AS x,2 AS x_3,3 AS x FROM course LIMIT 1");
        List<?> columns=(List<?>)answer.get("columns"); Map<?,?> row=(Map<?,?>)((List<?>)answer.get("rows")).get(0);
        assertEquals(3,new HashSet<>(columns).size()); assertEquals(3,row.size());
        assertEquals(3,((Number)row.get(columns.get(2))).intValue());
    }
    @Test void rejectsUnsafeModelOutputAndClientSqlAndUsesSelectOnlyReader() throws Exception {
        for(String sql:new String[]{"SELECT * FROM users","SELECT password FROM teacher","SELECT GET_LOCK('teaching-ai-test',1) FROM course","UPDATE course SET course_name='x'","SELECT id FROM course; DELETE FROM course"}) assertThrows(IllegalArgumentException.class,()->query(sql),sql);
        assertThrows(IllegalArgumentException.class,()->service.query(admin,map("question","测试","sql","SELECT * FROM course")));
        try(Connection connection=DriverManager.getConnection(System.getenv("TEACHING_TEST_DB_URL"),System.getenv("TEACHING_AI_DB_USER"),System.getenv("TEACHING_AI_DB_PASSWORD"));Statement statement=connection.createStatement()) {
            try(ResultSet identity=statement.executeQuery("SELECT CURRENT_USER()")) { assertTrue(identity.next()); assertTrue(identity.getString(1).startsWith(System.getenv("TEACHING_AI_DB_USER")+"@")); assertFalse(identity.getString(1).startsWith("root@")); }
            for(String sql:new String[]{"SELECT * FROM users LIMIT 0","SELECT * FROM teacher LIMIT 0","UPDATE course SET course_name=course_name WHERE 1=0"}) assertThrows(SQLException.class,()->statement.execute(sql),sql);
        }
    }
    @Test void expensiveReadonlyQueryIsCancelledWithinTheConfiguredDeadline() {
        long start=System.nanoTime();
        IllegalArgumentException error=assertThrows(IllegalArgumentException.class,()->query("SELECT COUNT(*) AS total FROM schedule_detail a JOIN schedule_detail b ON a.id>=0 JOIN schedule_detail c ON b.id>=0"));
        assertTrue(error.getMessage().contains("查询未完成"));
        assertTrue((System.nanoTime()-start)/1000000<12000,"SQL timeout should cancel the query within twelve seconds including local model overhead");
    }
}
