package com.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.teaching.TeachingExcel.map;
import java.util.*;

class TeachingImportTest {
    @Test void importedTermStatusUsesFebruaryFirstAndAugustThirtyFirstBoundaries() {
        assertEquals("ARCHIVED",TeachingImportService.importTermStatus(2025,2,java.time.LocalDate.of(2026,8,31)));
        assertEquals("OPEN",TeachingImportService.importTermStatus(2026,1,java.time.LocalDate.of(2026,8,31)));
        assertEquals("ARCHIVED",TeachingImportService.importTermStatus(2025,1,java.time.LocalDate.of(2026,2,1)));
        assertEquals("OPEN",TeachingImportService.importTermStatus(2025,2,java.time.LocalDate.of(2026,2,1)));
    }
    @Test void sameFileHashReturnsPreviousBatchWithoutReadingOrWritingWorkbook() {
        JdbcTemplate db=mock(JdbcTemplate.class); TeachingAccess access=mock(TeachingAccess.class);
        when(db.queryForList(eq("SELECT id FROM teaching_import_batch WHERE file_sha256=?"),eq(Long.class),anyString())).thenReturn(Collections.singletonList(1L));
        when(db.queryForMap(anyString(),eq(1L))).thenReturn(map("rows_count",255,"promoted",255,"errors",0,"review_count",0));
        Map<String,Object> result=new TeachingImportService(db,new ObjectMapper(),access).timetable(new MockHttpServletRequest(),new byte[]{1,2,3},"same.xlsx");
        assertEquals(1L,result.get("batchId")); assertEquals(255,result.get("promoted"));
        assertTrue(mockingDetails(db).getInvocations().stream().noneMatch(i->i.getMethod().getName().equals("update")));
    }
    @Test void projectImportRejectsHistoricalOrForeignTaskBeforeExcelRead() {
        JdbcTemplate db=mock(JdbcTemplate.class); TeachingAccess access=mock(TeachingAccess.class); MockHttpServletRequest request=new MockHttpServletRequest();
        doThrow(new TeachingAccess.AccessException(403,"历史任务只读")).when(access).requireTask(request,9L,true);
        TeachingImportService service=new TeachingImportService(db,new ObjectMapper(),access);
        assertThrows(TeachingAccess.AccessException.class,()->service.projects(request,9L,new byte[]{1}));
        verifyNoInteractions(db);
    }
    @Test void distinctTaskConfirmationRequiresExplicitAcknowledgement() {
        TeachingImportService service=new TeachingImportService(mock(JdbcTemplate.class),new ObjectMapper(),mock(TeachingAccess.class));
        assertThrows(IllegalArgumentException.class,()->service.confirm(new MockHttpServletRequest(),1L,2,"Sheet1",false));
    }
}
