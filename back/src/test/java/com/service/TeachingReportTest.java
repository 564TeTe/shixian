package com.service;

import static com.utils.TeachingExcel.map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.security.TeachingAccess;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.*;

class TeachingReportTest {
    @Test
    void sumsPerTaskAndLabHoursTimesEnrollmentWithoutMultiplyingByProjectsOrTeachers() {
        List<Map<String, Object>> input =
                Arrays.asList(
                        map(
                                "lab_code",
                                "A",
                                "lab_name",
                                "A",
                                "task_id",
                                1,
                                "course_id",
                                7,
                                "scheduled_hours",
                                8,
                                "enrollment_count",
                                30),
                        map(
                                "lab_code",
                                "A",
                                "lab_name",
                                "A",
                                "task_id",
                                2,
                                "course_id",
                                7,
                                "scheduled_hours",
                                4,
                                "enrollment_count",
                                20),
                        map(
                                "lab_code",
                                "B",
                                "lab_name",
                                "B",
                                "task_id",
                                1,
                                "course_id",
                                7,
                                "scheduled_hours",
                                2,
                                "enrollment_count",
                                30));
        List<Map<String, Object>> result = TeachingReportService.aggregateLabs(input);
        assertEquals(new BigDecimal("320"), result.get(0).get("person_hours"));
        assertEquals(new BigDecimal("12"), result.get(0).get("scheduled_hours"));
        assertEquals(1, result.get(0).get("course_count"));
        assertEquals(2, result.get(0).get("task_count"));
        assertEquals(new BigDecimal("60"), result.get(1).get("person_hours"));
    }

    @Test
    void everyTeacherReportQueryUsesMembershipExistenceFilter() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        TeachingAccess access = mock(TeachingAccess.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        when(access.teacherId(req)).thenReturn(79L);
        new TeachingReportService(db, access).report(req, null, null);
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(db, times(3)).queryForList(sql.capture(), eq(79L));
        for (String query : sql.getAllValues())
            assertTrue(
                    query.contains(
                            "EXISTS (SELECT 1 FROM teaching_task_teacher scope WHERE"
                                + " scope.task_id=t.id AND scope.teacher_account_id=?)"));
    }
}
