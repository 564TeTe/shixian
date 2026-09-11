package com.service;

import static com.utils.TeachingExcel.*;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.ExcelRow;
import com.security.TeachingAccess;
import com.utils.TeachingPasswords;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Opt-in integration tests; all temporary business rows are rolled back. */
@EnabledIfEnvironmentVariable(named = "TEACHING_DB_TEST", matches = "1")
class TeachingImportDatabaseTest {
    JdbcTemplate db;
    DataSourceTransactionManager manager;
    TransactionStatus tx;
    TeachingImportService imports;
    TeachingReportService reports;
    MockHttpServletRequest admin;

    @BeforeEach
    void begin() {
        DriverManagerDataSource ds = TeachingTestDatabase.dataSource();
        db = new JdbcTemplate(ds);
        manager = new DataSourceTransactionManager(ds);
        tx = manager.getTransaction(new DefaultTransactionDefinition());
        TeachingAccess access = new TeachingAccess(db);
        imports = new TeachingImportService(db, new ObjectMapper(), access);
        reports = new TeachingReportService(db, access);
        admin = request("users", TeachingTestDatabase.createAccount(db, "ADMIN"));
    }

    @AfterEach
    void rollback() {
        if (tx != null) manager.rollback(tx);
    }

    private MockHttpServletRequest request(String table, long id) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.getSession().setAttribute("tableName", table);
        r.getSession().setAttribute("userId", id);
        return r;
    }

    @Test
    void timetableImportGeneratesMappedBusinessDataAndRequiresConfirmationForSecondFile() {
        String unique = UUID.randomUUID().toString().substring(0, 8),
                name = "导入验证甲" + unique,
                second = "导入验证乙" + unique;
        int[] current = TeachingTermService.calendarTerm(LocalDate.now());
        List<Object> row =
                new ArrayList<>(
                        Arrays.asList(
                                current[0] + "-" + (current[0] + 1),
                                current[1],
                                "验证学院",
                                "IMPTEST" + unique,
                                "导入验证课程",
                                1,
                                "测试班" + unique,
                                30,
                                name + "、" + second,
                                2,
                                "1-2周",
                                8,
                                "TEST-LAB-" + unique,
                                "测试专业",
                                25,
                                1,
                                2,
                                "1-2周",
                                "2027-01-01",
                                "2",
                                "星期一第1-2节{1-2周}"));
        byte[] first = workbook("课表", TIMETABLE_HEADERS, Collections.singletonList(row), null);
        Map<String, Object> imported = imports.timetable(admin, first, "test.xlsx");
        long batch = ((Number) imported.get("batchId")).longValue();
        assertEquals(1, ((Number) imported.get("promoted")).intValue());
        long taskId =
                db.queryForObject(
                        "SELECT t.id FROM teaching_task t JOIN teaching_import_row r ON"
                            + " r.id=t.source_import_row_id WHERE r.batch_id=?",
                        Long.class,
                        batch);
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM teaching_task_teacher WHERE task_id=?",
                        Integer.class,
                        taskId));
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM schedule_detail WHERE task_id=?",
                        Integer.class,
                        taskId));
        Map<String, Object> teacher =
                db.queryForMap(
                        "SELECT username AS teacher_no,password_hash AS password FROM account WHERE"
                            + " role='TEACHER' AND display_name=?",
                        name);
        assertTrue(teacher.get("teacher_no").toString().startsWith("TMP"));
        assertTrue(teacher.get("password").toString().startsWith("$2"));
        assertNull(
                db.queryForObject(
                        "SELECT equipment_count FROM laboratory WHERE lab_code=?",
                        Integer.class,
                        "TEST-LAB-" + unique));
        assertEquals(batch, imports.timetable(admin, first, "renamed.xlsx").get("batchId"));
        row.set(13, "改动专业");
        byte[] secondFile = workbook("课表", TIMETABLE_HEADERS, Collections.singletonList(row), null);
        Map<String, Object> review = imports.timetable(admin, secondFile, "second.xlsx");
        assertEquals(0, ((Number) review.get("promoted")).intValue());
        assertEquals(1, ((Number) review.get("review_count")).intValue());
        long reviewId = ((Number) review.get("batchId")).longValue();
        Map<String, Object> confirmed = imports.confirm(admin, reviewId, 2, "课表", true);
        assertNotEquals(taskId, ((Number) confirmed.get("task_id")).longValue());
        assertEquals(
                confirmed.get("task_id"),
                imports.confirm(admin, reviewId, 2, "课表", true).get("task_id"));
        List<?> projectRow =
                Arrays.asList(
                        "11059",
                        "",
                        "真实项目" + unique,
                        "3",
                        "2",
                        "0809",
                        "1",
                        "3",
                        "",
                        2,
                        2,
                        "IMPTEST" + unique,
                        "导入验证课程",
                        8,
                        name);
        Map<String, Object> projects =
                imports.projects(
                        admin,
                        taskId,
                        workbook(
                                "真实项目",
                                PROJECT_HEADERS,
                                Collections.singletonList(projectRow),
                                "填写说明"));
        assertEquals(1, projects.get("imported"));
        assertEquals(
                "0809",
                db.queryForObject(
                        "SELECT discipline_code FROM experiment_project WHERE task_id=?",
                        String.class,
                        taskId));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        imports.projects(
                                admin,
                                taskId,
                                workbook(
                                        "真实项目",
                                        PROJECT_HEADERS,
                                        Collections.singletonList(projectRow),
                                        null)));
    }

    @Test
    void teacherReportTotalsMatchOnlyTheirOwnScheduleRows() {
        long teacher = TeachingTestDatabase.createAccount(db, "TEACHER");
        long course = TeachingTestDatabase.createCourse(db),
                lab = TeachingTestDatabase.createLab(db);
        long term =
                ((Number) new TeachingTermService(db).ensureCurrentTerm().get("id")).longValue();
        String taskCode = "TEST" + UUID.randomUUID().toString();
        db.update(
                "INSERT INTO"
                    + " teaching_task(task_code,term_id,course_id,course_name_snapshot,class_composition,enrollment_count,planned_lab_hours)"
                    + " VALUES (?,?,?,'测试课程','测试班',30,2)",
                taskCode,
                term,
                course);
        long task =
                db.queryForObject(
                        "SELECT id FROM teaching_task WHERE task_code=?", Long.class, taskCode);
        db.update(
                "INSERT INTO teaching_task_teacher(task_id,teacher_account_id) VALUES (?,?)",
                task,
                teacher);
        db.update(
                "INSERT INTO"
                    + " schedule_detail(task_id,lab_id,teaching_week,weekday,period_start,period_end)"
                    + " VALUES (?,?,1,1,1,2)",
                task,
                lab);
        Map<String, Object> report = reports.report(request("teacher", teacher), null, null);
        BigDecimal expected =
                db.queryForObject(
                        "SELECT COALESCE(SUM(s.hours*t.enrollment_count),0) FROM schedule_detail s"
                            + " JOIN teaching_task t ON t.id=s.task_id WHERE EXISTS(SELECT 1 FROM"
                            + " teaching_task_teacher x WHERE x.task_id=t.id AND"
                            + " x.teacher_account_id=?)",
                        BigDecimal.class,
                        teacher);
        BigDecimal actual = BigDecimal.ZERO;
        for (Object row : (List<?>) report.get("labs"))
            actual = actual.add((BigDecimal) ((Map<?, ?>) row).get("person_hours"));
        assertEquals(0, expected.compareTo(actual));
        assertTrue(report.get("basis").toString().contains("课程关联地点"));
        assertTrue(reports.export(request("teacher", teacher), null, null, "labs").length > 100);
    }

    @Test
    void projectTemplateContainsNoRealDataAndOriginalTimetableParses() throws Exception {
        String template = System.getenv("TEACHING_PROJECT_FIXTURE"),
                timetable = System.getenv("TEACHING_TIMETABLE_FIXTURE");
        Assumptions.assumeTrue(template != null && timetable != null);
        assertTrue(
                tableRows(Files.readAllBytes(Paths.get(template)), PROJECT_HEADERS, true)
                        .isEmpty());
        List<ExcelRow> rows =
                tableRows(Files.readAllBytes(Paths.get(timetable)), TIMETABLE_HEADERS, false);
        assertEquals(255, rows.size());
        int expanded = 0;
        for (ExcelRow row : rows) {
            expanded += parseSchedule(row.at("教学地点"), row.at("上课时间")).size();
            assertNotNull(dateTime(row.at("课程结束时间")));
        }
        assertEquals(2208, expanded);
    }

    @Test
    void resourceImportsPreserveTeacherIdentityAndLabManagerLinks() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String temporary = "TMP" + suffix, real = "TEACHER" + suffix, name = "资源导入" + suffix;
        db.update(
                "INSERT INTO account(username,password_hash,display_name,role) VALUES"
                    + " (?,?,?,'TEACHER')",
                temporary,
                TeachingPasswords.hash("TestPassword928!"),
                name);
        long id =
                db.queryForObject("SELECT id FROM account WHERE username=?", Long.class, temporary);
        byte[] teachers =
                workbook(
                        "教师",
                        TEACHER_HEADERS,
                        Collections.singletonList(Arrays.asList(real, name, "测试学院")),
                        null);
        assertEquals(1, imports.teachers(admin, teachers).get("imported"));
        assertEquals(
                id, db.queryForObject("SELECT id FROM account WHERE username=?", Long.class, real));
        assertEquals(1, imports.teachers(admin, teachers).get("imported"));
        String lab = "LAB" + suffix;
        byte[] labs =
                workbook(
                        "实验室",
                        LAB_HEADERS,
                        Collections.singletonList(Arrays.asList(lab, "测试实验室", "测试楼", real, 0)),
                        null);
        assertEquals(1, imports.labs(admin, labs).get("imported"));
        assertEquals(
                id,
                db.queryForObject(
                        "SELECT manager_account_id FROM laboratory WHERE lab_code=?",
                        Long.class,
                        lab));
        assertEquals(
                "ACTIVE",
                db.queryForObject(
                        "SELECT status FROM laboratory WHERE lab_code=?", String.class, lab));
        assertEquals(1, imports.labs(admin, labs).get("imported"));
        Map<String, Object> detail = imports.detail(admin, createReviewBatch());
        assertFalse(((List<?>) detail.get("rows")).isEmpty());
    }

    private long createReviewBatch() {
        String hash =
                UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", "");
        db.update(
                "INSERT INTO teaching_import_batch(file_sha256,file_name,parser_version,row_count)"
                    + " VALUES (?,'test.xlsx','test',1)",
                hash);
        long batch =
                db.queryForObject(
                        "SELECT id FROM teaching_import_batch WHERE file_sha256=?",
                        Long.class,
                        hash);
        db.update(
                "INSERT INTO"
                    + " teaching_import_row(batch_id,sheet_name,source_row,raw_data,issues,status)"
                    + " VALUES (?,'测试',2,'{}','[]','REVIEW')",
                batch);
        return batch;
    }
}
