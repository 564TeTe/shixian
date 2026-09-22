package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.TeachingAiSqlGuard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Parses AI output and compiles the legacy structured query plans. */
final class TeachingAiQueryCompiler {

    static final int MAX_LIMIT = 200;

    private static final Set<String> PLAN_FIELDS =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "intent",
                                    "courseCode",
                                    "courseName",
                                    "limit",
                                    "reason")));

    private static final List<String> INTENTS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "LIST_COURSES",
                            "COUNT_COURSES",
                            "LIST_TASKS",
                            "COUNT_TASKS",
                            "TASK_COUNT_BY_COURSE",
                            "PLANNED_HOURS_BY_COURSE",
                            "ENROLLMENT_BY_COURSE",
                            "UNSUPPORTED"));

    CompiledQuery compile(ObjectMapper json, String modelOutput) {
        JsonNode plan = readPlan(json, modelOutput);
        validateFields(plan);
        String intent = text(plan, "intent").toUpperCase(Locale.ROOT);
        String reason = optionalText(plan, "reason", 300);
        if (!INTENTS.contains(intent)) {
            throw new IllegalArgumentException("AI返回了不支持的查询意图：" + intent);
        }
        if ("UNSUPPORTED".equals(intent)) {
            return unsupported(reason.isEmpty() ? "当前查询无法安全回答" : reason);
        }
        if (!reason.isEmpty()) {
            throw new IllegalArgumentException("受支持的AI查询计划中reason必须为空");
        }
        String courseCode = optionalText(plan, "courseCode", 64);
        String courseName = optionalText(plan, "courseName", 200);
        int limit = integer(plan, "limit", 20, 1, MAX_LIMIT);
        List<Object> parameters = new ArrayList<>();
        boolean taskQuery = !"LIST_COURSES".equals(intent) && !"COUNT_COURSES".equals(intent);
        String where = filters(courseCode, courseName, parameters, taskQuery);
        String baseSql;
        boolean aggregate = "COUNT_COURSES".equals(intent) || "COUNT_TASKS".equals(intent);

        switch (intent) {
            case "LIST_COURSES":
                baseSql =
                        "SELECT c.course_code,c.course_name FROM course c"
                                + where
                                + " ORDER BY c.course_code";
                break;
            case "COUNT_COURSES":
                baseSql = "SELECT COUNT(*) AS course_count FROM course c" + where;
                break;
            case "LIST_TASKS":
                baseSql =
                        "SELECT t.task_code,c.course_code,t.course_name_snapshot AS course_name,"
                                + "t.class_composition,t.enrollment_count,t.planned_lab_hours"
                                + " FROM teaching_task t JOIN course c ON c.id=t.course_id"
                                + where
                                + " ORDER BY c.course_code,t.task_code";
                break;
            case "COUNT_TASKS":
                baseSql =
                        "SELECT COUNT(*) AS task_count FROM teaching_task t"
                                + " JOIN course c ON c.id=t.course_id"
                                + where;
                break;
            case "TASK_COUNT_BY_COURSE":
                baseSql =
                        "SELECT c.course_code,c.course_name,COUNT(t.id) AS task_count"
                                + " FROM course c LEFT JOIN teaching_task t ON t.course_id=c.id"
                                + where
                                + " GROUP BY c.id,c.course_code,c.course_name"
                                + " ORDER BY task_count DESC,c.course_code";
                break;
            case "PLANNED_HOURS_BY_COURSE":
                baseSql =
                        "SELECT c.course_code,c.course_name,"
                                + "COALESCE(SUM(t.planned_lab_hours),0) AS planned_lab_hours"
                                + " FROM course c LEFT JOIN teaching_task t ON t.course_id=c.id"
                                + where
                                + " GROUP BY c.id,c.course_code,c.course_name"
                                + " ORDER BY planned_lab_hours DESC,c.course_code";
                break;
            case "ENROLLMENT_BY_COURSE":
                baseSql =
                        "SELECT c.course_code,c.course_name,"
                                + "COALESCE(SUM(t.enrollment_count),0) AS enrollment_count"
                                + " FROM course c LEFT JOIN teaching_task t ON t.course_id=c.id"
                                + where
                                + " GROUP BY c.id,c.course_code,c.course_name"
                                + " ORDER BY enrollment_count DESC,c.course_code";
                break;
            default:
                throw new IllegalArgumentException("当前查询意图尚未实现");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("intent", intent);
        summary.put("courseCode", courseCode.isEmpty() ? null : courseCode);
        summary.put("courseName", courseName.isEmpty() ? null : courseName);
        summary.put("limit", limit);
        int resultLimit = aggregate ? 1 : limit;
        String displaySql = baseSql + " LIMIT " + resultLimit;
        String executionSql =
                aggregate
                        ? displaySql
                        : baseSql + " LIMIT " + Math.min(resultLimit + 1, MAX_LIMIT + 1);
        return new CompiledQuery(
                displaySql, executionSql, parameters, summary, resultLimit, false);
    }

/**
 * 检查模型输出中是否包含SQL字段
 * @param json ObjectMapper对象，用于处理JSON数据
 * @param modelOutput 模型输出的字符串
 * @return 如果包含SQL字段且为文本类型则返回true，否则返回false
 */
    boolean hasModelSql(ObjectMapper json, String modelOutput) {
        try {
        // 使用readPlan方法解析模型输出，获取JsonNode结果
            JsonNode result = readPlan(json, modelOutput);
        // 检查结果中是否包含"sql"字段，并且该字段是文本类型
            return result.has("sql") && result.get("sql").isTextual();
        } catch (IllegalArgumentException e) {
        // 如果发生IllegalArgumentException异常，返回false
            return false;
        }
    }
/**
 * 编译查询方法，将AI返回的模型输出转换为可执行的查询对象
 * @param json 对象映射器，用于处理JSON数据
 * @param modelOutput AI返回的模型输出字符串
 * @return 编译后的查询对象CompiledQuery
 * @throws IllegalArgumentException 当AI返回内容缺少SQL字段时抛出
 */
    CompiledQuery modelSql(ObjectMapper json, String modelOutput) {
    // 读取并解析AI的计划输出
        JsonNode result = readPlan(json, modelOutput);
    // 获取JSON中的SQL节点
        JsonNode sqlNode = result.get("sql");
    // 检查SQL节点是否存在且为文本类型
        if (sqlNode == null || !sqlNode.isTextual()) {
            throw new IllegalArgumentException("AI返回内容缺少SQL字段");
        }
    // 验证并清理SQL语句，确保安全性
        String safeSql = TeachingAiSqlGuard.validate(sqlNode.asText().trim());
    // 创建查询摘要信息，使用LinkedHashMap保持插入顺序
        Map<String, Object> summary = new LinkedHashMap<>();
    // 设置查询意图为MODEL_SQL
        summary.put("intent", "MODEL_SQL");
    // 设置查询结果的最大限制
        summary.put("limit", MAX_LIMIT);
    // 返回编译后的查询对象，包含安全SQL、原始SQL、空参数列表、摘要信息和最大限制
        return new CompiledQuery(
                safeSql, safeSql, Collections.emptyList(), summary, MAX_LIMIT, false);
    }

    CompiledQuery unsupported(String reason) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("intent", "UNSUPPORTED");
        plan.put("courseCode", null);
        plan.put("courseName", null);
        plan.put("limit", 1);
        plan.put("reason", reason);
        return new CompiledQuery("", "", Collections.emptyList(), plan, 1, true);
    }

    String sqlInstructions(String metadata) {
        return "你是数据库自然语言查询助手。请根据用户问题生成一条可执行的MySQL SELECT查询，"
                + "只返回JSON对象，不要使用Markdown，不要解释。JSON格式必须是"
                + "{\"sql\":\"SELECT ...\"}。只能生成一条SELECT，禁止INSERT、UPDATE、DELETE、DDL、"
                + "存储过程、变量、注释、UNION、子查询、CTE和多语句。请使用表中真实存在的字段，"
                + "只查询回答问题所需的最少列和行，结果最多200行。列别名使用英文蛇形命名，"
                + "不要使用中文标识符或反引号。不要查询任何密码、令牌或密钥字段。"
                + "如果问题涉及名称，优先使用LIKE进行模糊匹配；无法回答时返回{\"sql\":\"\"}。"
                + "\n数据库结构：\n"
                + metadata;
    }

