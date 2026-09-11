package com.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TeachingAiSqlGuardTest {
    @Test
    void rejectsFunctionsHiddenInLikeEscapeAndSharedLocks() {
        for (String sql :
                new String[] {
                    "SELECT id FROM course WHERE course_name LIKE 'x' ESCAPE GET_LOCK('x',3)",
                    "SELECT id FROM course FOR SHARE",
                    "SELECT id FROM course LOCK IN SHARE MODE"
                })
            assertThrows(
                    IllegalArgumentException.class, () -> TeachingAiSqlGuard.validate(sql), sql);
    }

    @Test
    void acceptsAggregationOverBusinessMetadata() {
        assertTrue(
                TeachingAiSqlGuard.validate(
                                "SELECT course_id, COUNT(*) AS total FROM teaching_task GROUP BY"
                                    + " course_id")
                        .startsWith("SELECT"));
    }

    @Test
    void rejectsWritesSecretsSystemObjectsAndExpensiveFunctions() {
        String[] bad = {
            "DELETE FROM teaching_task",
            "SELECT * FROM course; DELETE FROM course",
            "SELECT * FROM teacher",
            "SELECT password FROM teacher",
            "SELECT * FROM mysql.user",
            "SELECT SLEEP(5) FROM course",
            "SELECT LOAD_FILE('/etc/passwd') FROM course",
            "SELECT * INTO OUTFILE '/tmp/a' FROM course",
            "SELECT * FROM course FOR UPDATE",
            "SELECT @@version FROM course",
            "SELECT evil_function(id) FROM course",
            "WITH x AS (SELECT * FROM users) SELECT * FROM x"
        };
        for (String sql : bad)
            assertThrows(
                    IllegalArgumentException.class, () -> TeachingAiSqlGuard.validate(sql), sql);
    }

    @Test
    void doesNotAllowHiddenTablesInSubqueries() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TeachingAiSqlGuard.validate(
                                "SELECT (SELECT password FROM users LIMIT 1) FROM course"));
    }

    @Test
    void traversesJoinWhereOrderAndNestedFunctionsAndForcesBoundedRows() {
        String[] bad = {
            "SELECT c.id FROM course c JOIN teaching_task t ON GET_LOCK('x',3)=1",
            "SELECT id FROM course ORDER BY SLEEP(5)",
            "SELECT COALESCE(LOAD_FILE('x'),'') FROM course",
            "SELECT id FROM course WHERE id IN (SELECT id FROM users)",
            "SELECT * FROM (SELECT * FROM course) c",
            "SELECT * FROM course UNION SELECT * FROM users",
            "SELECT * FROM course INTO DUMPFILE 'x'",
            "SELECT * FROM course WHERE id=@x:=1",
            "SELECT * FROM course /* comment */"
        };
        for (String sql : bad)
            assertThrows(
                    IllegalArgumentException.class, () -> TeachingAiSqlGuard.validate(sql), sql);
        String safe =
                TeachingAiSqlGuard.validate(
                        "SELECT c.course_code,COUNT(t.id) AS count FROM course c JOIN teaching_task"
                            + " t ON t.course_id=c.id WHERE t.enrollment_count>0 GROUP BY"
                            + " c.course_code ORDER BY count DESC LIMIT 100000");
        assertTrue(safe.endsWith("LIMIT 201"));
    }

    @Test
    void preservesRequestedTopCountAndRejectsNonliteralOrOffsetLimits() {
        assertTrue(
                TeachingAiSqlGuard.validate("SELECT course_code FROM course LIMIT 10")
                        .endsWith("LIMIT 10"));
        assertTrue(
                TeachingAiSqlGuard.validate("SELECT course_code FROM course LIMIT 0")
                        .endsWith("LIMIT 0"));
        for (String sql :
                new String[] {
                    "SELECT * FROM course LIMIT -1",
                    "SELECT * FROM course LIMIT 10 OFFSET 20",
                    "SELECT * FROM course LIMIT 20,10",
                    "SELECT * FROM course LIMIT ?"
                })
            assertThrows(
                    IllegalArgumentException.class, () -> TeachingAiSqlGuard.validate(sql), sql);
    }
}
