package com.service;

import static com.utils.TeachingExcel.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Real HTTP/browser test. Commits isolated fixtures and deletes only those fixtures in finally. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "TEACHING_BROWSER_TEST", matches = "1")
class WorkspaceBrowserIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TeachingService teaching;
    @Autowired private TeachingTermService terms;
    @Autowired private org.springframework.transaction.PlatformTransactionManager manager;

    private long adminId,
            teacherId,
            strangerId,
            courseId,
            labId,
            currentId,
            historyId,
            ownTask,
            foreignTask,
            sourceTask;
    private final String suffix = UUID.randomUUID().toString().substring(0, 12);
    private final String importName = "workspace-" + suffix + ".xlsx";

    private MockHttpServletRequest request(String table, long id) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.getSession().setAttribute("tableName", table);
        r.getSession().setAttribute("userId", id);
        return r;
    }

    private long task(long teacher, String className) {
        Map<String, Object> body =
                map(
                        "termId",
                        currentId,
                        "courseId",
                        courseId,
                        "teacherIds",
                        Collections.singletonList(teacher),
                        "classComposition",
                        className,
                        "enrollmentCount",
                        30,
                        "plannedLabHours",
                        2);
        long id =
                ((Number) teaching.createTask(request("users", adminId), body).get("id"))
                        .longValue();
        jdbc.update(
                "INSERT INTO"
                    + " schedule_detail(task_id,lab_id,teaching_week,weekday,period_start,period_end)"
                    + " VALUES (?,?,1,7,1,2)",
                id,
                labId);
        return id;
    }

    private String username(long id) {
        return jdbc.queryForObject("SELECT username FROM account WHERE id=?", String.class, id);
    }

    @Test
    void workspaceRunsAgainstRealHttpAndDatabase() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(manager);
        Path directory = Files.createTempDirectory("teaching-workspace-http-");
        try {
            tx.execute(
                    status -> {
                        adminId = TeachingTestDatabase.createAccount(jdbc, "ADMIN");
                        teacherId = TeachingTestDatabase.createAccount(jdbc, "TEACHER");
                        strangerId = TeachingTestDatabase.createAccount(jdbc, "TEACHER");
                        courseId = TeachingTestDatabase.createCourse(jdbc);
                        labId = TeachingTestDatabase.createLab(jdbc);
                        currentId = ((Number) terms.ensureCurrentTerm().get("id")).longValue();
                        int year = 1900;
                        while (jdbc.queryForObject(
                                        "SELECT COUNT(*) FROM academic_term WHERE start_year=? AND"
                                            + " term_no=1",
                                        Integer.class,
                                        year)
                                > 0) year++;
                        jdbc.update(
                                "INSERT INTO academic_term(start_year,term_no,status) VALUES"
                                    + " (?,1,'ARCHIVED')",
                                year);
                        historyId =
                                jdbc.queryForObject(
                                        "SELECT id FROM academic_term WHERE start_year=? AND"
                                            + " term_no=1",
                                        Long.class,
                                        year);
                        ownTask = task(teacherId, "Browser own " + suffix);
                        foreignTask = task(strangerId, "Browser foreign " + suffix);
                        sourceTask = task(teacherId, "Browser history " + suffix);
                        teaching.createProject(
                                request("teacher", teacherId),
                                map(
                                        "task_id",
                                        sourceTask,
                                        "school_code",
                                        "11059",
                                        "name",
                                        "History project " + suffix,
                                        "category_code",
                                        "3",
                                        "type_code",
                                        "2",
                                        "discipline_code",
                                        "0809",
                                        "requirement_code",
                                        "1",
                                        "participant_type_code",
                                        "3",
                                        "group_size",
                                        2,
                                        "hours",
                                        2));
                        jdbc.update(
                                "UPDATE teaching_task SET term_id=? WHERE id=?",
                                historyId,
                                sourceTask);
                        return null;
                    });
            String courseCode =
                    jdbc.queryForObject(
                            "SELECT course_code FROM course WHERE id=?", String.class, courseId);
            String labCode =
                    jdbc.queryForObject(
                            "SELECT lab_code FROM laboratory WHERE id=?", String.class, labId);
            Map<String, Object> term = terms.currentTerm();
            int year = ((Number) term.get("start_year")).intValue();
            List<Object> row =
                    new ArrayList<>(
                            Arrays.asList(
                                    year + "-" + (year + 1),
                                    term.get("term_no"),
                                    "Test college",
                                    courseCode,
                                    "事务测试课程",
                                    1,
                                    "Browser import " + suffix,
                                    30,
                                    username(teacherId),
                                    2,
                                    "1周",
                                    2,
                                    labCode,
                                    "Test major",
                                    30,
                                    1,
                                    2,
                                    "1周",
                                    "2026-12-31",
                                    "2",
                                    "星期日第1-2节{1周}"));
            Files.write(
                    directory.resolve(importName),
                    workbook("项目", TIMETABLE_HEADERS, Collections.singletonList(row), null));
            row.set(13, "Another major");
            Files.write(
                    directory.resolve("review-" + importName),
                    workbook("项目", TIMETABLE_HEADERS, Collections.singletonList(row), null));
            List<Object> projectRow =
                    Arrays.asList(
                            "11059",
                            "FORGED-999",
                            "Imported project " + suffix,
                            "3",
                            "2",
                            "0809",
                            "1",
                            "3",
                            "",
                            2,
                            2,
                            courseCode,
                            "事务测试课程",
                            2,
                            username(teacherId));
            Files.write(
                    directory.resolve("projects.xlsx"),
                    workbook("项目", PROJECT_HEADERS, Collections.singletonList(projectRow), null));
            ProcessBuilder builder =
                    new ProcessBuilder(
                            "node",
                            Paths.get("../static-front/smoke-test.cjs")
                                    .toAbsolutePath()
                                    .normalize()
                                    .toString());
            Map<String, String> env = builder.environment();
            env.put(
                    "WORKSPACE_URL",
                    "http://127.0.0.1:" + port + "/springboote51e2/workspace/index.html");
            env.put("WORKSPACE_ADMIN", username(adminId));
            env.put("WORKSPACE_TEACHER", username(teacherId));
            env.put("WORKSPACE_PASSWORD", "TestPassword928!");
            env.put("WORKSPACE_TASK", String.valueOf(ownTask));
            env.put("WORKSPACE_FOREIGN_TASK", String.valueOf(foreignTask));
            env.put("WORKSPACE_SOURCE_TASK", String.valueOf(sourceTask));
            env.put("WORKSPACE_TEACHER_ID", String.valueOf(teacherId));
            env.put("WORKSPACE_HISTORY", String.valueOf(historyId));
            env.put("WORKSPACE_CURRENT", String.valueOf(currentId));
            env.put("WORKSPACE_LAB_CODE", labCode);
            env.put("WORKSPACE_FIXTURES", directory.toString());
            env.put("WORKSPACE_TIMETABLE", importName);
            builder.redirectErrorStream(true)
                    .redirectOutput(directory.resolve("browser.log").toFile());
            Process process = builder.start();
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                fail("Browser test timed out: " + directory);
            }
            String log =
                    new String(
                            Files.readAllBytes(directory.resolve("browser.log")),
                            java.nio.charset.StandardCharsets.UTF_8);
            System.out.println(log);
            assertEquals(0, process.exitValue(), log);
            assertTrue(
                    jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM teaching_task WHERE course_id=?",
                                    Integer.class,
                                    courseId)
                            >= 5,
                    "Timetable upload and confirmation must persist real tasks");
        } finally {
            tx.execute(
                    status -> {
                        if (courseId > 0) {
                            jdbc.update(
                                    "UPDATE experiment_project p JOIN teaching_task t ON"
                                        + " t.id=p.task_id SET p.copied_from_id=NULL WHERE"
                                        + " t.course_id=?",
                                    courseId);
                            jdbc.update(
                                    "DELETE p FROM experiment_project p JOIN teaching_task t ON"
                                        + " t.id=p.task_id WHERE t.course_id=?",
                                    courseId);
                            jdbc.update(
                                    "DELETE s FROM schedule_detail s JOIN teaching_task t ON"
                                        + " t.id=s.task_id WHERE t.course_id=?",
                                    courseId);
                            jdbc.update(
                                    "DELETE a FROM teaching_task_teacher a JOIN teaching_task t ON"
                                        + " t.id=a.task_id WHERE t.course_id=?",
                                    courseId);
                            jdbc.update("DELETE FROM teaching_task WHERE course_id=?", courseId);
                            jdbc.update("DELETE FROM course WHERE id=?", courseId);
                        }
                        jdbc.update(
                                "DELETE r FROM teaching_import_row r JOIN teaching_import_batch b"
                                    + " ON b.id=r.batch_id WHERE b.file_name IN (?,?)",
                                importName,
                                "review-" + importName);
                        jdbc.update(
                                "DELETE FROM teaching_import_batch WHERE file_name IN (?,?)",
                                importName,
                                "review-" + importName);
                        jdbc.update("DELETE FROM laboratory WHERE id=?", labId);
                        jdbc.update("DELETE FROM academic_term WHERE id=?", historyId);
                        for (long id : new long[] {adminId, teacherId, strangerId}) {
                            jdbc.update("DELETE FROM token WHERE account_id=?", id);
                            jdbc.update("DELETE FROM account WHERE id=?", id);
                        }
                        return null;
                    });
        }
    }
}