/**
 * 根据课程代码和课程名称构建SQL查询的WHERE条件
 * @param courseCode 课程代码，不为空时添加到查询条件
 * @param courseName 课程名称，不为空时添加到查询条件
 * @param parameters 用于存储SQL查询参数的列表
 * @param taskQuery 是否为任务查询，影响课程名称的查询方式
 * @return 构建好的SQL WHERE条件字符串
 */
    private static String filters(
            String courseCode,              // 课程代码
            String courseName,              // 课程名称
            List<Object> parameters,        // SQL查询参数列表
            boolean taskQuery) {            // 是否为任务查询的标志
        StringBuilder where = new StringBuilder(" WHERE 1=1");  // 初始化WHERE子句基础条件
        if (!courseCode.isEmpty()) {    // 如果课程代码不为空
            where.append(" AND c.course_code=?");  // 添加课程代码条件
            parameters.add(courseCode);  // 将课程代码添加到参数列表
        }
        if (!courseName.isEmpty()) {    // 如果课程名称不为空
            if (taskQuery) {            // 如果是任务查询
                // 添加课程名称或课程名称快照的模糊查询条件
                where.append(" AND (c.course_name LIKE ? OR t.course_name_snapshot LIKE ?)");
                parameters.add("%" + courseName + "%");  // 添加课程名称参数
                parameters.add("%" + courseName + "%");  // 添加课程名称快照参数
            } else {                    // 如果不是任务查询
                // 添加课程名称模糊查询条件
                where.append(" AND c.course_name LIKE ?");
                parameters.add("%" + courseName + "%");  // 添加课程名称参数
            }
        }
        return where.toString();  // 返回构建好的WHERE条件字符串
    }

    private static JsonNode readPlan(ObjectMapper json, String modelOutput) {
        if (modelOutput == null || modelOutput.trim().isEmpty()) {
            throw new IllegalArgumentException("AI没有返回查询计划");
        }
        String text = modelOutput.trim();
        if (text.startsWith("```")) {
            text =
                    text.replaceFirst("^```(?:json|JSON)?\\s*", "")
                            .replaceFirst("\\s*```$", "")
                            .trim();
        }
        if (!text.startsWith("{") || !text.endsWith("}")) {
            throw new IllegalArgumentException("AI返回内容不是JSON查询计划");
        }
        try {
            JsonNode result = json.readTree(text);
            if (!result.isObject()) {
                throw new IllegalArgumentException("AI返回内容不是JSON对象");
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("AI返回的JSON查询计划无法解析", e);
        }
    }

/**
 * 验证JSON节点中的字段是否符合预期
 * @param plan 需要验证的JSON节点对象
 * @throws IllegalArgumentException 当字段不完整或包含未知字段时抛出
 */
    private static void validateFields(JsonNode plan) {
    // 创建一个Set来存储实际存在的字段名
        Set<String> actual = new HashSet<>();
    // 获取JSON节点的所有字段名迭代器
        Iterator<String> names = plan.fieldNames();
    // 遍历所有字段名并添加到actual集合中
        while (names.hasNext()) {
            actual.add(names.next());
        }
    // 比较实际字段与预期字段集合PLAN_FIELDS是否相等
        if (!actual.equals(PLAN_FIELDS)) {
        // 计算缺少的字段：存在于PLAN_FIELDS但不存在于actual中
            Set<String> missing = new HashSet<>(PLAN_FIELDS);
            missing.removeAll(actual);
        // 计算未知的字段：存在于actual中但不存在于PLAN_FIELDS中
            Set<String> unknown = new HashSet<>(actual);
            unknown.removeAll(PLAN_FIELDS);
        // 抛出异常，提示缺少和未知的字段信息
            throw new IllegalArgumentException(
                    "AI查询计划字段不完整或包含未知字段，缺少：" + missing + "，未知：" + unknown);
        }
    }

    private static String text(JsonNode plan, String field) {
        String value = optionalText(plan, field, 100);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("AI查询计划缺少字段：" + field);
        }
        return value;
    }

    private static String optionalText(JsonNode plan, String field, int maxLength) {
        JsonNode value = plan.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("AI查询计划字段格式不正确：" + field);
        }
        String text = value.asText().trim();
        if (text.length() > maxLength) {
            throw new IllegalArgumentException("AI查询计划字段过长：" + field);
        }
        return text;
    }

    private static int integer(
            JsonNode plan, String field, int defaultValue, int minimum, int maximum) {
        JsonNode value = plan.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException("AI查询计划字段必须是整数：" + field);
        }
        int number = value.asInt();
        if (number < minimum || number > maximum) {
            throw new IllegalArgumentException(
                    "AI查询计划字段超出范围：" + field + "应为" + minimum + "至" + maximum);
        }
        return number;
    }

    static final class CompiledQuery {
        private final String sql;
        private final String executionSql;
        private final List<Object> parameters;
        private final Map<String, Object> plan;
        private final int resultLimit;
        private final boolean unsupported;

        private CompiledQuery(
                String sql,
                String executionSql,
                List<Object> parameters,
                Map<String, Object> plan,
                int resultLimit,
                boolean unsupported) {
            this.sql = sql;
            this.executionSql = executionSql;
            this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
            this.plan = Collections.unmodifiableMap(new LinkedHashMap<>(plan));
            this.resultLimit = resultLimit;
            this.unsupported = unsupported;
        }

/**
 * 创建一个已编译的查询对象(CompiledQuery)
 *
 * @param sql 原始SQL查询语句
 * @param executionSql 实际执行的SQL语句，可能与原始SQL有所不同
 * @param parameters SQL查询参数列表
 * @param plan 查询执行计划，包含查询优化信息
 * @param resultLimit 查询结果限制数量
 * @return 返回一个已编译的查询对象实例
 */
        static CompiledQuery compiled(
                String sql,               // 原始SQL语句
                String executionSql,      // 实际执行的SQL语句
                List<Object> parameters,  // SQL查询参数列表
                Map<String, Object> plan, // 查询执行计划
                int resultLimit) {       // 结果集限制数量
            return new CompiledQuery(
                    sql, executionSql, parameters, plan, resultLimit, false);
        }

        String getSql() {
            return sql;
        }

        String getExecutionSql() {
            return executionSql;
        }

        List<Object> getParameters() {
            return parameters;
        }

        Map<String, Object> getPlan() {
            return plan;
        }

        int getResultLimit() {
            return resultLimit;
        }

        boolean isUnsupported() {
            return unsupported;
        }
    }
}
