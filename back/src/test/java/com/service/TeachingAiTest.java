package com.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.exception.AccessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.TeachingAccess;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

class TeachingAiTest {
    @Test
    void teachersCannotTriggerAnyModelOrDatabaseQuery() {
        TeachingAccess access = mock(TeachingAccess.class);
        JdbcTemplate db = mock(JdbcTemplate.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        doThrow(new AccessException(403, "仅管理员可用")).when(access).requireAdmin(request);
        TeachingAiService service = new TeachingAiService(access, db, new ObjectMapper());
        assertThrows(
                AccessException.class,
                () -> service.query(request, Collections.singletonMap("question", "统计教学任务")));
        verifyNoInteractions(db);
    }

    @Test
    void teacherStatusExplicitlyReportsUnavailableAdminOnlyFeature() {
        TeachingAccess access = mock(TeachingAccess.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(access.teacherId(request)).thenReturn(8L);
        java.util.Map<String, Object> result =
                new TeachingAiService(access, mock(JdbcTemplate.class), new ObjectMapper())
                        .status(request);
        assertEquals(true, result.get("admin_only"));
        assertEquals(false, result.get("available"));
        assertTrue(result.get("message").toString().contains("仅供管理员"));
    }
}
