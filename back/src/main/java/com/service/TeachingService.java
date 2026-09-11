package com.service;

import com.exception.AccessException;
import com.security.TeachingAccess;
import com.utils.TeachingPasswords;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

@Service
public class TeachingService {

    private final JdbcTemplate jdbc;

    private final TeachingAccess access;

    private final TeachingTermService terms;

    public TeachingService(JdbcTemplate jdbc, TeachingAccess access, TeachingTermService terms) {
        this.jdbc = jdbc;
        this.access = access;
        this.terms = terms;
    }

    private static final String TASK_FROM =
            " FROM teaching_task t JOIN course c ON c.id=t.course_id "
                    + "JOIN academic_term a ON a.id=t.term_id ";

    private static final String TASK_SELECT =
            "SELECT t.*,c.course_code,t.course_name_snapshot"
                + " course_name,CONCAT(a.start_year,'-',a.start_year+1,'-',a.term_no)"
                + " term_name,a.status,a.start_year,a.term_no,COALESCE((SELECT"
                + " GROUP_CONCAT(j.display_name ORDER BY j.id SEPARATOR '、') FROM"
                + " teaching_task_teacher tt JOIN account j ON j.id=tt.teacher_account_id WHERE"
                + " tt.task_id=t.id),'') teacher_names,COALESCE((SELECT SUM(s.hours) FROM"
                + " schedule_detail s WHERE s.task_id=t.id),0) scheduled_hours,COALESCE((SELECT"
                + " GROUP_CONCAT(DISTINCT l.lab_name ORDER BY l.lab_name SEPARATOR '、') FROM"
                + " schedule_detail s JOIN laboratory l ON l.id=s.lab_id WHERE s.task_id=t.id),'')"
                + " lab_names,(SELECT COUNT(*) FROM experiment_project p WHERE p.task_id=t.id)"
                + " project_count";

    private String scope(Long teacher, String alias, List<Object> args) {
        if (teacher == null) {
            return "";
        }
        args.add(teacher);
        return " AND EXISTS (SELECT 1 FROM teaching_task_teacher own WHERE own.task_id="
                + alias
                + ".id AND own.teacher_account_id=?)";
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }

    public Map<String, Object> lookups(HttpServletRequest request) {
        Long teacher = access.teacherId(request);
        List<Object> args = new ArrayList<>();
        String filter = scope(teacher, "t", args);
        List<Map<String, Object>> courses =
                jdbc.queryForList(
                        "SELECT c.id,c.course_code,c.course_name FROM course c WHERE 1=1"
                                + (teacher == null
                                        ? ""
                                        : " AND EXISTS (SELECT 1 FROM teaching_task t WHERE"
                                                + " t.course_id=c.id"
                                                + filter
                                                + ")")
                                + " ORDER BY c.course_code",
                        args.toArray());
        return map(
                "terms",
                terms.terms(teacher),
                "teachers",
                teacherRows(teacher, ""),
                "labs",
                labRows(teacher, ""),
                "courses",
                courses);
    }

