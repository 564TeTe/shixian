package com.service;

import static com.utils.TeachingExcel.map;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TeachingAiScopeTest {
    private final List<Map<String, Object>> terms = Arrays.asList(
            map("id", 3L, "name", "2026-2027-1"));
    private final List<Map<String, Object>> labs = Arrays.asList(
            map("id", 76L, "lab_code", "36-601", "lab_name", "36-601实验室"));
    private final String query = "SELECT COUNT(DISTINCT t.course_id) AS course_count "
            + "FROM schedule_detail s JOIN teaching_task t ON t.id=s.task_id "
            + "JOIN laboratory l ON l.id=s.lab_id JOIN academic_term a ON a.id=t.term_id ";

    @Test void roomQuestionCannotReturnGlobalCountOrBypassWhereWithOr() {
        TeachingAiScope scope = TeachingAiScope.resolve("36栋601一共有多少门课程", "", terms, labs);
        assertThrows(IllegalArgumentException.class, () -> scope.validate("SELECT COUNT(*) FROM course"));
        assertThrows(IllegalArgumentException.class, () -> scope.validate(query + "WHERE l.id=76 OR 1=1"));
        assertThrows(IllegalArgumentException.class, () -> scope.validate(query + "WHERE NOT(l.id=76)"));
        assertThrows(IllegalArgumentException.class, () -> scope.validate(query + "WHERE a.id=76"));
        assertDoesNotThrow(() -> scope.validate(query + "WHERE l.id=76"));
        assertTrue(scope.description().contains("36-601"));
    }

    @Test void selectedTermMustBeAppliedButExplicitTermTakesPriority() {
        TeachingAiScope selected = TeachingAiScope.resolve("36号楼601有多少门课", "3", terms, labs);
        assertThrows(IllegalArgumentException.class, () -> selected.validate(query + "WHERE l.id=76"));
        assertDoesNotThrow(() -> selected.validate(query + "WHERE l.id=76 AND a.id=3"));
        TeachingAiScope explicit = TeachingAiScope.resolve("2025-2026学年第2学期36-601有多少门课", "3", terms, labs);
        assertDoesNotThrow(() -> explicit.validate(query + "WHERE l.id=76 AND a.start_year=2025 AND a.term_no=2"));
        assertFalse(explicit.description().contains("页面选择"));
        TeachingAiScope all = TeachingAiScope.resolve("全部学期36栋601多少门课", "3", terms, labs);
        assertDoesNotThrow(() -> all.validate(query + "WHERE l.id=76"));
    }

    @Test void unknownRoomAndInvalidTermAskForClarification() {
        assertThrows(IllegalArgumentException.class, () -> TeachingAiScope.resolve("36栋999多少门课", "", terms, labs));
        assertThrows(IllegalArgumentException.class, () -> TeachingAiScope.resolve("多少门课程", "999", terms, labs));
        assertThrows(IllegalArgumentException.class, () -> TeachingAiScope.resolve("上学期多少门课程", "", terms, labs));
    }
}
