package com.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class TeachingAiQueryCompilerTest {

    private final TeachingAiQueryCompiler compiler = new TeachingAiQueryCompiler();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void compilesCourseTaskQueryWithBoundParameters() {
        TeachingAiQueryCompiler.CompiledQuery query =
                compiler.compile(
                        json,
                        "{\"intent\":\"LIST_TASKS\",\"courseCode\":null,"
                            + "\"courseName\":\"软件工程\",\"limit\":10,\"reason\":\"\"}");

        assertTrue(query.getSql().contains("JOIN course c ON c.id=t.course_id"));
        assertTrue(query.getSql().contains("t.course_name_snapshot LIKE ?"));
        assertTrue(query.getSql().endsWith("LIMIT 10"));
        assertTrue(query.getExecutionSql().endsWith("LIMIT 11"));
        assertEquals(2, query.getParameters().size());
        assertEquals("%软件工程%", query.getParameters().get(0));
        assertFalse(query.getSql().contains("软件工程"));
        assertEquals("LIST_TASKS", query.getPlan().get("intent"));
    }

    @Test
    void compilesDeterministicAggregateInsteadOfAcceptingModelSql() {
        TeachingAiQueryCompiler.CompiledQuery query =
                compiler.compile(
                        json,
                        "```json\n"
                            + "{\"intent\":\"PLANNED_HOURS_BY_COURSE\","
                            + "\"courseCode\":null,\"courseName\":null,\"limit\":5,\"reason\":\"\"}"
                            + "\n```");

        assertTrue(query.getSql().contains("SUM(t.planned_lab_hours)"));
        assertTrue(query.getSql().contains("GROUP BY c.id,c.course_code,c.course_name"));
        assertTrue(query.getSql().endsWith("LIMIT 5"));
        assertTrue(query.getExecutionSql().endsWith("LIMIT 6"));
        assertTrue(query.getParameters().isEmpty());
    }

    @Test
    void rejectsRawSqlUnknownIntentAndOutOfRangeLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(json, "SELECT * FROM course"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        compiler.compile(
                                json,
                                "{\"intent\":\"DELETE_DATA\",\"courseCode\":null,"
                                    + "\"courseName\":null,\"limit\":20,\"reason\":\"\"}"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        compiler.compile(
                                json,
                                "{\"intent\":\"LIST_COURSES\",\"courseCode\":null,"
                                    + "\"courseName\":null,\"limit\":201,\"reason\":\"\"}"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        compiler.compile(
                                json,
                                "{\"intent\":\"LIST_COURSES\",\"courseCode\":null,"
                                    + "\"courseName\":null,\"limit\":20,\"reason\":\"\","
                                    + "\"sql\":\"SELECT * FROM account\"}"));
    }

    @Test
    void returnsUnsupportedPlanWithoutGeneratingSql() {
        TeachingAiQueryCompiler.CompiledQuery query =
                compiler.compile(
                        json,
                        "{\"intent\":\"UNSUPPORTED\",\"courseCode\":null,"
                            + "\"courseName\":null,\"limit\":20,"
                            + "\"reason\":\"演示版未开放实验室查询\"}");
        assertTrue(query.isUnsupported());
        assertEquals("", query.getSql());
        assertTrue(query.getPlan().get("reason").toString().contains("实验室"));
    }

    @Test
    void recognizesUnambiguousDemoQuestionsLocallyWhenModelMisclassifiesThem() {
        TeachingAiQueryCompiler.CompiledQuery count =
                compiler.fallback("一共有多少门课程");
        assertNotNull(count);
        assertEquals("COUNT_COURSES", count.getPlan().get("intent"));
        assertTrue(count.getSql().contains("COUNT(*) AS course_count"));

        TeachingAiQueryCompiler.CompiledQuery tasks =
                compiler.fallback("列出软件工程的教学任务");
        assertNotNull(tasks);
        assertEquals("LIST_TASKS", tasks.getPlan().get("intent"));
        assertEquals("%软件工程%", tasks.getParameters().get(0));
    }

    @Test
    void countsClassesForTeacherWithoutExposingAccountTable() {
        TeachingAiQueryCompiler.CompiledQuery query =
                compiler.fallback("陈冲教几个班");

        assertNotNull(query);
        assertEquals("COUNT_CLASSES_BY_TEACHER", query.getPlan().get("intent"));
        assertTrue(query.getSql().contains("COUNT(DISTINCT task_id) AS class_count"));
        assertTrue(query.getSql().contains("FROM ai_teacher_workload"));
        assertEquals("陈冲", query.getParameters().get(0));
        assertEquals("陈冲", query.getParameters().get(1));
        assertFalse(query.getSql().contains("account"));
    }

    @Test
    void acceptsModelGeneratedSelectForAnyAllowedTable() {
        String output =
                "{\"sql\":\"SELECT l.lab_code AS lab_code,"
                        + "COUNT(DISTINCT s.task_id) AS task_count FROM laboratory l"
                        + " LEFT JOIN schedule_detail s ON s.lab_id=l.id"
                        + " GROUP BY l.id,l.lab_code LIMIT 20\"}";

        assertTrue(compiler.hasModelSql(json, output));
        TeachingAiQueryCompiler.CompiledQuery query = compiler.modelSql(json, output);
        assertEquals("MODEL_SQL", query.getPlan().get("intent"));
        assertTrue(query.getSql().contains("FROM laboratory l"));
        assertTrue(query.getSql().endsWith("LIMIT 20"));
    }

    @Test
    void leavesComplexTeacherQuestionsToTheModel() {
        assertNull(compiler.fallback("列出教学任务最多的前5位教师"));
        assertNull(compiler.fallback("统计每位教师的排课学时"));
    }
}
