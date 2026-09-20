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

/**
 * 处理自然语言查询请求，将其转换为SQL查询并执行
 * @param request HttpServletRequest对象，包含HTTP请求信息
 * @param input 包含查询参数的Map，必须包含"question"键
 * @return 包含查询结果的Map
 * @throws IllegalArgumentException 当输入参数不合法或AI模型未配置时抛出
 */
    public Map<String, Object> query(HttpServletRequest request, Map<String, Object> input) {
    // 检查管理员权限
        access.requireAdmin(request);
    // 检查AI模型是否已配置
        if (!configured()) {
            throw new IllegalArgumentException("AI模型尚未配置，无法执行自然语言查询");
        }
    // 验证输入参数
        if (input == null
                || input.containsKey("sql")  // 不允许直接传入SQL
                || !(input.get("question") instanceof String)) {  // 必须是自然语言问题
            throw new IllegalArgumentException("仅接受question自然语言问题，不接受客户端SQL");
        }
    // 获取并验证问题内容
        String question = TeachingExcel.required(input.get("question").toString(), "问题", 1000);
    // 尝试直接编译问题
        TeachingAiQueryCompiler.CompiledQuery direct = compiler.fallback(question);
        if (direct != null) {
            return executeCompiled(direct);
        }
    // 获取元数据并生成SQL指令
        String metadata = metadata();
        String prompt = compiler.sqlInstructions(metadata);
    // 调用AI模型获取初始输出
        String modelOutput = model(prompt, question, null, null);
        TeachingAiQueryCompiler.CompiledQuery compiled;
    // 检查模型输出是否包含SQL
        if (compiler.hasModelSql(json, modelOutput)) {
            try {
            // 尝试从模型输出中提取SQL
                compiled = compiler.modelSql(json, modelOutput);
            } catch (IllegalArgumentException first) {
            // 如果SQL不合法，尝试修复
                String repaired =
                        model(
                                prompt,
                                question,
                                modelOutput,
                                "上一个SQL不合格，请只返回修正后的JSON。错误：" + first.getMessage());
                compiled = compiler.modelSql(json, repaired);
            }
        } else {
            try {
            // 尝试编译模型输出
                compiled = compiler.compile(json, modelOutput);
            } catch (IllegalArgumentException first) {
            // 如果编译失败，尝试修复
                String repaired =
                        model(
                                prompt,
                                question,
                                modelOutput,
                                "上一个查询计划不合格，请只返回修正后的JSON。错误：" + first.getMessage());
                compiled = compiler.compile(json, repaired);
            }
        }
    // 检查是否为不支持的查询
        if (compiled.isUnsupported()) {
        // 尝试使用备用方案
            TeachingAiQueryCompiler.CompiledQuery fallback = compiler.fallback(question);
            if (fallback == null) {
                return unsupported(compiled);
            }
            compiled = fallback;
        }
    // 执行编译后的查询
        return executeCompiled(compiled);
    }

/**
 * 执行已编译的查询
 * @param compiled 已编译的查询对象，包含执行SQL等信息
 * @return 包含查询结果的Map，键为字符串类型，值为Object类型
 */
    private Map<String, Object> executeCompiled(
            TeachingAiQueryCompiler.CompiledQuery compiled) {
    // 使用SQL验证器验证执行SQL，确保SQL的安全性
        String executionSql = TeachingAiSqlGuard.validate(compiled.getExecutionSql());
    // 执行验证后的SQL并返回结果
        return execute(executionSql, compiled);
    }

    static boolean hasUnsupportedTimeScope(String question) {
        return question.matches(
                ".*(本学期|当前学期|上学期|下学期|第[一二12]学期|按学期|按学年|学年|本年度|今年|去年).*");
    }

/**
 * 处理不支持的查询编译结果，返回一个包含错误信息的Map
 *
 * @param compiled 已编译的查询对象，包含查询计划和相关信息
 * @return 包含错误信息的Map，其中包含空的SQL、原始计划、错误消息列和行
 */
    private static Map<String, Object> unsupported(
            TeachingAiQueryCompiler.CompiledQuery compiled) {
    // 从编译计划中获取不支持的原因
        String reason = String.valueOf(compiled.getPlan().get("reason"));
    // 构建并返回包含错误信息的Map
        return map(
                "sql",           // SQL语句（此处为空）
                "",              // 空字符串表示不生成SQL
                "plan",          // 查询计划
                compiled.getPlan(), // 原始查询计划
                "columns",       // 列信息
                Arrays.asList("message"), // 只包含消息列
                "rows",          // 数据行
                Arrays.asList(map("message", reason)), // 包含错误消息的行
                "truncated",     // 是否截断
                false);          // 不截断
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

/**
 * 生成数据库表的元数据信息，包括表结构、字段注释和表间关联关系
 * @return 返回格式化的元数据字符串，包含表结构信息和关联关系
 */
    private String metadata() {
    // 使用StringBuilder构建结果字符串，提高性能
        StringBuilder result = new StringBuilder();
    // 查询数据库中所有表的字段信息
        List<Map<String, Object>> columns =
                db.queryForList(
                        "SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,COLUMN_COMMENT FROM"
                            + " information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                            + " ORDER BY TABLE_NAME,ORDINAL_POSITION");
    // 初始化当前表名为空
        String currentTable = null;
    // 遍历所有字段信息
        for (Map<String, Object> column : columns) {
        // 获取表名和字段名
            String table = String.valueOf(column.get("TABLE_NAME"));
            String name = String.valueOf(column.get("COLUMN_NAME"));
        // 跳过敏感字段
            if (sensitiveColumn(name)) {
                continue;
            }
        // 如果是新的表
            if (!table.equals(currentTable)) {
            // 如果不是第一个表，先闭合上一个表的括号
                if (currentTable != null) {
                    result.append(")\n");
                }
            // 更新当前表名并开始新的表定义
                currentTable = table;
                result.append(table).append("(");
            } else {
            // 同一个表的字段用逗号分隔
                result.append(",");
            }
        // 添加字段信息：字段名、数据类型和注释
            result.append(name)
                    .append(" ")
                    .append(column.get("DATA_TYPE"))
                    .append("：")
                    .append(column.get("COLUMN_COMMENT"));
        }
    // 如果存在表定义，闭合最后一个表的括号
        if (currentTable != null) {
            result.append(")\n");
        }
    // 查询表间的关联关系
        List<Map<String, Object>> relations =
                db.queryForList(
                        "SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME"
                            + " FROM information_schema.KEY_COLUMN_USAGE WHERE"
                            + " TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL"
                            + " ORDER BY TABLE_NAME,ORDINAL_POSITION");
    // 添加关联关系的标题
        result.append("关联：");
    // 遍历并添加所有关联关系
        for (Map<String, Object> relation : relations) {
            result.append(relation.get("TABLE_NAME"))
                    .append(".")
                    .append(relation.get("COLUMN_NAME"))
                    .append("=")
                    .append(relation.get("REFERENCED_TABLE_NAME"))
                    .append(".")
                    .append(relation.get("REFERENCED_COLUMN_NAME"))
                    .append("；");
        }
    // 添加额外的说明信息
        result.append(
                "course是一门课程的基础资料；teaching_task是一门课程的一次独立开课；"
                        + "planned_lab_hours是单个教学任务的计划实验学时；"
                        + "enrollment_count是单个教学任务的选课人数。\n");
    // 返回完整的元数据字符串
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
