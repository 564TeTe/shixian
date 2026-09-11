package com.security;

import com.exception.AccessException;
import com.service.TeachingTermService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/** Shared authorization for all teaching endpoints. A missing scope never means admin. */
@Component
public class TeachingAccess {

    private final JdbcTemplate jdbc;

    public TeachingAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long teacherId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AccessException(401, "请先登录");
        }
        String table = String.valueOf(session.getAttribute("tableName"));
        if (!"users".equals(table) && !"teacher".equals(table)) {
            throw new AccessException(403, "当前账号没有教学管理权限");
        }
        long id;
        try {
            id = Long.parseLong(String.valueOf(session.getAttribute("userId")));
        } catch (NumberFormatException e) {
            throw new AccessException(401, "登录状态无效，请重新登录");
        }
        if (id <= 0) {
            throw new AccessException(401, "登录状态无效，请重新登录");
        }
        String role = "users".equals(table) ? "ADMIN" : "TEACHER";
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM account WHERE id=? AND role=?", Long.class, id, role);
        if (count == null || count != 1) {
            throw new AccessException(401, "账号不存在，请重新登录");
        }
        return "users".equals(table) ? null : id;
    }

    public long accountId(HttpServletRequest request) {
        teacherId(request);
        return Long.parseLong(String.valueOf(request.getSession(false).getAttribute("userId")));
    }

    public void requireAdmin(HttpServletRequest request) {
        if (teacherId(request) != null) {
            throw new AccessException(403, "仅管理员可以执行此操作");
        }
    }

    public Map<String, Object> requireTask(HttpServletRequest request, long taskId, boolean write) {
        Long teacher = teacherId(request);
        String sql =
                "SELECT t.*,a.status,a.term_no,a.start_year FROM teaching_task t "
                        + "JOIN academic_term a ON a.id=t.term_id WHERE t.id=?";
        List<Map<String, Object>> rows =
                teacher == null
                        ? jdbc.queryForList(sql, taskId)
                        : jdbc.queryForList(
                                sql
                                        + " AND EXISTS (SELECT 1 FROM teaching_task_teacher own"
                                        + " WHERE own.task_id=t.id AND own.teacher_account_id=?)",
                                taskId,
                                teacher);
        if (rows.isEmpty()) {
            throw new AccessException(404, "教学任务不存在或没有访问权限");
        }
        Map<String, Object> task = rows.get(0);
        if (write && !writable(task)) {
            throw new AccessException(403, "仅当前开放学期可编辑，历史或未开放学期只读");
        }
        return task;
    }

    public static boolean writable(Map<String, Object> termOrTask) {
        if (!"OPEN".equals(termOrTask.get("status"))) {
            return false;
        }
        int[] current = TeachingTermService.calendarTerm(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        return termOrTask.get("start_year") instanceof Number
                && termOrTask.get("term_no") instanceof Number
                && ((Number) termOrTask.get("start_year")).intValue() == current[0]
                && ((Number) termOrTask.get("term_no")).intValue() == current[1];
    }
}