    public Map<String, Object> dashboard(HttpServletRequest request) {
        Long teacher = access.teacherId(request);
        List<Object> args = new ArrayList<>();
        String filter = scope(teacher, "t", args);
        long taskCount =
                count("SELECT COUNT(*) FROM teaching_task t WHERE 1=1" + filter, args.toArray());
        long courseCount =
                count(
                        "SELECT COUNT(DISTINCT t.course_id) FROM teaching_task t WHERE 1=1"
                                + filter,
                        args.toArray());
        long projectCount =
                count(
                        "SELECT COUNT(*) FROM experiment_project p JOIN teaching_task t ON"
                                + " t.id=p.task_id WHERE 1=1"
                                + filter,
                        args.toArray());
        List<Map<String, Object>> labs = labRows(teacher, "");
        List<Map<String, Object>> teachers = teacherRows(teacher, "");
        List<String> warnings = new ArrayList<>();
        long hoursMismatch =
                count(
                        "SELECT COUNT(*) FROM teaching_task t WHERE"
                                + " t.planned_lab_hours<>COALESCE((SELECT SUM(s.hours) FROM"
                                + " schedule_detail s WHERE s.task_id=t.id),0)"
                                + filter,
                        args.toArray());
        if (hoursMismatch > 0) {
            warnings.add(hoursMismatch + " 个教学任务的计划实验学时与实际排课学时不同；报表按实际排课统计。");
        }
        long missing =
                labs.stream()
                        .filter(
                                l ->
                                        l.get("manager_teacher_id") == null
                                                || l.get("equipment_count") == null)
                        .count();
        if (missing > 0) {
            warnings.add(missing + " 间实验室尚未补齐负责人或设备数量。");
        }
        if (teachers.stream().anyMatch(t -> Boolean.TRUE.equals(t.get("is_temporary")))) {
            warnings.add("部分教师使用 TMP 临时工号，待管理员核实真实工号。");
        }
        if (projectCount == 0) {
            warnings.add("尚未录入实验项目；请在当前学期任务中添加、导入或复制项目。");
        }
        return map(
                "counts",
                map(
                        "tasks",
                        taskCount,
                        "courses",
                        courseCount,
                        "labs",
                        labs.size(),
                        "teachers",
                        teachers.size(),
                        "projects",
                        projectCount,
                        "imports",
                        teacher == null ? count("SELECT COUNT(*) FROM teaching_import_batch") : 0),
                "currentTerm",
                terms.currentTerm(),
                "warnings",
                warnings);
    }

