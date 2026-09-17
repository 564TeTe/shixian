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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles a small AI-produced query plan into parameterized SQL.
 *
 * <p>The model never chooses table names, column names, joins, or SQL fragments. Expanding the demo
 * means adding another explicit intent here together with tests.
 */
final class TeachingAiQueryCompiler {

    static final int MAX_LIMIT = 200;

    private static final Pattern TEACHER_CLASS_QUESTION =
            Pattern.compile(
                    "^\\s*([\\p{IsHan}·]{2,10})\\s*(?:老师)?(?:教|带)(?:几个|多少个|多少|几)(?:个)?班\\s*[？?]?$");

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
            return unsupported(reason.isEmpty() ? "当前演示版无法回答这个问题" : reason);
        }
        if (!reason.isEmpty()) {
            throw new IllegalArgumentException("受支持的AI查询计划中reason必须为空");
        }

        String courseCode = optionalText(plan, "courseCode", 64);
        String courseName = optionalText(plan, "courseName", 200);
        int limit = integer(plan, "limit", 20, 1, MAX_LIMIT);
        List<Object> parameters = new ArrayList<>();
        boolean taskQuery =
                !"LIST_COURSES".equals(intent) && !"COUNT_COURSES".equals(intent);
        String where = filters(courseCode, courseName, parameters, taskQuery);
        String baseSql;
        boolean aggregate =
                "COUNT_COURSES".equals(intent) || "COUNT_TASKS".equals(intent);

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
                        "SELECT COUNT(*) AS task_count FROM teaching_task t JOIN course c ON"
                                + " c.id=t.course_id"
                                + where;
                break;
            case "TASK_COUNT_BY_COURSE":
                baseSql =
                        "SELECT c.course_code,c.course_name,COUNT(t.id) AS task_count FROM course c"
                            + " LEFT JOIN teaching_task t ON t.course_id=c.id"
                            + where
                            + " GROUP BY c.id,c.course_code,c.course_name ORDER BY task_count DESC,"
                            + "c.course_code";
                break;
            case "PLANNED_HOURS_BY_COURSE":
                baseSql =
                        "SELECT c.course_code,c.course_name,"
                            + "COALESCE(SUM(t.planned_lab_hours),0) AS planned_lab_hours FROM course"
                            + " c LEFT JOIN teaching_task t ON t.course_id=c.id"
                            + where
                            + " GROUP BY c.id,c.course_code,c.course_name ORDER BY planned_lab_hours"
                            + " DESC,c.course_code";
                break;
            case "ENROLLMENT_BY_COURSE":
                baseSql =
                        "SELECT c.course_code,c.course_name,"
                            + "COALESCE(SUM(t.enrollment_count),0) AS enrollment_count FROM course c"
                            + " LEFT JOIN teaching_task t ON t.course_id=c.id"
                            + where
                            + " GROUP BY c.id,c.course_code,c.course_name ORDER BY enrollment_count"
                            + " DESC,c.course_code";
                break;
            default:
                throw new IllegalArgumentException("当前演示版尚未实现该查询意图");
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

    CompiledQuery fallback(String question) {
        String text = question == null ? "" : question.trim();
        Matcher teacherClassQuestion = TEACHER_CLASS_QUESTION.matcher(text);
        if (teacherClassQuestion.matches()) {
            return teacherClassCount(teacherClassQuestion.group(1).trim());
        }
        if (text.matches(".*(教师|老师).*")) {
            return null;
        }
        if (text.matches(".*(一共有多少门课程|共有多少门课程|课程总数|课程数量|多少门课).*")) {
            return fixedPlan("COUNT_COURSES", null, null, 20);
        }
        if (text.matches(".*(计划实验学时最多|实验学时最多的).*")) {
            return fixedPlan("PLANNED_HOURS_BY_COURSE", null, null, numberInQuestion(text, 10));
        }
        if (text.matches(".*(选课人数最多|选课人数统计|按课程统计选课人数).*")) {
            return fixedPlan("ENROLLMENT_BY_COURSE", null, null, numberInQuestion(text, 20));
        }
        if (text.matches(".*(每门课程.*教学任务|按课程统计.*教学任务|课程.*任务数量).*")) {
            return fixedPlan("TASK_COUNT_BY_COURSE", null, null, numberInQuestion(text, 20));
        }
        if (text.matches(".*(列出|查看|查询|有哪些).*(教学任务|开课任务).*")) {
            String courseName = extractCourseName(text);
            return fixedPlan("LIST_TASKS", null, courseName, numberInQuestion(text, 20));
        }
        if (text.matches(".*(列出|查看|查询|有哪些).*(课程|课程信息).*")) {
            return fixedPlan("LIST_COURSES", null, null, numberInQuestion(text, 20));
        }
        if (text.matches(".*(多少个|多少条|数量).*(教学任务|开课任务).*")) {
            return fixedPlan("COUNT_TASKS", null, null, 20);
        }
        return null;
    }

    boolean hasModelSql(ObjectMapper json, String modelOutput) {
        try {
            JsonNode result = readPlan(json, modelOutput);
            return result.has("sql") && result.get("sql").isTextual();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    CompiledQuery modelSql(ObjectMapper json, String modelOutput) {
        JsonNode result = readPlan(json, modelOutput);
        JsonNode sqlNode = result.get("sql");
        if (sqlNode == null || !sqlNode.isTextual()) {
            throw new IllegalArgumentException("AI返回内容缺少SQL字段");
        }
        String safeSql = TeachingAiSqlGuard.validate(sqlNode.asText().trim());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("intent", "MODEL_SQL");
        summary.put("limit", MAX_LIMIT);
        return new CompiledQuery(
                safeSql, safeSql, Collections.emptyList(), summary, MAX_LIMIT, false);
    }

    private CompiledQuery teacherClassCount(String teacherName) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("intent", "COUNT_CLASSES_BY_TEACHER");
        plan.put("courseCode", null);
        plan.put("courseName", null);
        plan.put("limit", 1);
        String sql =
                "SELECT ? AS teacher_name,COUNT(DISTINCT task_id) AS class_count"
                        + " FROM ai_teacher_workload WHERE teacher_name=? LIMIT 1";
        return new CompiledQuery(
                sql,
                sql,
                Arrays.asList(teacherName, teacherName),
                plan,
                1,
                false);
    }

    private CompiledQuery fixedPlan(String intent, String courseCode, String courseName, int limit) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("intent", intent);
        plan.put("courseCode", courseCode);
        plan.put("courseName", courseName);
        plan.put("limit", limit);
        plan.put("reason", "");
        String planJson;
        try {
            planJson =
                    new ObjectMapper()
                            .writeValueAsString(plan);
        } catch (IOException e) {
            throw new IllegalArgumentException("无法创建本地查询计划", e);
        }
        return compile(new ObjectMapper(), planJson);
    }

    private static int numberInQuestion(String question, int defaultValue) {
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{1,3})(?!\\d)").matcher(question);
        if (!matcher.find()) {
            return defaultValue;
        }
        int value = Integer.parseInt(matcher.group(1));
        return Math.max(1, Math.min(value, MAX_LIMIT));
    }

    private static String extractCourseName(String question) {
        Matcher matcher =
                Pattern.compile("(?:列出|查看|查询|有哪些)\\s*(.*?)的?(?:教学任务|开课任务)")
                        .matcher(question);
        if (!matcher.find()) {
            return null;
        }
        String name = matcher.group(1).trim();
        return name.isEmpty() ? null : name;
    }

    CompiledQuery unsupported(String reason) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("intent", "UNSUPPORTED");
        plan.put("courseCode", null);
        plan.put("courseName", null);
        plan.put("limit", 1);
        plan.put("reason", reason);
        return new CompiledQuery(
                "", "", Collections.emptyList(), plan, 1, true);
    }

    String instructions(String metadata) {
        return "你是实验教学查询规划器。把用户问题转换成JSON查询计划，不要直接输出SQL，不要解释，"
                + "不要使用Markdown。当前演示版只查询course和teaching_task两张表。"
                + "必须严格输出以下JSON字段："
                + "{\"intent\":\"意图\",\"courseCode\":null,\"courseName\":null,"
                + "\"limit\":20,\"reason\":\"\"}。"
                + "intent只能是："
                + String.join(",", INTENTS)
                + "。courseCode只在用户明确给出课程号时填写；courseName用于课程名称关键词；"
                + "limit默认20，范围1到200。无法由这两张表回答时使用UNSUPPORTED并在reason中说明。"
                + "\n意图说明：LIST_COURSES列课程；COUNT_COURSES统计课程数；LIST_TASKS列教学任务；"
                + "COUNT_TASKS统计任务数；TASK_COUNT_BY_COURSE按课程统计任务数；"
                + "PLANNED_HOURS_BY_COURSE按课程汇总计划实验学时；"
                + "ENROLLMENT_BY_COURSE按课程汇总选课人数。"
                + "\n示例："
                + "\n问题：一共有多少门课程"
                + "\n输出：{\"intent\":\"COUNT_COURSES\",\"courseCode\":null,"
                + "\"courseName\":null,\"limit\":20,\"reason\":\"\"}"
                + "\n问题：列出软件工程的教学任务"
                + "\n输出：{\"intent\":\"LIST_TASKS\",\"courseCode\":null,"
                + "\"courseName\":\"软件工程\",\"limit\":20,\"reason\":\"\"}"
                + "\n问题：计划实验学时最多的10门课程"
                + "\n输出：{\"intent\":\"PLANNED_HOURS_BY_COURSE\",\"courseCode\":null,"
                + "\"courseName\":null,\"limit\":10,\"reason\":\"\"}"
                + "\n问题：统计每间实验室的排课学时"
                + "\n输出：{\"intent\":\"UNSUPPORTED\",\"courseCode\":null,"
                + "\"courseName\":null,\"limit\":20,\"reason\":\"当前演示版尚未开放实验室和排课表\"}"
                + "\n数据库元数据：\n"
                + metadata;
    }

    String sqlInstructions(String metadata) {
        return "你是实验教学数据库查询助手。请根据用户问题生成一条可执行的MySQL SELECT查询，"
                + "只返回JSON对象，不要使用Markdown，不要解释。JSON格式必须是"
                + "{\"sql\":\"SELECT ...\"}。只能生成一条SELECT，禁止INSERT、UPDATE、DELETE、DDL、"
                + "存储过程、变量、注释、UNION、子查询、CTE和多语句。请使用表中真实存在的字段，"
                + "只查询回答问题所需的最少列和行，结果最多200行。列别名使用英文蛇形命名，"
                + "不要使用中文标识符或反引号。不要查询任何密码、令牌或密钥字段。"
                + "如果问题涉及名称，优先使用LIKE进行模糊匹配。"
                + "\n数据库结构：\n"
                + metadata;
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

    private static String filters(
            String courseCode,
            String courseName,
            List<Object> parameters,
            boolean taskQuery) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (!courseCode.isEmpty()) {
            where.append(" AND c.course_code=?");
            parameters.add(courseCode);
        }
        if (!courseName.isEmpty()) {
            if (taskQuery) {
                where.append(" AND (c.course_name LIKE ? OR t.course_name_snapshot LIKE ?)");
                parameters.add("%" + courseName + "%");
                parameters.add("%" + courseName + "%");
            } else {
                where.append(" AND c.course_name LIKE ?");
                parameters.add("%" + courseName + "%");
            }
        }
        return where.toString();
    }

    private static void validateFields(JsonNode plan) {
        Set<String> actual = new HashSet<>();
        Iterator<String> names = plan.fieldNames();
        while (names.hasNext()) {
            actual.add(names.next());
        }
        if (!actual.equals(PLAN_FIELDS)) {
            Set<String> missing = new HashSet<>(PLAN_FIELDS);
            missing.removeAll(actual);
            Set<String> unknown = new HashSet<>(actual);
            unknown.removeAll(PLAN_FIELDS);
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
