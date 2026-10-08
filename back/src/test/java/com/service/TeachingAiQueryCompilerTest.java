package com.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.TeachingAiSqlGuard;

import org.junit.jupiter.api.Test;

class TeachingAiQueryCompilerTest {

    private final TeachingAiQueryCompiler compiler = new TeachingAiQueryCompiler();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parsesModelSqlJsonAndPreservesBoundedSelect() {
        TeachingAiQueryCompiler.CompiledQuery query =
                compiler.modelSql(
                        json,
                        "```json\n"
                            + "{\"sql\":\"SELECT c.course_code FROM course c LIMIT 20\"}\n"
                            + "```");

        assertTrue(query.getSql().contains("FROM course c"));
        assertTrue(query.getSql().endsWith("LIMIT 20"));
        assertTrue(query.getParameters().isEmpty());
        assertEquals(TeachingAiQueryCompiler.MAX_LIMIT, query.getResultLimit());
    }

    @Test
    void rejectsMissingSqlAndUnsafeModelSql() {
        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.modelSql(json, "{\"not_sql\":true}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.modelSql(json, "{\"sql\":\"SELECT * FROM course\"}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.modelSql(json, "{\"sql\":\"UPDATE course SET course_name='x'\"}"));
    }

    @Test
    void fallbackBuildsParameterizedCourseTaskQuery() {
        TeachingAiQueryCompiler.CompiledQuery query =
                TeachingAiFallback.compile(
                        "列出软件工程的教学任务", TeachingAiSqlGuard.allowedTables());

        assertNotNull(query);
        assertTrue(query.getSql().contains("JOIN course c ON c.id=t.course_id"));
        assertTrue(query.getSql().contains("t.course_name_snapshot LIKE ?"));
        assertTrue(query.getSql().endsWith("LIMIT 20"));
        assertTrue(query.getExecutionSql().endsWith("LIMIT 21"));
        assertEquals(2, query.getParameters().size());
        assertEquals("%软件工程%", query.getParameters().get(0));
        assertEquals(20, query.getResultLimit());
    }

    @Test
    void fallbackProtectsTeacherWorkloadViewAndSkipsComplexQuestions() {
        TeachingAiQueryCompiler.CompiledQuery teacher =
                TeachingAiFallback.compile(
                        "陈冲教几个班", TeachingAiSqlGuard.allowedTables());

        assertNotNull(teacher);
        assertTrue(teacher.getSql().contains("FROM ai_teacher_workload"));
        assertFalse(teacher.getSql().contains("account"));
        assertEquals("陈冲", teacher.getParameters().get(0));
        assertEquals("陈冲", teacher.getParameters().get(1));

        assertNull(
                TeachingAiFallback.compile(
                        "统计每位教师的排课学时", TeachingAiSqlGuard.allowedTables()));
    }
}
