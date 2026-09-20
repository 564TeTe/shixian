package com.service;

import static com.utils.TeachingExcel.map;

import com.security.TeachingAccess;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/** Read model for the prototype workspace, with ownership enforced before loading records. */
@Service
public class WorkspaceService {
    private final JdbcTemplate jdbc;
    private final TeachingAccess access;
    private final TeachingService teaching;
    private final TeachingTermService terms;

    public WorkspaceService(
            JdbcTemplate jdbc,
            TeachingAccess access,
            TeachingService teaching,
            TeachingTermService terms) {
        this.jdbc = jdbc;
        this.access = access;
        this.teaching = teaching;
        this.terms = terms;
    }

    public Map<String, Object> load(HttpServletRequest request) {
        Long teacher = access.teacherId(request);
        long accountId = access.accountId(request);
        String scope =
                teacher == null
                        ? ""
                        : " WHERE EXISTS (SELECT 1 FROM teaching_task_teacher own WHERE"
                              + " own.task_id=t.id AND own.teacher_account_id=?)";
        Object[] args = teacher == null ? new Object[0] : new Object[] {teacher};
        Map<String, Object> lookups = teaching.lookups(request);
        List<Map<String, Object>> tasks =
                jdbc.queryForList(
                        "SELECT t.*,c.course_code,a.start_year,a.term_no,a.status,COALESCE((SELECT"
                            + " GROUP_CONCAT(ac.display_name ORDER BY ac.id SEPARATOR '、') FROM"
                            + " teaching_task_teacher tt JOIN account ac ON"
                            + " ac.id=tt.teacher_account_id WHERE tt.task_id=t.id),'')"
                            + " teacher_names FROM teaching_task t JOIN course c ON"
                            + " c.id=t.course_id JOIN academic_term a ON a.id=t.term_id"
                                + scope
                                + " ORDER BY t.id",
                        args);
        List<Map<String, Object>> projects =
                jdbc.queryForList(
                        "SELECT p.* FROM experiment_project p JOIN teaching_task t ON"
                            + " t.id=p.task_id"
                                + scope
                                + " ORDER BY p.sort_order,p.id",
                        args);
        List<Map<String, Object>> schedule =
                jdbc.queryForList(
                        "SELECT s.* FROM schedule_detail s JOIN teaching_task t ON t.id=s.task_id"
                                + scope
                                + " ORDER BY s.teaching_week,s.weekday,s.period_start",
                        args);
        List<Map<String, Object>> assignments =
                jdbc.queryForList(
                        "SELECT tt.task_id,tt.teacher_account_id FROM teaching_task_teacher tt JOIN"
                            + " teaching_task t ON t.id=tt.task_id"
                                + scope,
                        args);
        return map(
                "account",
                jdbc.queryForMap(
                        "SELECT id,username,display_name,role,college FROM account WHERE id=?",
                        accountId),
                "terms",
                lookups.get("terms"),
                "currentTerm",
                terms.currentTerm(),
                "teachers",
                lookups.get("teachers"),
                "labs",
                lookups.get("labs"),
                "tasks",
                tasks,
                "projects",
                projects,
                "schedule",
                schedule,
                "assignments",
                assignments);
    }
}
