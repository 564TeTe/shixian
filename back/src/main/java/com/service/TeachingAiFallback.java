package com.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI查询的确定性保底层。
 *
 * <p>高频且含义明确的问题优先在这里生成参数化SQL；未命中的问题再交给AI。
 * 更换数据库后，可以保留通用流程，只维护本文件中的表依赖、正则和固定SQL。
 */
final class TeachingAiFallback {

    static final int MAX_LIMIT = 200;

    private static final Pattern TEACHER_CLASS_QUESTION =
            Pattern.compile(
                    "^\\s*([\\p{IsHan}·]{2,10})\\s*(?:老师)?(?:教|带)(?:几个|多少个|多少|几)(?:个)?班\\s*[？?]?$");


    /**
     * 第一步：尝试用确定性规则回答。
     *
     * <p>每条规则都会先检查依赖表是否在白名单中，避免换库后误用旧业务SQL。
     */
    static TeachingAiQueryCompiler.CompiledQuery compile(
            String question, Set<String> allowedTables) {
        String text = question == null ? "" : question.trim();

        Matcher teacherClassQuestion = TEACHER_CLASS_QUESTION.matcher(text);
        if (teacherClassQuestion.matches()
                && hasTables(allowedTables, "ai_teacher_workload")) {
            return teacherClassCount(teacherClassQuestion.group(1).trim());
        }
        if (text.matches(".*(教师|老师).*")) {
            return null;
        }
        if (!hasTables(allowedTables, "course", "teaching_task")) {
            return null;
        }
        if (text.matches(".*(一共有多少门课程|共有多少门课程|课程总数|课程数量|多少门课).*")) {
            return fixedPlan("COUNT_COURSES", null, null, 20);
        }
        if (text.matches(".*(计划实验学时最多|实验学时最多的).*")) {
            return fixedPlan(
                    "PLANNED_HOURS_BY_COURSE", null, null, numberInQuestion(text, 10));
        }
        if (text.matches(".*(选课人数最多|选课人数统计|按课程统计选课人数).*")) {
            return fixedPlan("ENROLLMENT_BY_COURSE", null, null, numberInQuestion(text, 20));
        }
        if (text.matches(".*(每门课程.*教学任务|按课程统计.*教学任务|课程.*任务数量).*")) {
            return fixedPlan("TASK_COUNT_BY_COURSE", null, null, numberInQuestion(text, 20));
        }
        if (text.matches(".*(列出|查看|查询|有哪些).*(教学任务|开课任务).*")) {
            return fixedPlan(
                    "LIST_TASKS", null, extractCourseName(text), numberInQuestion(text, 20));
        }
        if (text.matches(".*(列出|查看|查询|有哪些).*(课程|课程信息).*")) {
            return fixedPlan("LIST_COURSES", null, null, numberInQuestion(text, 20));
        }
        if (text.matches(".*(多少个|多少条|数量).*(教学任务|开课任务).*")) {
            return fixedPlan("COUNT_TASKS", null, null, 20);
        }
        return null;
    }

/**
 * 检查允许的表格集合中是否包含所有必需的表格
 * @param allowedTables 允许的表格集合，表示用户可以访问的表格列表
 * @param required 需要检查的表格名称，可变参数，可以传入一个或多个表格名称
 * @return 如果允许的表格集合中包含所有必需的表格，则返回true；否则返回false
 */
    private static boolean hasTables(Set<String> allowedTables, String... required) {
    // 使用containsAll方法检查allowedTables是否包含所有required中的表格
    // Arrays.asList(required)将可变参数转换为List集合
        return allowedTables.containsAll(Arrays.asList(required));
    }

/**
 * 编译一个查询教师课程数量的SQL查询
 * @param teacherName 教师姓名
 * @return 返回一个编译好的查询对象，包含SQL语句和参数
 */
    private static TeachingAiQueryCompiler.CompiledQuery teacherClassCount(String teacherName) {
    // 创建一个执行计划，使用COUNT_CLASSES_BY_TEACHER标识符，无额外参数，结果集大小为1
        Map<String, Object> plan = plan("COUNT_CLASSES_BY_TEACHER", null, null, 1);
    // 定义SQL查询语句，统计指定教师的课程数量
        String sql =
                "SELECT ? AS teacher_name,COUNT(DISTINCT task_id) AS class_count"
                        + " FROM ai_teacher_workload WHERE teacher_name=? LIMIT 1";
    // 返回编译好的查询对象，包含SQL语句、参数列表、执行计划和结果集大小
        return TeachingAiQueryCompiler.CompiledQuery.compiled(
                sql, sql, Arrays.asList(teacherName, teacherName), plan, 1);
    }

/**
 * 根据给定的意图、课程代码、课程名称和限制数量，生成一个固定的查询计划
 *
 * @param intent 查询意图，如"LIST_COURSES"、"COUNT_COURSES"等
 * @param courseCode 课程代码，用于过滤结果
 * @param courseName 课程名称，用于过滤结果
 * @param limit 返回结果的最大数量
 * @return 返回一个编译后的查询对象，包含显示SQL、执行SQL、参数列表等信息
 */
    private static TeachingAiQueryCompiler.CompiledQuery fixedPlan(
            String intent, String courseCode, String courseName, int limit) {
    // 创建参数列表，用于SQL查询中的参数化查询
        List<Object> parameters = new ArrayList<>();
    // 判断是否为任务查询，排除"LIST_COURSES"和"COUNT_COURSES"两种情况
        boolean taskQuery = !"LIST_COURSES".equals(intent) && !"COUNT_COURSES".equals(intent);
    // 根据过滤条件生成WHERE子句
        String where = filters(courseCode, courseName, parameters, taskQuery);
        String baseSql;
    // 判断是否只需要返回单行结果
        boolean singleRow = "COUNT_COURSES".equals(intent) || "COUNT_TASKS".equals(intent);

    // 根据不同的查询意图构建基础SQL语句
        switch (intent) {
            case "LIST_COURSES":
            // 列出课程的基础SQL查询
                baseSql =
                        "SELECT c.course_code,c.course_name FROM course c"
                                + where
                                + " ORDER BY c.course_code";
                break;
            case "COUNT_COURSES":
            // 统计课程数量的基础SQL查询
                baseSql = "SELECT COUNT(*) AS course_count FROM course c" + where;
                break;
            case "LIST_TASKS":
            // 列出教学任务的基础SQL查询
                baseSql =
                        "SELECT t.task_code,c.course_code,t.course_name_snapshot AS course_name,"
                                + "t.class_composition,t.enrollment_count,t.planned_lab_hours"
                                + " FROM teaching_task t JOIN course c ON c.id=t.course_id"
                                + where
                                + " ORDER BY c.course_code,t.task_code";
                break;
            case "COUNT_TASKS":
            // 统计教学任务数量的基础SQL查询
                baseSql =
                        "SELECT COUNT(*) AS task_count FROM teaching_task t"
                                + " JOIN course c ON c.id=t.course_id"
                                + where;
                break;
            case "TASK_COUNT_BY_COURSE":
            // 按课程统计任务数量的基础SQL查询
                baseSql =
                        "SELECT c.course_code,c.course_name,COUNT(t.id) AS task_count"
                                + " FROM course c LEFT JOIN teaching_task t ON t.course_id=c.id"
                                + where
                                + " GROUP BY c.id,c.course_code,c.course_name"
                                + " ORDER BY task_count DESC,c.course_code";
                break;
            case "PLANNED_HOURS_BY_COURSE":
            // 按课程统计计划学时的基础SQL查询
                baseSql =
                        "SELECT c.course_code,c.course_name,"
                                + "COALESCE(SUM(t.planned_lab_hours),0) AS planned_lab_hours"
                                + " FROM course c LEFT JOIN teaching_task t ON t.course_id=c.id"
                                + where
                                + " GROUP BY c.id,c.course_code,c.course_name"
                                + " ORDER BY planned_lab_hours DESC,c.course_code";
                break;
            case "ENROLLMENT_BY_COURSE":
            // 按课程统计学生人数的基础SQL查询
                baseSql =
                        "SELECT c.course_code,c.course_name,"
                                + "COALESCE(SUM(t.enrollment_count),0) AS enrollment_count"
                                + " FROM course c LEFT JOIN teaching_task t ON t.course_id=c.id"
                                + where
                                + " GROUP BY c.id,c.course_code,c.course_name"
                                + " ORDER BY enrollment_count DESC,c.course_code";
                break;
            default:
            // 如果意图未实现，抛出异常
                throw new IllegalArgumentException("未实现的保底查询：" + intent);
        }

    // 确定结果限制数量，如果是单行查询则限制为1
        int resultLimit = singleRow ? 1 : limit;
    // 构建用于显示的SQL语句，添加结果限制
        String displaySql = baseSql + " LIMIT " + resultLimit;
    // 构建实际执行的SQL语句，用于判断是否有更多结果
        String executionSql =
                singleRow
                        ? displaySql
                        : baseSql + " LIMIT " + Math.min(resultLimit + 1, MAX_LIMIT + 1);
    // 返回编译后的查询对象
        return TeachingAiQueryCompiler.CompiledQuery.compiled(
                displaySql,
                executionSql,
                parameters,
                plan(intent, courseCode, courseName, limit),
                resultLimit);
    }

/**
 * 构建SQL查询的WHERE条件语句
 * @param courseCode 课程代码
 * @param courseName 课程名称
 * @param parameters 参数列表，用于预编译SQL语句
 * @param taskQuery 是否为任务查询，决定课程名称的查询方式
 * @return 返回构建好的WHERE条件字符串
 */
    private static String filters(
            String courseCode,      // 课程代码，用于精确匹配
            String courseName,     // 课程名称，用于模糊匹配
            List<Object> parameters, // 参数列表，用于存储SQL语句中的参数值
            boolean taskQuery) {    // 是否为任务查询的标志位
        StringBuilder where = new StringBuilder(" WHERE 1=1");  // 初始化WHERE条件基础字符串
        // 如果课程代码不为空，添加精确匹配条件
        if (courseCode != null && !courseCode.isEmpty()) {
            where.append(" AND c.course_code=?");  // 添加课程代码的精确匹配条件
            parameters.add(courseCode);            // 将课程代码添加到参数列表
        }
        // 如果课程名称不为空，添加模糊匹配条件
        if (courseName != null && !courseName.isEmpty()) {
            // 根据是否为任务查询，决定使用不同的匹配条件
            if (taskQuery) {
                // 任务查询时，同时匹配当前课程名称和历史快照
                where.append(" AND (c.course_name LIKE ? OR t.course_name_snapshot LIKE ?)");
                parameters.add("%" + courseName + "%");  // 添加当前课程名称的模糊匹配参数
                parameters.add("%" + courseName + "%");  // 添加历史快照的模糊匹配参数
            } else {
                // 非任务查询时，只匹配当前课程名称
                where.append(" AND c.course_name LIKE ?");
                parameters.add("%" + courseName + "%");  // 添加课程名称的模糊匹配参数
            }
        }
        return where.toString();  // 返回构建好的完整WHERE条件字符串
    }

    private static Map<String, Object> plan(
            String intent, String courseCode, String courseName, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intent", intent);
        result.put("courseCode", courseCode);
        result.put("courseName", courseName);
        result.put("limit", limit);
        return result;
    }

/**
 * 从问题字符串中提取数字，并进行范围限制
 * @param question 包含数字的问题字符串
 * @param defaultValue 当未找到有效数字时的默认返回值
 * @return 返回在1到MAX_LIMIT范围内的有效数字，若未找到则返回默认值
 */
    private static int numberInQuestion(String question, int defaultValue) {
        // 使用正则表达式匹配1到3位的数字，且该数字前后不能有其他数字
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{1,3})(?!\\d)").matcher(question);
        // 如果未找到匹配的数字，返回默认值
        if (!matcher.find()) {
            return defaultValue;
        }
        // 将匹配到的数字转换为整数，并确保其在1到MAX_LIMIT的范围内
        return Math.max(1, Math.min(Integer.parseInt(matcher.group(1)), MAX_LIMIT));
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

}
