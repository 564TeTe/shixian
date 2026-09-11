package com.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.model.ExcelRow;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

class TeachingExcelTest {
    @Test
    void supportsDateOnlyAndDateTimeSourceValues() {
        assertEquals(
                java.time.LocalDateTime.of(2025, 12, 16, 0, 0),
                TeachingExcel.dateTime("2025-12-16 "));
        assertEquals(
                java.time.LocalDateTime.of(2025, 12, 16, 18, 20),
                TeachingExcel.dateTime("2025-12-16 18:20:00"));
        assertThrows(IllegalArgumentException.class, () -> TeachingExcel.dateTime("2025-99-16"));
    }

    @Test
    void downloadedTemplatesRemainImportableAndContainNoDemoRows() {
        byte[] template =
                TeachingExcel.workbook(
                        "数据", TeachingExcel.TEACHER_HEADERS, Collections.emptyList(), "填写真实账号");
        assertTrue(
                TeachingExcel.tableRows(template, TeachingExcel.TEACHER_HEADERS, false).isEmpty());
    }

    @Test
    void rejectsMissingTimetableHeaderEvenIfSheetNameContainsExample() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = book.createSheet("示例命名的真实课表");
            sheet.createRow(1).createCell(0).setCellValue("2025-2026");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            TeachingExcel.tableRows(
                                    bytes(book), TeachingExcel.TIMETABLE_HEADERS, false));
        }
    }

    @Test
    void expandsOddWeeksAndNonconsecutivePeriodsWithoutCountingGap() {
        List<Map<String, Object>> rows = TeachingExcel.parseSchedule("A101", "星期一第1-2,4节{1-5周(单)}");
        assertEquals(6, rows.size());
        assertEquals(9, rows.stream().mapToInt(r -> ((Number) r.get("hours")).intValue()).sum());
    }

    @Test
    void rejectsOverlappingSegmentsAndUnpairedLocations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TeachingExcel.parseSchedule("A;B", "星期一第1-2节{1周};星期一第2-3节{1周}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> TeachingExcel.parseSchedule("A;B", "星期一第1-2节{1周}"));
    }

    @Test
    void skipsExampleAndBlankProjectRowsButKeepsRealRows() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet example = book.createSheet("示例");
            fill(example.createRow(0), TeachingExcel.PROJECT_HEADERS);
            fill(example.createRow(1), new String[] {"11059", "", "不应导入"});
            Sheet data = book.createSheet("项目");
            fill(data.createRow(0), TeachingExcel.PROJECT_HEADERS);
            fill(data.createRow(1), new String[] {"11059", "", ""});
            fill(data.createRow(2), new String[] {"11059", "", "实际项目"});
            List<ExcelRow> rows =
                    TeachingExcel.tableRows(bytes(book), TeachingExcel.PROJECT_HEADERS, true);
            assertEquals(1, rows.size());
            assertEquals("实际项目", rows.get(0).getValues().get("实验名称"));
        }
    }

    @Test
    void rejectsFormulasInsteadOfTrustingCachedValues() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet data = book.createSheet("教师");
            fill(data.createRow(0), TeachingExcel.TEACHER_HEADERS);
            Row row = data.createRow(1);
            row.createCell(0).setCellFormula("1+1");
            row.createCell(1).setCellValue("教师");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            TeachingExcel.tableRows(
                                    bytes(book), TeachingExcel.TEACHER_HEADERS, false));
        }
    }

    private static void fill(Row row, String[] cells) {
        for (int i = 0; i < cells.length; i++) row.createCell(i).setCellValue(cells[i]);
    }

    private static byte[] bytes(Workbook book) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        book.write(out);
        return out.toByteArray();
    }
}
