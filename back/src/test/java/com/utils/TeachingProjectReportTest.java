package com.utils;

import static com.utils.TeachingExcel.map;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TeachingProjectReportTest {
    @Test
    void emptyExportContainsHeadersAndInstructionsWithoutTemplateExamples() throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(
                TeachingProjectReport.workbook(Collections.emptyList())))) {
            assertEquals(1, book.getNumberOfSheets());
            Sheet sheet = book.getSheetAt(0);
            assertEquals("学校代码", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("授课教师", sheet.getRow(0).getCell(14).getStringCellValue());
            for (int row = 1; row <= 10; row++) {
                for (int col = 0; col < 15; col++) {
                    assertEquals(CellType.BLANK, sheet.getRow(row).getCell(col).getCellType());
                }
            }
            assertTrue(sheet.getRow(12).getCell(0).getStringCellValue().contains("填表说明"));
        }
    }

    @Test
    void moreThanTenProjectsPreserveAllColumnsAndMoveInstructionsBelowData() throws Exception {
        List<Map<String, Object>> projects = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            projects.add(map("school_code", "00123", "project_code", "P" + index,
                    "project_name", "=测试实验" + index, "category_code", "1", "type_code", "2",
                    "discipline_code", "0809", "requirement_code", "1",
                    "participant_type_code", "3", "participant_count", null,
                    "group_size", 4, "hours", new BigDecimal("1.50"),
                    "course_code", "0001", "course_name", "测试课程",
                    "planned_lab_hours", 24, "teacher_names", "教师甲、教师乙"));
        }
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(
                TeachingProjectReport.workbook(projects)))) {
            Sheet sheet = book.getSheetAt(0);
            for (int index = 0; index < 12; index++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(index + 1);
                assertEquals("00123", row.getCell(0).getStringCellValue());
                assertEquals("P" + index, row.getCell(1).getStringCellValue());
                assertEquals(CellType.STRING, row.getCell(2).getCellType());
                assertEquals("=测试实验" + index, row.getCell(2).getStringCellValue());
                assertEquals("0809", row.getCell(5).getStringCellValue());
                assertEquals(CellType.BLANK, row.getCell(8).getCellType());
                assertEquals(4, row.getCell(9).getNumericCellValue());
                assertEquals(1.5, row.getCell(10).getNumericCellValue());
                assertEquals("0001", row.getCell(11).getStringCellValue());
                assertEquals("测试课程", row.getCell(12).getStringCellValue());
                assertEquals(24, row.getCell(13).getNumericCellValue());
                assertEquals("教师甲、教师乙", row.getCell(14).getStringCellValue());
            }
            assertTrue(sheet.getRow(14).getCell(0).getStringCellValue().contains("填表说明"));
            assertEquals("A15:O18", sheet.getMergedRegion(0).formatAsString());
        }
    }
}
