package com.teaching;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeachingCoreTest {
    private MockHttpServletRequest request(String table, long id) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.getSession().setAttribute("tableName", table);
        r.getSession().setAttribute("userId", id);
        return r;
    }

    @Test void academicCalendarChangesOnAugust31AndFebruary1() {
        assertArrayEquals(new int[]{2025, 2}, TeachingTermService.calendarTerm(LocalDate.of(2026, 8, 30)));
        assertArrayEquals(new int[]{2026, 1}, TeachingTermService.calendarTerm(LocalDate.of(2026, 8, 31)));
        assertArrayEquals(new int[]{2026, 1}, TeachingTermService.calendarTerm(LocalDate.of(2027, 1, 31)));
        assertArrayEquals(new int[]{2026, 2}, TeachingTermService.calendarTerm(LocalDate.of(2027, 2, 1)));
    }

    @Test void unsupportedRoleCannotBecomeAdministrator() {
        TeachingAccess access = new TeachingAccess(mock(JdbcTemplate.class));
        assertThrows(TeachingAccess.AccessException.class, () -> access.teacherId(request("xuesheng", 1)));
        assertThrows(TeachingAccess.AccessException.class, () -> access.teacherId(new MockHttpServletRequest()));
    }

    @Test void teacherCannotGuessAnotherTeachersTaskId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(1L);
        when(jdbc.queryForList(anyString(), eq(999L), eq(11L))).thenReturn(Collections.emptyList());
        TeachingAccess access = new TeachingAccess(jdbc);
        assertThrows(TeachingAccess.AccessException.class, () -> access.requireTask(request("teacher", 11), 999, false));
        verify(jdbc).queryForList(contains("teacher_id=?"), eq(999L), eq(11L));
    }

    @Test void archivedAndDraftTermsRejectEvenAdministratorWrites() {
        for (String status : Arrays.asList("ARCHIVED", "DRAFT")) {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(1L);
            Map<String,Object> task = new HashMap<>();
            task.put("id", 9L); task.put("status", status);
            when(jdbc.queryForList(anyString(), eq(9L))).thenReturn(Collections.singletonList(task));
            TeachingAccess access = new TeachingAccess(jdbc);
            assertThrows(TeachingAccess.AccessException.class, () -> access.requireTask(request("users", 1), 9, true));
            assertEquals(9L, access.requireTask(request("users", 1), 9, false).get("id"));
        }
    }

    @Test void projectValidationRejectsUnsupportedCodesAndFractionalGroupSize() {
        Map<String,Object> values = validProject();
        values.put("category_code", "9");
        assertThrows(IllegalArgumentException.class, () -> TeachingService.validateProject(values));
        final Map<String,Object> badGroup = validProject(); badGroup.put("group_size", 1.5);
        assertThrows(IllegalArgumentException.class, () -> TeachingService.validateProject(badGroup));
        assertDoesNotThrow(() -> TeachingService.validateProject(validProject()));
    }

    @Test void openStatusAloneCannotReopenHistoricalTask() {
        Map<String,Object> task = new HashMap<>();
        task.put("status", "OPEN"); task.put("start_year", 2000); task.put("term_no", 1);
        assertFalse(TeachingAccess.writable(task));
        int[] current = TeachingTermService.calendarTerm(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
        task.put("start_year", current[0]); task.put("term_no", current[1]);
        assertTrue(TeachingAccess.writable(task));
    }

    @Test void projectValidationRejectsMissingNameAndExcessPrecision() {
        Map<String,Object> missing = validProject(); missing.put("name", "  ");
        assertThrows(IllegalArgumentException.class, () -> TeachingService.validateProject(missing));
        Map<String,Object> precision = validProject(); precision.put("hours", "1.001");
        assertThrows(IllegalArgumentException.class, () -> TeachingService.validateProject(precision));
        Map<String,Object> participant = validProject(); participant.put("participant_type_code", "9");
        assertThrows(IllegalArgumentException.class, () -> TeachingService.validateProject(participant));
    }

    @Test void teacherCannotCreateOtherTeachersAccounts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(1L);
        TeachingAccess access = new TeachingAccess(jdbc);
        TeachingService service = new TeachingService(jdbc, access, new TeachingTermService(jdbc));
        assertThrows(TeachingAccess.AccessException.class, () -> service.saveTeacher(request("teacher", 11), null, new HashMap<>()));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private Map<String,Object> validProject() {
        Map<String,Object> p = new HashMap<>();
        p.put("school_code", "TEST"); p.put("name", "验证项目");
        p.put("category_code", "1"); p.put("type_code", "2"); p.put("discipline_code", "0809");
        p.put("requirement_code", "1"); p.put("participant_type_code", "1");
        p.put("group_size", 2); p.put("hours", 2); p.put("sort_order", 0);
        return p;
    }
}
