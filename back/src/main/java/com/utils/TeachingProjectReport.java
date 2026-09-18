package com.utils;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Fill the supplied collection form without including its demonstration records. */
public final class TeachingProjectReport {
    private static final String[] COLUMNS = {
        "school_code", "project_code", "project_name", "category_code", "type_code",
        "discipline_code", "requirement_code", "participant_type_code", "participant_count",
        "group_size", "hours", "course_code", "course_name", "planned_lab_hours", "teacher_names"
    };

    private TeachingProjectReport() {}

    public static byte[] workbook(List<Map<String, Object>> projects) {
        try (InputStream input = new ClassPathResource(
                        "templates/teaching-project-collection.xlsx").getInputStream();
                XSSFWorkbook book = new XSSFWorkbook(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            while (book.getNumberOfSheets() > 1) book.removeSheetAt(1);
            Sheet sheet = book.getSheetAt(0);
            book.setSheetName(0, "教学实验项目采集表");
            int capacity = Math.max(10, projects.size());
            if (capacity > 10) {
                // Keep the blank separator and the merged instructions below the data.
                sheet.shiftRows(11, sheet.getLastRowNum(), capacity - 10, true, false);
            }
            CellStyle[] styles = new CellStyle[COLUMNS.length];
            for (int col = 0; col < COLUMNS.length; col++) {
                styles[col] = book.createCellStyle();
                styles[col].cloneStyleFrom(sheet.getRow(1).getCell(col).getCellStyle());
                styles[col].setWrapText(true);
            }
            for (int index = 0; index < capacity; index++) {
                Row row = sheet.getRow(index + 1);
                if (row == null) row = sheet.createRow(index + 1);
                int lines = 2;
                for (int col = 0; col < COLUMNS.length; col++) {
                    Cell cell = row.getCell(col, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    cell.setBlank();
                    cell.setCellStyle(styles[col]);
                    Object value = index < projects.size() ? projects.get(index).get(COLUMNS[col]) : null;
                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                    } else if (value != null) {
                        String text = value.toString();
                        cell.setCellValue(text); // Strings remain strings, including leading zeroes and '='.
                        double width = Math.max(1, sheet.getColumnWidth(col) / 256.0 - 1);
                        int textLines = 0;
                        for (String part : text.split("\\n", -1)) {
                            int units = part.codePoints().map(c -> c > 255 ? 2 : 1).sum();
                            textLines += Math.max(1, (int) Math.ceil(units / width));
                        }
                        lines = Math.max(lines, textLines);
                    }
                }
                row.setHeightInPoints(Math.min(409, Math.max(32.25f, lines * 15f)));
            }
            sheet.createFreezePane(0, 1);
            sheet.setRepeatingRows(new CellRangeAddress(0, 0, -1, -1));
            book.setActiveSheet(0);
            book.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("教学实验项目采集表导出失败", e);
        }
    }
}
