package com.utils;

import com.model.ExcelRow;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict value-only Excel boundary shared by imports and exports. */
public final class TeachingExcel {

    public static final String[] TIMETABLE_HEADERS = {
        "学年", "学期", "开课学院", "课程号", "课程名称", "学分", "教学班组成", "教学班人数", "教师名称", "周学时", "起始结束周",
        "课程实验总学时", "教学地点", "专业组成", "选课人数", "起始周", "结束周", "排课起始结束周", "课程结束时间", "课程周学时", "上课时间"
    };

    public static final String[] PROJECT_HEADERS = {
        "学校代码", "实验编号", "实验名称", "实验类别", "实验类型", "实验所属学科", "实验要求", "实验者类别", "实验者人数", "每组人数", "实验学时数",
        "课程号", "课程名称", "实验总学时", "授课教师"
    };

    public static final String[] TEACHER_HEADERS = {"工号", "教师姓名", "学院"};

    public static final String[] LAB_HEADERS = {"实验室编号", "实验室名称", "实验室位置", "负责人教师工号", "设备数"};

    private TeachingExcel() {}

    public static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择非空 xlsx 文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Excel 文件不能超过10MB");
        }
        if (file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("仅支持 .xlsx 文件");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取上传文件");
        }
    }

    public static List<ExcelRow> tableRows(byte[] content, String[] headers, boolean project) {
        List<ExcelRow> result = new ArrayList<>();
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (book.getNumberOfSheets() > 30) {
                throw new IllegalArgumentException("工作表数量不能超过30");
            }
            for (Sheet sheet : book) {
                if ((project && sheet.getSheetName().matches("(?i).*(示例|样例|example).*"))
                        || Arrays.asList("填写说明", "填报说明", "填表说明").contains(sheet.getSheetName())) {
                    continue;
                }
                if (sheet.getLastRowNum() > 10000) {
                    throw new IllegalArgumentException("每张表最多10000行");
                }
                boolean populated = false;
                for (Row row : sheet) {
                    if (!blank(row)) {
                        populated = true;
                        break;
                    }
                }
                if (!populated) {
                    continue;
                }
                Row header = sheet.getRow(0);
                if (header == null || header.getLastCellNum() != headers.length) {
                    throw new IllegalArgumentException(sheet.getSheetName() + "：表头必须与下载模板一致");
                }
                for (int col = 0; col < headers.length; col++) {
                    if (!headers[col].equals(value(header.getCell(col)))) {
                        throw new IllegalArgumentException(
                                sheet.getSheetName()
                                        + "：第"
                                        + (col + 1)
                                        + "列表头应为“"
                                        + headers[col]
                                        + "”");
                    }
                }
                for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                    Row row = sheet.getRow(index);
                    if (blank(row)) {
                        continue;
                    }
                    String first = value(row.getCell(0));
                    if (first.startsWith("填报说明")
                            || first.startsWith("填写说明")
                            || first.startsWith("填表说明")
                            || first.startsWith("说明：")) {
                        continue;
                    }
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int col = 0; col < headers.length; col++) {
                        values.put(headers[col], value(row.getCell(col)));
                    }
                    if (project
                            && values.get("实验名称").isEmpty()
                            && values.get("实验编号").isEmpty()
                            && values.get("课程号").isEmpty()) {
                        continue;
                    }
                    result.add(new ExcelRow(sheet.getSheetName(), index + 1, values));
                    if (result.size() > 10000) {
                        throw new IllegalArgumentException("一个文件最多10000条数据");
                    }
                }
            }
        } catch (IOException | org.apache.poi.openxml4j.exceptions.InvalidOperationException e) {
            throw new IllegalArgumentException("无法解析 xlsx 文件，请使用下载模板");
        }
        return result;
    }

    private static boolean blank(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell c : row) {
            if (c.getCellType() != CellType.BLANK
                    && !(c.getCellType() == CellType.STRING
                            && c.getStringCellValue().trim().isEmpty())) {
                return false;
            }
        }
        return true;
    }

    private static String value(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            throw new IllegalArgumentException(
                    cell.getSheet().getSheetName() + "!" + cell.getAddress() + " 包含公式，请粘贴为原始值后导入");
        }
        if (cell.getCellType() == CellType.ERROR || cell.getCellType() == CellType.BOOLEAN) {
            throw new IllegalArgumentException("单元格必须是文本、数值或日期");
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            return new BigDecimal(Double.toString(cell.getNumericCellValue()))
                    .stripTrailingZeros()
                    .toPlainString();
        }
        String text = cell.getStringCellValue().trim();
        if (text.startsWith("=")) {
            throw new IllegalArgumentException("不能导入公式文本，请提供原始值");
        }
        if (text.length() > 16000) {
            throw new IllegalArgumentException("单元格内容过长");
        }
        return text;
    }

    public static BigDecimal number(String value, String label, boolean integer) {
        String message = label + "必须是范围内的非负" + (integer ? "整数" : "数值（最多2位小数）");
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        BigDecimal number;
        try {
            number = new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message, e);
        }
        if (number.signum() < 0
                || number.compareTo(new BigDecimal(integer ? "2147483647" : "999999.99")) > 0
                || number.stripTrailingZeros().scale() > (integer ? 0 : 2)) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    public static LocalDateTime dateTime(String value) {
        if (value == null) {
            throw new IllegalArgumentException("课程结束时间不能为空");
        }
        try {
            String text = value.trim();
            return text.matches("\\d{4}-\\d{2}-\\d{2}")
                    ? java.time.LocalDate.parse(text).atStartOfDay()
                    : LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("课程结束时间应为yyyy-MM-dd或yyyy-MM-dd HH:mm:ss", e);
        }
    }

    public static String required(String value, String label, int max) {
        if (value == null || value.trim().isEmpty() || value.length() > max) {
            throw new IllegalArgumentException(label + "不能为空且长度不能超过" + max);
        }
        return value.trim();
    }

    public static List<String> teacherNames(String value) {
        Set<String> names = new TreeSet<>();
        for (String n : value.split("[,，;；、]")) {
            if (!n.trim().isEmpty()) {
                names.add(n.trim());
            }
        }
        return new ArrayList<>(names);
    }

    public static List<Map<String, Object>> parseSchedule(String locations, String times) {
        String[] places = locations.split("[;；]", -1), parts = times.split("[;；]", -1);
        if (places.length != parts.length) {
            throw new IllegalArgumentException("教学地点与上课时间的片段数不一致");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> occupied = new HashSet<>();
        Pattern pattern = Pattern.compile("星期([一二三四五六日天])第([\\d,-]+)节\\{(.+)\\}");
        for (int segment = 0; segment < places.length; segment++) {
            String place = places[segment].trim();
            Matcher match = pattern.matcher(parts[segment].trim());
            if (place.isEmpty()
                    || "无".equals(place)
                    || "None".equals(place)
                    || place.length() > 200
                    || !match.matches()) {
                throw new IllegalArgumentException("无法识别地点或时间片段 " + (segment + 1));
            }
            int weekday = "一二三四五六日".indexOf(match.group(1)) + 1;
            if (weekday == 0) {
                weekday = 7;
            }
            SortedSet<Integer> periods = new TreeSet<>(), weeks = new TreeSet<>();
            for (String token : match.group(2).split(",")) {
                for (int p : range(token, 24)) {
                    if (!periods.add(p)) {
                        throw new IllegalArgumentException("同一片段节次重叠");
                    }
                }
            }
            for (String token : match.group(3).split(",")) {
                Matcher wm = Pattern.compile("([\\d-]+)周(?:\\(([单双])\\))?").matcher(token);
                if (!wm.matches()) {
                    throw new IllegalArgumentException("无法识别周次: " + token);
                }
                for (int week : range(wm.group(1), 53)) {
                    if (wm.group(2) != null && week % 2 != ("单".equals(wm.group(2)) ? 1 : 0)) {
                        continue;
                    }
                    if (!weeks.add(week)) {
                        throw new IllegalArgumentException("同一片段周次重复");
                    }
                }
            }
            if (weeks.isEmpty()) {
                throw new IllegalArgumentException("周次筛选后为空");
            }
            List<int[]> intervals = new ArrayList<>();
            for (int period : periods) {
                if (!intervals.isEmpty() && intervals.get(intervals.size() - 1)[1] + 1 == period) {
                    intervals.get(intervals.size() - 1)[1] = period;
                } else {
                    intervals.add(new int[] {period, period});
                }
            }
            for (int week : weeks) {
                for (int p : periods) {
                    if (!occupied.add(week + ":" + weekday + ":" + p)) {
                        throw new IllegalArgumentException("同一任务的排课片段在时间上重叠，需人工核对");
                    }
                }
                for (int[] interval : intervals) {
                    result.add(
                            map(
                                    "lab_code",
                                    place,
                                    "week",
                                    week,
                                    "weekday",
                                    weekday,
                                    "period_start",
                                    interval[0],
                                    "period_end",
                                    interval[1],
                                    "hours",
                                    interval[1] - interval[0] + 1,
                                    "source_segment",
                                    segment + 1));
                }
            }
        }
        return result;
    }

    private static Set<Integer> range(String token, int max) {
        if (!token.matches("\\d+(?:-\\d+)?")) {
            throw new IllegalArgumentException("非法范围: " + token);
        }
        String[] edges = token.split("-");
        int start, end;
        try {
            start = Integer.parseInt(edges[0]);
            end = Integer.parseInt(edges[edges.length - 1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("范围过大");
        }
        if (start < 1 || end < start || end > max) {
            throw new IllegalArgumentException("范围越界或逆序: " + token);
        }
        Set<Integer> values = new TreeSet<>();
        for (int i = start; i <= end; i++) {
            values.add(i);
        }
        return values;
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(pairs[i].toString(), pairs[i + 1]);
        }
        return result;
    }

    public static byte[] workbook(
            String sheetName, String[] headers, List<? extends List<?>> records, String note) {
        try (XSSFWorkbook book = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet(sheetName);
            sheet.createFreezePane(0, 1);
            Font font = book.createFont();
            font.setFontName("Arial");
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            CellStyle style = book.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row top = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = top.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, Math.min(60, Math.max(18, headers[i].length() * 3)) * 256);
            }
            int rowNo = 1;
            for (List<?> record : records) {
                Row row = sheet.createRow(rowNo++);
                for (int i = 0; i < record.size(); i++) {
                    Object value = record.get(i);
                    Cell c = row.createCell(i);
                    if (value instanceof Number) {
                        c.setCellValue(((Number) value).doubleValue());
                    } else {
                        c.setCellValue(value == null ? "" : value.toString());
                    }
                }
            }
            if (!records.isEmpty()) {
                sheet.setAutoFilter(
                        new org.apache.poi.ss.util.CellRangeAddress(
                                0, rowNo - 1, 0, headers.length - 1));
            }
            if (note != null) {
                Sheet instructions = book.createSheet("填写说明");
                instructions.createRow(0).createCell(0).setCellValue(note);
                instructions.setColumnWidth(0, 100 * 256);
                CellStyle wrap = book.createCellStyle();
                wrap.setWrapText(true);
                instructions.getRow(0).getCell(0).setCellStyle(wrap);
                instructions.getRow(0).setHeightInPoints(160);
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("无法生成Excel文件", e);
        }
    }
}
