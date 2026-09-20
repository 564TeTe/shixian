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

    static final int MAX_LIMIT = 200; // 查询结果的最大限制值

    /**
     * 用于匹配教师班级问题的正则表达式模式
     * 匹配格式：教师名 + "老师" + "教" + "几个/多少个" + "班"
     * 例如："张老师教几个班"、"李老师带多少班"
     */
    private static final Pattern TEACHER_CLASS_QUESTION =
            Pattern.compile(
                    "^\\s*([\\p{IsHan}·]{2,10})\\s*(?:老师)?(?:教|带)(?:几个|多少个|多少|几)(?:个)?班\\s*[？?]?$");

    /**
     * 查询计划中必须包含的字段集合
     * 包括：意图、课程代码、课程名称、限制数量、原因
     */
    private static final Set<String> PLAN_FIELDS =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "intent",
                                    "courseCode",
                                    "courseName",
                                    "limit",
                                    "reason")));

    /**
     * 支持的查询意图列表
     * 包括：列出课程、统计课程、列出任务、统计任务、按课程统计任务数、
     * 按课程统计计划学时、按课程统计选课人数、不支持
     */
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

    /**
     * 编译AI查询计划为参数化SQL
     * @param json JSON解析器
     * @param modelOutput AI返回的查询计划
     * @return 编译后的查询对象
     * @throws IllegalArgumentException 当查询计划无效或包含不支持的意图时抛出
     */
    CompiledQuery compile(ObjectMapper json, String modelOutput) {
        // 解析并验证AI返回的查询计划
        JsonNode plan = readPlan(json, modelOutput);
        validateFields(plan);
        // 获取并验证查询意图
        String intent = text(plan, "intent").toUpperCase(Locale.ROOT);
        String reason = optionalText(plan, "reason", 300);
        // 检查意图是否支持
        if (!INTENTS.contains(intent)) {
            throw new IllegalArgumentException("AI返回了不支持的查询意图：" + intent);
        }
        // 处理不支持的情况
        if ("UNSUPPORTED".equals(intent)) {
            return unsupported(reason.isEmpty() ? "当前演示版无法回答这个问题" : reason);
        }
        // 验证受支持的查询中reason必须为空
        if (!reason.isEmpty()) {
            throw new IllegalArgumentException("受支持的AI查询计划中reason必须为空");
        }

        // 获取查询参数
        String courseCode = optionalText(plan, "courseCode", 64);
        String courseName = optionalText(plan, "courseName", 200);
        int limit = integer(plan, "limit", 20, 1, MAX_LIMIT);
        List<Object> parameters = new ArrayList<>();
        // 判断是否为任务查询
        boolean taskQuery =
                !"LIST_COURSES".equals(intent) && !"COUNT_COURSES".equals(intent);
        // 构建WHERE条件
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

/**
 * 根据输入的问题返回对应的预编译查询
 * 该方法通过分析问题文本，匹配不同的模式，返回相应的查询计划
 * @param question 用户输入的问题字符串
 * @return 返回一个CompiledQuery对象，表示匹配到的查询计划，如果没有匹配则返回null
 */
    CompiledQuery fallback(String question) {
        // 处理输入问题，去除可能的null值和前后空格
        String text = question == null ? "" : question.trim();
        // 匹配教师班级相关的提问模式
        Matcher teacherClassQuestion = TEACHER_CLASS_QUESTION.matcher(text);
        // 如果匹配到教师班级相关问题，则返回教师班级统计查询
        if (teacherClassQuestion.matches()) {
            return teacherClassCount(teacherClassQuestion.group(1).trim());
        }
        // 如果问题中包含"教师"或"老师"关键词，返回null
        if (text.matches(".*(教师|老师).*")) {
            return null;
        }
        // 匹配询问课程总数的多种表达方式
        if (text.matches(".*(一共有多少门课程|共有多少门课程|课程总数|课程数量|多少门课).*")) {
            return fixedPlan("COUNT_COURSES", null, null, 20);
        }
        // 匹配询问计划实验学时最多的问题
        if (text.matches(".*(计划实验学时最多|实验学时最多的).*")) {
            return fixedPlan("PLANNED_HOURS_BY_COURSE", null, null, numberInQuestion(text, 10));
        }
        // 匹配询问选课人数最多或选课人数统计的问题
        if (text.matches(".*(选课人数最多|选课人数统计|按课程统计选课人数).*")) {
            return fixedPlan("ENROLLMENT_BY_COURSE", null, null, numberInQuestion(text, 20));
        }
        // 匹配询问每门课程教学任务数量的问题
        if (text.matches(".*(每门课程.*教学任务|按课程统计.*教学任务|课程.*任务数量).*")) {
            return fixedPlan("TASK_COUNT_BY_COURSE", null, null, numberInQuestion(text, 20));
        }
        // 匹配列出教学任务的问题
        if (text.matches(".*(列出|查看|查询|有哪些).*(教学任务|开课任务).*")) {
            // 从问题中提取课程名称
            String courseName = extractCourseName(text);
            return fixedPlan("LIST_TASKS", null, courseName, numberInQuestion(text, 20));
        }
        // 匹配列出课程信息的问题
        if (text.matches(".*(列出|查看|查询|有哪些).*(课程|课程信息).*")) {
            return fixedPlan("LIST_COURSES", null, null, numberInQuestion(text, 20));
        }
        // 匹配询问教学任务数量的问题
        if (text.matches(".*(多少个|多少条|数量).*(教学任务|开课任务).*")) {
            return fixedPlan("COUNT_TASKS", null, null, 20);
        }
        // 如果没有匹配到任何模式，返回null
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

/**
 * 创建一个固定计划的查询方法
 * @param intent 查询意图
 * @param courseCode 课程代码
 * @param courseName 课程名称
 * @param limit 限制数量
 * @return 返回一个编译后的查询对象(CompiledQuery)
 */
    private CompiledQuery fixedPlan(String intent, String courseCode, String courseName, int limit) {
    // 使用LinkedHashMap保持插入顺序
        Map<String, Object> plan = new LinkedHashMap<>();
    // 向计划中添加各个参数
        plan.put("intent", intent);
        plan.put("courseCode", courseCode);
        plan.put("courseName", courseName);
        plan.put("limit", limit);
        plan.put("reason", "");
        String planJson;
        try {
        // 将计划对象转换为JSON字符串
            planJson =
                    new ObjectMapper()
                            .writeValueAsString(plan);
        } catch (IOException e) {
        // 转换失败时抛出非法参数异常
            throw new IllegalArgumentException("无法创建本地查询计划", e);
        }
    // 编译JSON查询计划并返回
        return compile(new ObjectMapper(), planJson);
    }

/**
 * 从问题字符串中提取数字并返回限定范围内的值
 * @param question 包含数字的问题字符串
 * @param defaultValue 当未找到数字时的默认返回值
 * @return 提取的数字值，确保在1到MAX_LIMIT之间，若未找到数字则返回defaultValue
 */
    private static int numberInQuestion(String question, int defaultValue) {
        // 使用正则表达式匹配1到3位数字，且该数字前后不能有其他数字
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{1,3})(?!\\d)").matcher(question);
        // 如果未找到匹配的数字，返回默认值
        if (!matcher.find()) {
            return defaultValue;
        }
        // 将匹配到的数字字符串转换为整数
        int value = Integer.parseInt(matcher.group(1));
        // 确保返回的值在1到MAX_LIMIT之间，如果超出范围则取边界值
        return Math.max(1, Math.min(value, MAX_LIMIT));
    }

/**
 * 从问题中提取课程名称
 * 该方法使用正则表达式匹配问题中的课程名称部分
 * @param question 用户输入的问题字符串
 * @return 提取到的课程名称，如果没有找到则返回null
 */
    private static String extractCourseName(String question) {
    // 使用正则表达式模式匹配问题中的课程名称
    // 匹配"列出/查看/查询/有哪些"开头，后面跟着任意空白字符，然后捕获课程名称
    // 最后可以跟"的"和"教学任务/开课任务"
        Matcher matcher =
                Pattern.compile("(?:列出|查看|查询|有哪些)\\s*(.*?)的?(?:教学任务|开课任务)")
                        .matcher(question);
    // 如果没有找到匹配项，直接返回null
        if (!matcher.find()) {
            return null;
        }
    // 获取匹配到的第一个捕获组（即课程名称），并去除前后空格
        String name = matcher.group(1).trim();
    // 如果提取到的名称为空字符串，则返回null，否则返回处理后的名称
        return name.isEmpty() ? null : name;
    }

/**
 * 创建一个表示不支持的查询的CompiledQuery对象
 * @param reason 不支持操作的具体原因
 * @return 返回一个包含不支持信息的CompiledQuery对象
 */
    CompiledQuery unsupported(String reason) {
    // 创建一个有序的Map来存储查询计划
        Map<String, Object> plan = new LinkedHashMap<>();
    // 设置查询意图为"UNSUPPORTED"
        plan.put("intent", "UNSUPPORTED");
    // 设置课程代码为null
        plan.put("courseCode", null);
    // 设置课程名称为null
        plan.put("courseName", null);
    // 设置查询限制为1
        plan.put("limit", 1);
    // 设置不支持的原因
        plan.put("reason", reason);
    // 返回一个新的CompiledQuery对象，包含空字符串的查询和计划信息
        return new CompiledQuery(
                "", "", Collections.emptyList(), plan, 1, true);
    }

/**
 * 生成实验教学查询规划器的指令文本
 * 该函数返回一段详细的指令，用于指导用户如何将问题转换为JSON格式的查询计划
 *
 * @param metadata 数据库元数据信息，用于提供数据库结构相关的上下文
 * @return 返回包含完整指令的字符串，指导用户如何构建JSON查询
 */
    String instructions(String metadata) {
    // 返回指令字符串，包含以下关键信息：
    // 1. 系统角色定义：实验教学查询规划器
    // 2. 输出要求：JSON格式，不直接输出SQL，不解释，不使用Markdown
    // 3. 当前限制：仅支持查询course和teaching_task两张表
    // 4. JSON格式规范：必须包含指定字段及其默认值
    // 5. 可用意图列表：枚举所有支持的查询意图
    // 6. 字段填写规则：courseCode和courseName的填写条件
    // 7. limit参数规范：默认值20，范围1-200
    // 8. 不支持情况处理：使用UNSUPPORTED意图并说明原因
    // 9. 意图详细说明：解释每种意图的具体功能
    // 10. 示例展示：提供多个输入输出的示例
    // 11. 数据库元数据：提供当前数据库的结构信息
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

/**
 * 读取并解析AI返回的JSON查询计划
 * @param json ObjectMapper实例，用于JSON解析
 * @param modelOutput AI返回的原始字符串
 * @return 解析后的JsonNode对象
 * @throws IllegalArgumentException 当输入为空、格式不正确或无法解析时抛出
 */
    private static JsonNode readPlan(ObjectMapper json, String modelOutput) {
    // 检查AI是否返回了查询计划
        if (modelOutput == null || modelOutput.trim().isEmpty()) {
            throw new IllegalArgumentException("AI没有返回查询计划");
        }
    // 去除字符串两端的空白字符
        String text = modelOutput.trim();
    // 如果返回内容被包含在代码块标记中（```），则移除这些标记
        if (text.startsWith("```")) {
            text =
                    text.replaceFirst("^```(?:json|JSON)?\\s*", "") // 移除开始的代码块标记
                            .replaceFirst("\\s*```$", "") // 移除结束的代码块标记
                            .trim(); // 再次去除可能的空白字符
        }
    // 验证返回内容是否为有效的JSON格式（以{开头，以}结尾）
        if (!text.startsWith("{") || !text.endsWith("}")) {
            throw new IllegalArgumentException("AI返回内容不是JSON查询计划");
        }
        try {
        // 使用ObjectMapper解析JSON字符串
            JsonNode result = json.readTree(text);
        // 验证解析结果是否为JSON对象
            if (!result.isObject()) {
                throw new IllegalArgumentException("AI返回内容不是JSON对象");
            }
            return result; // 返回解析后的JSON对象
        } catch (IOException e) {
        // 如果JSON解析失败，抛出包含原始异常信息的异常
            throw new IllegalArgumentException("AI返回的JSON查询计划无法解析", e);
        }
    }

/**
 * 根据课程代码和课程名称构建SQL查询的WHERE条件语句
 * @param courseCode 课程代码，如果为空字符串则忽略该条件
 * @param courseName 课程名称，如果为空字符串则忽略该条件
 * @param parameters 用于存储SQL查询参数的列表
 * @param taskQuery 是否为任务查询，影响课程名称条件的构建方式
 * @return 返回构建好的WHERE条件语句字符串
 */
    private static String filters(
            String courseCode,               // 课程代码参数
            String courseName,              // 课程名称参数
            List<Object> parameters,        // SQL查询参数列表
            boolean taskQuery) {            // 是否为任务查询的标志
        // 初始化WHERE条件，基础条件为"1=1"以确保AND连接有效
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        // 如果课程代码不为空，添加课程代码条件
        if (!courseCode.isEmpty()) {
            where.append(" AND c.course_code=?");
            parameters.add(courseCode);
        }
        // 如果课程名称不为空，添加课程名称条件
        if (!courseName.isEmpty()) {
            // 根据是否为任务查询，决定课程名称条件的构建方式
            if (taskQuery) {
                // 任务查询时，同时匹配当前课程名称和历史快照
                where.append(" AND (c.course_name LIKE ? OR t.course_name_snapshot LIKE ?)");
                parameters.add("%" + courseName + "%");
                parameters.add("%" + courseName + "%");
            } else {
                // 普通查询时，只匹配当前课程名称
                where.append(" AND c.course_name LIKE ?");
                parameters.add("%" + courseName + "%");
            }
        }
        // 返回构建完成的WHERE条件语句
        return where.toString();
    }

/**
 * 验证JSON节点中的字段是否符合预期
 * @param plan 需要验证的JSON节点对象
 * @throws IllegalArgumentException 当字段不完整或包含未知字段时抛出异常
 */
    private static void validateFields(JsonNode plan) {
    // 创建一个HashSet来存储实际存在的字段名
        Set<String> actual = new HashSet<>();
    // 获取JSON节点中所有字段的迭代器
        Iterator<String> names = plan.fieldNames();
    // 遍历所有字段名并添加到actual集合中
        while (names.hasNext()) {
            actual.add(names.next());
        }
    // 比较实际字段与预期字段集合是否相等
        if (!actual.equals(PLAN_FIELDS)) {
        // 创建一个集合来存储缺失的字段
            Set<String> missing = new HashSet<>(PLAN_FIELDS);
            missing.removeAll(actual);
        // 创建一个集合来存储未知的字段
            Set<String> unknown = new HashSet<>(actual);
            unknown.removeAll(PLAN_FIELDS);
        // 抛出异常，提示缺少哪些字段和包含哪些未知字段
            throw new IllegalArgumentException(
                    "AI查询计划字段不完整或包含未知字段，缺少：" + missing + "，未知：" + unknown);
        }
    }

/**
 * 从JsonNode中获取指定字段的文本值，如果值为空则抛出异常
 * @param plan JsonNode对象，包含AI查询计划的数据
 * @param field 需要获取的字段名
 * @return 返回指定字段的文本值
 * @throws IllegalArgumentException 当获取到的值为空时抛出异常
 */
    private static String text(JsonNode plan, String field) {
    // 调用optionalText方法获取字段值，限制最大长度为100
        String value = optionalText(plan, field, 100);
    // 检查获取的值是否为空
        if (value.isEmpty()) {
        // 如果值为空，抛出异常提示缺少指定字段
            throw new IllegalArgumentException("AI查询计划缺少字段：" + field);
        }
    // 返回获取到的有效值
        return value;
    }

/**
 * 从JsonNode中获取指定字段的文本值，并进行必要的验证和截断处理
 * @param plan 包含字段的JsonNode对象
 * @param field 要获取的字段名
 * @param maxLength 文本最大允许长度
 * @return 返回处理后的文本字符串，如果字段不存在或为null则返回空字符串
 * @throws IllegalArgumentException 如果字段存在但不是文本类型，或文本长度超过最大限制
 */
    private static String optionalText(JsonNode plan, String field, int maxLength) {
    // 获取指定字段的JsonNode
        JsonNode value = plan.get(field);
    // 检查字段是否存在或为null
        if (value == null || value.isNull()) {
            return "";
        }
    // 检查字段是否为文本类型
        if (!value.isTextual()) {
            throw new IllegalArgumentException("AI查询计划字段格式不正确：" + field);
        }
    // 将文本值转换为String并去除首尾空格
        String text = value.asText().trim();
    // 检查文本长度是否超过最大限制
        if (text.length() > maxLength) {
            throw new IllegalArgumentException("AI查询计划字段过长：" + field);
        }
        return text;
    }

/**
 * 从JSON节点中获取指定字段的整数值，并进行有效性验证
 * @param plan JSON节点对象，包含要查询的数据
 * @param field 要获取的字段名称
 * @param defaultValue 当字段不存在或为null时的默认返回值
 * @param minimum 允许的最小值
 * @param maximum 允许的最大值
 * @return 字段的整数值，如果验证通过则返回该值，否则返回默认值或抛出异常
 * @throws IllegalArgumentException 当字段值不是整数或超出允许范围时抛出
 */
    private static int integer(
            JsonNode plan, String field, int defaultValue, int minimum, int maximum) {
        // 获取指定字段的JSON节点
        JsonNode value = plan.get(field);
        // 检查字段是否存在或为null
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        // 检查字段值是否为整数类型
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException("AI查询计划字段必须是整数：" + field);
        }
        // 将JSON节点转换为整数值
        int number = value.asInt();
        // 检查数值是否在允许的范围内
        if (number < minimum || number > maximum) {
            throw new IllegalArgumentException(
                    "AI查询计划字段超出范围：" + field + "应为" + minimum + "至" + maximum);
        }
        return number;
    }

/**
 * 编译查询的静态内部类，用于存储编译后的SQL查询相关信息
 */
    static final class CompiledQuery {
        private final String sql;        // 原始SQL语句
        private final String executionSql; // 执行SQL语句
        private final List<Object> parameters; // 查询参数列表
        private final Map<String, Object> plan; // 查询计划
        private final int resultLimit;   // 结果限制数量
        private final boolean unsupported; // 是否不支持该查询

        /**
         * 构造方法，初始化编译查询的各个属性
         * @param sql 原始SQL语句
         * @param executionSql 执行SQL语句
         * @param parameters 查询参数列表
         * @param plan 查询计划
         * @param resultLimit 结果限制数量
         * @param unsupported 是否不支持该查询
         */
        private CompiledQuery(
                String sql,
                String executionSql,
                List<Object> parameters,
                Map<String, Object> plan,
                int resultLimit,
                boolean unsupported) {
            this.sql = sql;
            this.executionSql = executionSql;
            // 创建参数列表的不可修改副本
            this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
            // 创建查询计划的不可修改副本
            this.plan = Collections.unmodifiableMap(new LinkedHashMap<>(plan));
            this.resultLimit = resultLimit;
            this.unsupported = unsupported;
        }

        /**
         * 获取原始SQL语句
         * @return 原始SQL语句
         */
        String getSql() {
            return sql;
        }

        /**
         * 获取执行SQL语句
         * @return 执行SQL语句
         */
        String getExecutionSql() {
            return executionSql;
        }

        /**
         * 获取查询参数列表
         * @return 查询参数列表
         */
        List<Object> getParameters() {
            return parameters;
        }

        /**
         * 获取查询计划
         * @return 查询计划
         */
        Map<String, Object> getPlan() {
            return plan;
        }

        /**
         * 获取结果限制数量
         * @return 结果限制数量
         */
        int getResultLimit() {
            return resultLimit;
        }

        /**
         * 获取是否不支持该查询
         * @return 如果不支持则返回true，否则返回false
         */
        boolean isUnsupported() {
            return unsupported;
        }
    }
}