    public Map<String, Object> tasks(
            HttpServletRequest request, Long termId, String q, int page, int limit) {
        Long teacher = access.teacherId(request);
        if (page < 1 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("页码须大于0，每页数量为1至200");
        }
        List<Object> args = new ArrayList<>();
        String where = " WHERE 1=1" + scope(teacher, "t", args);
        if (termId != null) {
            where += " AND t.term_id=?";
            args.add(termId);
        }
        if (q != null && !q.trim().isEmpty()) {
            where +=
                    " AND (c.course_code LIKE ? OR t.course_name_snapshot LIKE ? OR"
                        + " t.class_composition LIKE ? OR t.task_code LIKE ? OR EXISTS (SELECT 1"
                        + " FROM teaching_task_teacher search_assignment JOIN account"
                        + " search_teacher ON"
                        + " search_teacher.id=search_assignment.teacher_account_id WHERE"
                        + " search_assignment.task_id=t.id AND search_teacher.display_name LIKE"
                        + " ?))";
            for (int i = 0; i < 5; i++) {
                args.add("%" + q.trim() + "%");
            }
        }
        long total = count("SELECT COUNT(*)" + TASK_FROM + where, args.toArray());
        args.add(limit);
        args.add(((long) page - 1) * limit);
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        TASK_SELECT
                                + TASK_FROM
                                + where
                                + " ORDER BY a.start_year DESC,a.term_no DESC,t.id DESC LIMIT ?"
                                + " OFFSET ?",
                        args.toArray());
        return map("list", rows, "total", total);
    }

    public Map<String, Object> task(HttpServletRequest request, long id) {
        access.requireTask(request, id, false);
        Map<String, Object> result =
                jdbc.queryForMap(TASK_SELECT + TASK_FROM + " WHERE t.id=?", id);
        result.put(
                "schedule",
                jdbc.queryForList(
                        "SELECT s.*,l.lab_code lab_code,l.lab_name lab_name FROM schedule_detail s"
                                + " JOIN laboratory l ON l.id=s.lab_id WHERE s.task_id=? ORDER BY"
                                + " s.teaching_week,s.weekday,s.period_start",
                        id));
        result.put(
                "teacher_ids",
                jdbc.queryForList(
                        "SELECT teacher_account_id FROM teaching_task_teacher WHERE task_id=? ORDER"
                                + " BY teacher_account_id",
                        Long.class,
                        id));
        result.put("editable", TeachingAccess.writable(result));
        return result;
    }

    @Transactional
    public Map<String, Object> createTask(HttpServletRequest request, Map<String, Object> body) {
        access.requireAdmin(request);
        long termId = positiveId(body, "termId"), courseId = positiveId(body, "courseId");
        List<Map<String, Object>> termRows =
                jdbc.queryForList(TeachingTermService.SELECT_TERMS + " WHERE a.id=?", termId);
        if (termRows.isEmpty() || !TeachingAccess.writable(termRows.get(0))) {
            throw new AccessException(403, "只能在当前开放学期创建教学任务");
        }
        List<Map<String, Object>> courses =
                jdbc.queryForList("SELECT course_name FROM course WHERE id=?", courseId);
        if (courses.isEmpty()) {
            throw new IllegalArgumentException("课程不存在");
        }
        Object teacherInput = body.get("teacherIds");
        if (!(teacherInput instanceof Collection) || ((Collection<?>) teacherInput).isEmpty()) {
            throw new IllegalArgumentException("请至少选择一位授课教师");
        }
        Set<Long> teacherIds = new LinkedHashSet<>();
        for (Object input : (Collection<?>) teacherInput) {
            long id = integer(input, "teacherIds", 1, Long.MAX_VALUE);
            if (!teacherIds.add(id)) {
                continue;
            }
            List<String> found =
                    jdbc.queryForList(
                            "SELECT display_name FROM account WHERE id=? AND role='TEACHER'",
                            String.class,
                            id);
            if (found.isEmpty()) {
                throw new IllegalArgumentException("所选教师不存在");
            }
        }
        if (body.get("labId") != null && !"".equals(body.get("labId"))) {
            throw new IllegalArgumentException("实验室由实际排课关联，请通过课表导入登记地点");
        }
        String classes = requiredText(body, "classComposition", 4000);
        String majors = optionalText(body, "majorComposition", 4000);
        int enrollment =
                (int) integer(body.get("enrollmentCount"), "enrollmentCount", 0, Integer.MAX_VALUE);
        BigDecimal hours =
                decimal(
                        body.get("plannedLabHours"),
                        "plannedLabHours",
                        new BigDecimal("0.01"),
                        new BigDecimal("999999.99"));
        String code =
                "T"
                        + UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 24)
                                .toUpperCase(Locale.ROOT);
        long id =
                insert(
                        "INSERT INTO"
                            + " teaching_task(task_code,term_id,course_id,course_name_snapshot,class_composition,major_composition,enrollment_count,planned_lab_hours)"
                            + " VALUES (?,?,?,?,?,?,?,?)",
                        code,
                        termId,
                        courseId,
                        courses.get(0).get("course_name"),
                        classes,
                        majors,
                        enrollment,
                        hours);
        for (Long teacher : teacherIds) {
            jdbc.update(
                    "INSERT INTO teaching_task_teacher(task_id,teacher_account_id) VALUES (?,?)",
                    id,
                    teacher);
        }
        return task(request, id);
    }

    public Map<String, Object> projects(HttpServletRequest request, long taskId) {
        Map<String, Object> task = access.requireTask(request, taskId, false);
        List<Map<String, Object>> list =
                jdbc.queryForList(
                        "SELECT * FROM experiment_project WHERE task_id=? ORDER BY sort_order,id",
                        taskId);
        return map("list", list, "total", list.size(), "editable", TeachingAccess.writable(task));
    }

    @Transactional
    public Map<String, Object> createProject(HttpServletRequest request, Map<String, Object> body) {
        long taskId = positiveId(body, "task_id");
        access.requireTask(request, taskId, true);
        validateProject(body);
        Long teacher = access.accountId(request);
        String code = optionalText(body, "project_code", 64);
        if (code.isEmpty()) {
            code = newProjectCode();
        }
        long id = insertProject(taskId, code, body, null, teacher);
        return jdbc.queryForMap("SELECT * FROM experiment_project WHERE id=?", id);
    }

    @Transactional
    public Map<String, Object> updateProject(
            HttpServletRequest request, long id, Map<String, Object> body) {
        Map<String, Object> original = projectAccess(request, id, true);
        if (body.containsKey("task_id")
                && positiveId(body, "task_id") != ((Number) original.get("task_id")).longValue()) {
            throw new IllegalArgumentException("不能更改项目所属教学任务，请使用复制功能");
        }
        if (body.containsKey("project_code")
                && !String.valueOf(original.get("project_code"))
                        .equals(String.valueOf(body.get("project_code")))) {
            throw new IllegalArgumentException("项目编号不可修改");
        }
        Map<String, Object> merged = new HashMap<>(original);
        merged.putAll(body);
        validateProject(merged);
        jdbc.update(
                "UPDATE experiment_project SET"
                    + " school_code=?,name=?,category_code=?,type_code=?,discipline_code=?,requirement_code=?,participant_type_code=?,group_size=?,hours=?,sort_order=?,updated_by_account_id=?"
                    + " WHERE id=?",
                merged.get("school_code"),
                merged.get("name"),
                merged.get("category_code"),
                merged.get("type_code"),
                merged.get("discipline_code"),
                merged.get("requirement_code"),
                merged.get("participant_type_code"),
                merged.get("group_size"),
                merged.get("hours"),
                merged.get("sort_order"),
                access.accountId(request),
                id);
        return jdbc.queryForMap("SELECT * FROM experiment_project WHERE id=?", id);
    }

    @Transactional
    public void deleteProject(HttpServletRequest request, long id) {
        projectAccess(request, id, true);
        if (count("SELECT COUNT(*) FROM experiment_project WHERE copied_from_id=?", id) > 0) {
            throw new IllegalArgumentException("该项目已有跨学期复制记录，无法删除，请保留原始项目");
        }
        jdbc.update("DELETE FROM experiment_project WHERE id=?", id);
    }

    @Transactional
    public Map<String, Object> copyProjects(
            HttpServletRequest request, long sourceTaskId, long targetTaskId) {
        if (sourceTaskId == targetTaskId) {
            throw new IllegalArgumentException("来源任务与目标任务不能相同");
        }
        Map<String, Object> source = access.requireTask(request, sourceTaskId, false);
        Map<String, Object> target = access.requireTask(request, targetTaskId, true);
        if (!source.get("course_id").equals(target.get("course_id"))) {
            throw new IllegalArgumentException("只能复制同一课程的实验项目");
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT * FROM experiment_project WHERE task_id=? ORDER BY sort_order,id",
                        sourceTaskId);
        Long teacher = access.accountId(request);
        int copied = 0, skipped = 0;
        for (Map<String, Object> row : rows) {
            long sourceId = ((Number) row.get("id")).longValue();
            if (count(
                            "SELECT COUNT(*) FROM experiment_project WHERE task_id=? AND"
                                    + " copied_from_id=?",
                            targetTaskId,
                            sourceId)
                    > 0) {
                skipped++;
                continue;
            }
            insertProject(targetTaskId, newProjectCode(), row, sourceId, teacher);
            copied++;
        }
        return map("copied", copied, "skipped", skipped, "source_total", rows.size());
    }

    private Map<String, Object> projectAccess(HttpServletRequest request, long id, boolean write) {
        // Scope before returning any row, and repeat task authorization before every mutation.
        Long teacher = access.teacherId(request);
        List<Object> args = new ArrayList<>();
        args.add(id);
        String filter = scope(teacher, "t", args);
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT p.* FROM experiment_project p JOIN teaching_task t ON"
                                + " t.id=p.task_id WHERE p.id=?"
                                + filter,
                        args.toArray());
        if (rows.isEmpty()) {
            throw new AccessException(404, "实验项目不存在或没有访问权限");
        }
        access.requireTask(request, ((Number) rows.get(0).get("task_id")).longValue(), write);
        return rows.get(0);
    }

    private long insertProject(
            long taskId, String code, Map<String, Object> body, Long copied, Long teacher) {
        return insert(
                "INSERT INTO"
                    + " experiment_project(task_id,project_code,school_code,name,category_code,type_code,discipline_code,requirement_code,participant_type_code,group_size,hours,sort_order,copied_from_id,created_by_account_id,updated_by_account_id)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                taskId,
                code,
                body.get("school_code"),
                body.get("name"),
                body.get("category_code"),
                body.get("type_code"),
                body.get("discipline_code"),
                body.get("requirement_code"),
                body.get("participant_type_code"),
                body.get("group_size"),
                body.get("hours"),
                body.get("sort_order"),
                copied,
                teacher,
                teacher);
    }

    private static String newProjectCode() {
        return "P" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    public static void validateProject(Map<String, Object> body) {
        for (String key : Arrays.asList("school_code", "name", "discipline_code")) {
            body.put(
                    key,
                    requiredText(
                            body,
                            key,
                            "name".equals(key) ? 50 : "school_code".equals(key) ? 5 : 16));
        }
        code(body, "category_code", "1", "2", "3", "4");
        code(body, "type_code", "1", "2", "3", "4", "5");
        code(body, "requirement_code", "1", "2", "3");
        code(body, "participant_type_code", "1", "2", "3", "4", "5");
        body.put("group_size", integer(body.get("group_size"), "group_size", 1, 99));
        body.put(
                "hours",
                decimal(
                        body.get("hours"),
                        "hours",
                        new BigDecimal("0.01"),
                        new BigDecimal("9999")));
        body.put(
                "sort_order",
                integer(body.getOrDefault("sort_order", 0), "sort_order", 0, Integer.MAX_VALUE));
    }

    public Map<String, Object> labs(HttpServletRequest request, String q) {
        List<Map<String, Object>> rows = labRows(access.teacherId(request), q);
        return map("list", rows, "total", rows.size());
    }

    private List<Map<String, Object>> labRows(Long teacher, String q) {
        List<Object> args = new ArrayList<>();
        String where = " WHERE 1=1";
        if (teacher != null) {
            where +=
                    " AND EXISTS (SELECT 1 FROM teaching_task t JOIN teaching_task_teacher own ON"
                        + " own.task_id=t.id WHERE own.teacher_account_id=? AND EXISTS (SELECT 1"
                        + " FROM schedule_detail s WHERE s.task_id=t.id AND s.lab_id=l.id))";
            args.add(teacher);
        }
        if (q != null && !q.trim().isEmpty()) {
            where += " AND (l.lab_code LIKE ? OR l.lab_name LIKE ? OR l.location LIKE ?)";
            for (int i = 0; i < 3; i++) {
                args.add("%" + q.trim() + "%");
            }
        }
        return jdbc.queryForList(
                "SELECT l.id,l.lab_code AS shiyanshibianhao,l.lab_name AS"
                    + " shiyanshimingcheng,l.location AS shiyanshiweizhi,l.manager_account_id AS"
                    + " manager_teacher_id,l.equipment_count,j.display_name manager_name FROM"
                    + " laboratory l LEFT JOIN account j ON j.id=l.manager_account_id"
                        + where
                        + " ORDER BY l.lab_code",
                args.toArray());
    }

    @Transactional
    public Map<String, Object> saveLab(
            HttpServletRequest request, Long id, Map<String, Object> body) {
        access.requireAdmin(request);
        String code = requiredText(body, "shiyanshibianhao", 32),
                name = requiredText(body, "shiyanshimingcheng", 100),
                location = optionalText(body, "shiyanshiweizhi", 200);
        Long manager = optionalId(body.get("manager_teacher_id"), "manager_teacher_id");
        Long equipment =
                body.get("equipment_count") == null || "".equals(body.get("equipment_count"))
                        ? null
                        : integer(
                                body.get("equipment_count"),
                                "equipment_count",
                                0,
                                Integer.MAX_VALUE);
        if (manager != null
                && count("SELECT COUNT(*) FROM account WHERE id=? AND role='TEACHER'", manager)
                        != 1) {
            throw new IllegalArgumentException("负责人教师不存在");
        }
        if (id == null) {
            id =
                    insert(
                            "INSERT INTO"
                                + " laboratory(lab_code,lab_name,location,manager_account_id,equipment_count,status)"
                                + " VALUES (?,?,?,?,?,?)",
                            code,
                            name,
                            location,
                            manager,
                            equipment,
                            "ACTIVE");
        } else {
            requireExists("laboratory", id, "实验室");
            jdbc.update(
                    "UPDATE laboratory SET"
                        + " lab_code=?,lab_name=?,location=?,manager_account_id=?,equipment_count=?"
                        + " WHERE id=?",
                    code,
                    name,
                    location,
                    manager,
                    equipment,
                    id);
        }
        return map("id", id);
    }

    @Transactional
    public void deleteLab(HttpServletRequest request, long id) {
        access.requireAdmin(request);
        requireExists("laboratory", id, "实验室");
        if (count("SELECT COUNT(*) FROM schedule_detail WHERE lab_id=?", id) > 0) {
            throw new IllegalArgumentException("实验室已有教学任务或排课，不能删除");
        }
        jdbc.update("DELETE FROM laboratory WHERE id=?", id);
    }

    public Map<String, Object> teachers(HttpServletRequest request, String q) {
        List<Map<String, Object>> rows = teacherRows(access.teacherId(request), q);
        return map("list", rows, "total", rows.size());
    }

    private List<Map<String, Object>> teacherRows(Long teacher, String q) {
        List<Object> args = new ArrayList<>();
        String where = " WHERE j.role='TEACHER'";
        if (teacher != null) {
            where +=
                    " AND (j.id=? OR EXISTS (SELECT 1 FROM teaching_task_teacher co JOIN"
                            + " teaching_task_teacher own ON own.task_id=co.task_id WHERE"
                            + " co.teacher_account_id=j.id AND own.teacher_account_id=?))";
            args.add(teacher);
            args.add(teacher);
        }
        if (q != null && !q.trim().isEmpty()) {
            where += " AND (j.username LIKE ? OR j.display_name LIKE ? OR j.college LIKE ?)";
            for (int i = 0; i < 3; i++) {
                args.add("%" + q.trim() + "%");
            }
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT j.id,j.username AS gonghao,j.display_name AS"
                                + " jiaoshixingming,j.college AS xueyuan FROM account j"
                                + where
                                + " ORDER BY j.username",
                        args.toArray());
        for (Map<String, Object> row : rows) {
            row.put(
                    "is_temporary",
                    String.valueOf(row.get("gonghao")).toUpperCase(Locale.ROOT).startsWith("TMP"));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> saveTeacher(
            HttpServletRequest request, Long id, Map<String, Object> body) {
        access.requireAdmin(request);
        String code = requiredText(body, "gonghao", 64),
                name = requiredText(body, "jiaoshixingming", 100),
                school = optionalText(body, "xueyuan", 100);
        if (id == null) {
            String password = TeachingPasswords.newPassword();
            id =
                    insert(
                            "INSERT INTO account(username,display_name,college,password_hash,role)"
                                    + " VALUES (?,?,?,?,'TEACHER')",
                            code,
                            name,
                            school,
                            TeachingPasswords.hash(password));
            return map("id", id, "password", password);
        }
        requireTeacher(id);
        jdbc.update(
                "UPDATE account SET username=?,display_name=?,college=? WHERE id=? AND"
                        + " role='TEACHER'",
                code,
                name,
                school,
                id);
        return map("id", id);
    }

    @Transactional
    public Map<String, Object> resetTeacherPassword(HttpServletRequest request, long id) {
        access.requireAdmin(request);
        requireTeacher(id);
        String password = TeachingPasswords.newPassword();
        jdbc.update(
                "UPDATE account SET password_hash=? WHERE id=? AND role='TEACHER'",
                TeachingPasswords.hash(password),
                id);
        jdbc.update("DELETE FROM token WHERE account_id=?", id);
        return map("password", password);
    }

    @Transactional
    public void deleteTeacher(HttpServletRequest request, long id) {
        access.requireAdmin(request);
        requireTeacher(id);
        if (count("SELECT COUNT(*) FROM teaching_task_teacher WHERE teacher_account_id=?", id) > 0
                || count("SELECT COUNT(*) FROM laboratory WHERE manager_account_id=?", id) > 0
                || count(
                                "SELECT COUNT(*) FROM experiment_project WHERE"
                                        + " created_by_account_id=? OR updated_by_account_id=?",
                                id,
                                id)
                        > 0) {
            throw new IllegalArgumentException("教师已关联任务、实验室或项目记录，不能删除");
        }
        jdbc.update("DELETE FROM token WHERE account_id=?", id);
        jdbc.update("DELETE FROM account WHERE id=? AND role='TEACHER'", id);
    }

    @Transactional
    public void changePassword(HttpServletRequest request, Map<String, Object> body) {
        long id = access.accountId(request);
        String oldPassword = String.valueOf(body.getOrDefault("oldPassword", ""));
        String password = String.valueOf(body.getOrDefault("newPassword", ""));
        if (password.length() < 8
                || password.length() > 64
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("新密码长度应为8至64位，UTF-8编码不超过72字节");
        }
        if (password.equals(oldPassword)) {
            throw new IllegalArgumentException("新密码不能与原密码相同");
        }
        String stored =
                jdbc.queryForObject(
                        "SELECT password_hash FROM account WHERE id=?", String.class, id);
        if (!TeachingPasswords.matches(oldPassword, stored)) {
            throw new IllegalArgumentException("原密码不正确");
        }
        jdbc.update(
                "UPDATE account SET password_hash=? WHERE id=?",
                TeachingPasswords.hash(password),
                id);
        String token = request.getHeader("Token");
        if (token == null) {
            jdbc.update("DELETE FROM token WHERE account_id=?", id);
        } else {
            jdbc.update("DELETE FROM token WHERE account_id=? AND token<>?", id, token);
        }
    }

    private void requireTeacher(long id) {
        if (count("SELECT COUNT(*) FROM account WHERE id=? AND role='TEACHER'", id) != 1) {
            throw new AccessException(404, "教师不存在");
        }
    }

    private void requireExists(String table, long id, String label) {
        if (count("SELECT COUNT(*) FROM " + table + " WHERE id=?", id) != 1) {
            throw new AccessException(404, label + "不存在");
        }
    }

    private long insert(String sql, Object... values) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    PreparedStatement p =
                            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < values.length; i++) {
                        p.setObject(i + 1, values[i]);
                    }
                    return p;
                },
                holder);
        if (holder.getKey() == null) {
            throw new IllegalStateException("未获取到新增记录编号");
        }
        return holder.getKey().longValue();
    }

    public static long positiveId(Map<String, Object> body, String key) {
        return integer(body.get(key), key, 1, Long.MAX_VALUE);
    }

    private static Long optionalId(Object value, String key) {
        return value == null || "".equals(value) ? null : integer(value, key, 1, Long.MAX_VALUE);
    }

    private static long integer(Object value, String key, long min, long max) {
        long number;
        try {
            number = new BigDecimal(String.valueOf(value)).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException(key + " 必须为 " + min + " 至 " + max + " 的整数", e);
        }
        if (number < min || number > max) {
            throw new IllegalArgumentException(key + " 必须为 " + min + " 至 " + max + " 的整数");
        }
        return number;
    }

    private static BigDecimal decimal(Object value, String key, BigDecimal min, BigDecimal max) {
        BigDecimal number;
        try {
            number = new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " 必须在 " + min + " 至 " + max + " 之间且最多两位小数", e);
        }
        if (number.compareTo(min) < 0
                || number.compareTo(max) > 0
                || number.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(key + " 必须在 " + min + " 至 " + max + " 之间且最多两位小数");
        }
        return number;
    }

    private static String requiredText(Map<String, Object> body, String key, int max) {
        String value = optionalText(body, key, max);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return value;
    }

    private static String optionalText(Map<String, Object> body, String key, int max) {
        Object raw = body.get(key);
        if (raw == null) {
            return "";
        }
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException(key + " 必须为文本");
        }
        String value = ((String) raw).trim();
        if (value.length() > max) {
            throw new IllegalArgumentException(key + " 长度不能超过 " + max);
        }
        return value;
    }

    private static void code(Map<String, Object> body, String key, String... allowed) {
        String value = String.valueOf(body.get(key));
        if (!Arrays.asList(allowed).contains(value)) {
            throw new IllegalArgumentException(key + " 代码无效");
        }
        body.put(key, value);
    }
}
