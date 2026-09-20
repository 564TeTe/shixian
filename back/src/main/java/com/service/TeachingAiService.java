package com.service;

import static com.utils.TeachingExcel.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.TeachingAccess;
import com.security.TeachingAiSqlGuard;
import com.utils.TeachingExcel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

@Service
public class TeachingAiService {

    private static final Logger LOG = LoggerFactory.getLogger(TeachingAiService.class);

    private final TeachingAccess access;

    private final JdbcTemplate db;

    private final ObjectMapper json;

    private final TeachingAiQueryCompiler compiler;

    private final String baseUrl, key, model, readerUser, readerPassword;

    @Value("${spring.datasource.url}")
    private String applicationDatabaseUrl;

    public TeachingAiService(TeachingAccess access, JdbcTemplate db, ObjectMapper json) {
        this.access = access;
        this.db = db;
        this.json = json;
        this.compiler = new TeachingAiQueryCompiler();
        Map<String, Object> local = localAiConfig(json);
        this.baseUrl = setting("TEACHING_AI_BASE_URL", local);
        this.key = setting("TEACHING_AI_API_KEY", local);
        this.model = setting("TEACHING_AI_MODEL", local);
        this.readerUser = setting("TEACHING_AI_DB_USER", local);
        this.readerPassword = setting("TEACHING_AI_DB_PASSWORD", local);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static String setting(String name, Map<String, Object> local) {
        String value = env(name);
        if (!value.isEmpty()) {
            return value;
        }
        Object localValue = local.get(name);
        return localValue == null ? "" : localValue.toString().trim();
    }

    private static Map<String, Object> localAiConfig(ObjectMapper json) {
        List<Path> candidates = new ArrayList<>();
        String configuredPath = env("TEACHING_AI_CONFIG_FILE");
        if (!configuredPath.isEmpty()) {
            candidates.add(Paths.get(configuredPath));
        }
        candidates.add(Paths.get("database", "generated", "ai-database.local.json"));
        candidates.add(Paths.get("..", "database", "generated", "ai-database.local.json"));
        for (Path path : candidates) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                Map<?, ?> raw = json.readValue(path.toFile(), Map.class);
                Map<String, Object> values = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        values.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                return values;
            } catch (IOException e) {
                LOG.warn("无法读取本地AI配置文件：{}", path.toAbsolutePath());
            }
        }
        return Collections.emptyMap();
    }

    private boolean configured() {
        return !baseUrl.isEmpty()
                && !key.isEmpty()
                && !model.isEmpty()
                && !readerUser.isEmpty()
                && !readerPassword.isEmpty()
                && !"root".equalsIgnoreCase(readerUser);
    }

    public Map<String, Object> status(HttpServletRequest request) {
        Long teacher = access.teacherId(request);
        return map(
                "configured",
                configured(),
                "model",
                model,
                "admin_only",
                true,
                "available",
                configured() && teacher == null,
                "message",
                teacher != null
                        ? "自然语言数据库查询目前仅供管理员使用；教师可在统计报表中查看本人教学范围。"
                        : configured()
                                ? "已配置模型与独立只读数据库账号；查询最多返回200行。"
                                : "尚未完成AI配置，请设置"
                                      + " TEACHING_AI_BASE_URL、TEACHING_AI_API_KEY、TEACHING_AI_MODEL，以及"
                                      + " TEACHING_AI_DB_USER、TEACHING_AI_DB_PASSWORD 只读数据库账号后重启。",
                "examples",
                Arrays.asList(
                        "一共有多少门课程",
                        "列出软件工程的教学任务",
                        "计划实验学时最多的10门课程"));
    }

    public Map<String, Object> query(HttpServletRequest request, Map<String, Object> input) {
        access.requireAdmin(request);
        if (!configured()) {
            throw new IllegalArgumentException("AI模型尚未配置，无法执行自然语言查询");
        }
        if (input == null
                || input.containsKey("sql")
                || !(input.get("question") instanceof String)) {
            throw new IllegalArgumentException("仅接受question自然语言问题，不接受客户端SQL");
        }
        String question = TeachingExcel.required(input.get("question").toString(), "问题", 1000);
        List<Map<String, Object>> termCatalog = db.queryForList(TeachingTermService.SELECT_TERMS);
        List<Map<String, Object>> labCatalog = db.queryForList("SELECT id,lab_code,lab_name FROM laboratory");
        TeachingAiScope scope = TeachingAiScope.resolve(question, input.get("termId"), termCatalog, labCatalog);
        String prompt = compiler.sqlInstructions(metadata()) + scope.instructions()
                + "\n学期目录：" + termCatalog + "\n实验室目录：" + labCatalog;
        String output = model(prompt, question, null, null);
        TeachingAiQueryCompiler.CompiledQuery compiled;
        try {
            compiled = compileScoped(output, scope);
        } catch (IllegalArgumentException first) {
            String repaired = model(prompt, question, output,
                    "上一次结果不合格，请保留用户全部条件并修正JSON。错误：" + first.getMessage());
            compiled = compileScoped(repaired, scope);
        }
        Map<String, Object> result = compiled.isUnsupported() ? unsupported(compiled) : executeCompiled(compiled);
        result.put("scope", scope.description());
        return result;
    }

    private TeachingAiQueryCompiler.CompiledQuery compileScoped(String output, TeachingAiScope scope) {
        TeachingAiQueryCompiler.CompiledQuery compiled = compiler.hasModelSql(json, output)
                ? compiler.modelSql(json, output) : compiler.compile(json, output);
        if (!compiled.isUnsupported()) scope.validate(compiled.getExecutionSql());
        return compiled;
    }

    private Map<String, Object> executeCompiled(
            TeachingAiQueryCompiler.CompiledQuery compiled) {
        String executionSql = TeachingAiSqlGuard.validate(compiled.getExecutionSql());
        return execute(executionSql, compiled);
    }

    static boolean hasUnsupportedTimeScope(String question) {
        return question.matches(
                ".*(本学期|当前学期|上学期|下学期|第[一二12]学期|按学期|按学年|学年|本年度|今年|去年).*");
    }

    private static Map<String, Object> unsupported(
            TeachingAiQueryCompiler.CompiledQuery compiled) {
        String reason = String.valueOf(compiled.getPlan().get("reason"));
        return map(
                "sql",
                "",
                "plan",
                compiled.getPlan(),
                "columns",
                Arrays.asList("message"),
                "rows",
                Arrays.asList(map("message", reason)),
                "truncated",
                false);
    }

    private String model(
            String prompt, String question, String previousOutput, String correction) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(25000);
        RestTemplate client = new RestTemplate(factory);
        client.getMessageConverters()
                .removeIf(converter -> converter instanceof StringHttpMessageConverter);
        client.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        headers.setBearerAuth(key);
        String url = baseUrl.replaceAll("/+$", "");
        if (!url.endsWith("/chat/completions")) {
            url += "/chat/completions";
        }
        if (!url.matches("https?://.+")) {
            throw new IllegalArgumentException("AI服务地址必须是HTTP或HTTPS地址");
        }
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(map("role", "system", "content", prompt));
            messages.add(map("role", "user", "content", question));
            if (previousOutput != null) {
                messages.add(map("role", "assistant", "content", previousOutput));
                messages.add(map("role", "user", "content", correction));
            }
            Map<String, Object> body =
                    map(
                            "model",
                            model,
                            "temperature",
                            0,
                            "max_tokens",
                            4096,
                            "reasoning_effort",
                            "low",
                            "response_format",
                            map("type", "json_object"),
                            "messages",
                            messages);
            String raw =
                    client.postForObject(
                            url,
                            new HttpEntity<>(json.writeValueAsString(body), headers),
                            String.class);
            if (raw == null || raw.trim().isEmpty()) {
                throw new IllegalArgumentException("AI服务返回了空响应");
            }
            JsonNode response = json.readTree(raw);
            String output =
                    response.path("choices")
                            .path(0)
                            .path("message")
                            .path("content")
                            .asText("")
                            .trim();
            if (output.isEmpty()) {
                throw new IllegalArgumentException("AI服务没有返回查询计划");
            }
            return output;
        } catch (HttpStatusCodeException e) {
            String providerMessage =
                    new String(e.getResponseBodyAsByteArray(), StandardCharsets.UTF_8);
            providerMessage =
                    providerMessage.substring(0, Math.min(providerMessage.length(), 1000));
            LOG.warn(
                    "AI provider returned HTTP {}: {}",
                    e.getStatusCode().value(),
                    providerMessage);
            throw new IllegalArgumentException(
                    "AI服务返回HTTP "
                            + e.getStatusCode().value()
                            + "，请查看后端控制台日志确认接口地址、Key和模型配置");
        } catch (java.io.IOException | org.springframework.web.client.RestClientException e) {
            LOG.warn(
                    "AI model request failed: {}: {}",
                    e.getClass().getSimpleName(),
                    e.getMessage());
            throw new IllegalArgumentException("AI服务请求失败，请检查模型配置与网络后重试", e);
        }
    }

    private Map<String, Object> execute(
            String executionSql, TeachingAiQueryCompiler.CompiledQuery compiled) {
        String databaseUrl = env("TEACHING_AI_DB_URL");
        if (databaseUrl.isEmpty()) {
            databaseUrl = applicationDatabaseUrl;
        }
        Properties properties = new Properties();
        properties.setProperty("user", readerUser);
        properties.setProperty("password", readerPassword);
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "7000");
        properties.setProperty("allowMultiQueries", "false");
        try (Connection connection = DriverManager.getConnection(databaseUrl, properties)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(executionSql)) {
                for (int i = 0; i < compiled.getParameters().size(); i++) {
                    statement.setObject(i + 1, compiled.getParameters().get(i));
                }
                statement.setQueryTimeout(5);
                statement.setMaxRows(201);
                statement.setMaxFieldSize(4000);
                try (ResultSet rs = statement.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    List<String> columns = new ArrayList<>();
                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String original = meta.getColumnLabel(i), name = original;
                        int suffix = 2;
                        while (columns.contains(name)) {
                            name = original + "_" + (suffix++);
                        }
                        columns.add(name);
                    }
                    boolean truncated = false;
                    while (rs.next()) {
                        if (rows.size() == compiled.getResultLimit()) {
                            truncated = true;
                            break;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columns.size(); i++) {
                            Object value = rs.getObject(i);
                            if (value instanceof byte[]) {
                                value =
                                        new String(
                                                (byte[]) value,
                                                java.nio.charset.StandardCharsets.UTF_8);
                            }
                            if (value instanceof String && ((String) value).length() > 4000) {
                                value = ((String) value).substring(0, 4000) + "…";
                            }
                            row.put(columns.get(i - 1), value);
                        }
                        rows.add(row);
                    }
                    return map(
                            "sql",
                            compiled.getSql(),
                            "plan",
                            compiled.getPlan(),
                            "columns",
                            columns,
                            "rows",
                            rows,
                            "truncated",
                            truncated);
                }
            } catch (SQLException e) {
                throw new IllegalArgumentException("查询未完成：可能超过5秒限制或模型生成的字段不正确，请简化问题后重试");
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException("无法使用AI只读数据库账号连接，请检查配置及业务表SELECT权限");
        }
    }

    private String metadata() {
        StringBuilder result = new StringBuilder();
        List<Map<String, Object>> columns =
                db.queryForList(
                        "SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,COLUMN_COMMENT FROM"
                            + " information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                            + " ORDER BY TABLE_NAME,ORDINAL_POSITION");
        String currentTable = null;
        for (Map<String, Object> column : columns) {
            String table = String.valueOf(column.get("TABLE_NAME"));
            String name = String.valueOf(column.get("COLUMN_NAME"));
            if (!TeachingAiSqlGuard.allowedTables().contains(table) || sensitiveColumn(name)) {
                continue;
            }
            if (!table.equals(currentTable)) {
                if (currentTable != null) {
                    result.append(")\n");
                }
                currentTable = table;
                result.append(table).append("(");
            } else {
                result.append(",");
            }
            result.append(name)
                    .append(" ")
                    .append(column.get("DATA_TYPE"))
                    .append("：")
                    .append(column.get("COLUMN_COMMENT"));
        }
        if (currentTable != null) {
            result.append(")\n");
        }
        List<Map<String, Object>> relations =
                db.queryForList(
                        "SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME"
                            + " FROM information_schema.KEY_COLUMN_USAGE WHERE"
                            + " TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL"
                            + " ORDER BY TABLE_NAME,ORDINAL_POSITION");
        result.append("关联：");
        for (Map<String, Object> relation : relations) {
            if (!TeachingAiSqlGuard.allowedTables().contains(String.valueOf(relation.get("TABLE_NAME")))
                    || !TeachingAiSqlGuard.allowedTables().contains(String.valueOf(relation.get("REFERENCED_TABLE_NAME")))) continue;
            result.append(relation.get("TABLE_NAME"))
                    .append(".")
                    .append(relation.get("COLUMN_NAME"))
                    .append("=")
                    .append(relation.get("REFERENCED_TABLE_NAME"))
                    .append(".")
                    .append(relation.get("REFERENCED_COLUMN_NAME"))
                    .append("；");
        }
        result.append(
                "course是一门课程的基础资料；teaching_task是一门课程的一次独立开课；"
                        + "planned_lab_hours是单个教学任务的计划实验学时；"
                        + "enrollment_count是单个教学任务的选课人数。\n");
        return result.toString();
    }

    private static boolean sensitiveColumn(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.equals("token")
                || normalized.contains("api_key")
                || normalized.contains("secret");
    }
}
