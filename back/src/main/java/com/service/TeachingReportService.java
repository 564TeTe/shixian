package com.service;

import static com.utils.TeachingExcel.map;

import com.security.TeachingAccess;
import com.utils.TeachingExcel;
import com.utils.TeachingProjectReport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

@Service
public class TeachingReportService {

    private final JdbcTemplate db;

    private final TeachingAccess access;

    public static final String BASIS =
            "教学人时＝实验室实际排课学时×任务选课人数。项目按教学任务关联的排课实验室筛选，同一项目在全部实验室汇总中只列一次；关联地点不代表项目实际执行地点。实验者人数只使用已确认的项目参与人数，未采集时留空；实验总学时取课程任务的计划实验学时。未排课任务不计入实验室统计和采集表。";

    public TeachingReportService(JdbcTemplate db, TeachingAccess access) {
        this.db = db;
        this.access = access;
    }

    public Map<String, Object> report(HttpServletRequest request, Long yearId, Long termId) {
        return report(request, yearId, termId, null);
    }

    public Map<String, Object> report(
            HttpServletRequest request, Long yearId, Long termId, Long labId) {
        Long teacherId = access.teacherId(request);
        if (labId != null && labId <= 0) {
            throw new IllegalArgumentException("实验室编号无效");
        }
        List<Object> args = new ArrayList<>();
        StringBuilder filter = new StringBuilder(" WHERE 1=1");
        if (yearId != null) {
            filter.append(" AND tm.start_year=?");
            args.add(yearId);
        }
        if (termId != null) {
            filter.append(" AND tm.id=?");
            args.add(termId);
        }
        if (teacherId != null) {
            filter.append(
                    " AND EXISTS (SELECT 1 FROM teaching_task_teacher scope WHERE"
                            + " scope.task_id=t.id AND scope.teacher_account_id=?)");
            args.add(teacherId);
        }
        String joins = " FROM teaching_task t JOIN academic_term tm ON tm.id=t.term_id";
        List<Object> labArgs = new ArrayList<>(args);
        if (labId != null) labArgs.add(labId);
        List<Map<String, Object>> perTaskLab =
                db.queryForList(
                        "SELECT l.id lab_id,l.lab_code,l.lab_name,stats.task_id,stats.course_id,"
                            + "stats.enrollment_count,stats.scheduled_hours FROM laboratory l"
                            + " LEFT JOIN (SELECT s.lab_id,t.id task_id,t.course_id,"
                            + "t.enrollment_count,SUM(s.hours) scheduled_hours"
                                + joins
                                + " JOIN schedule_detail s ON s.task_id=t.id"
                                + filter
                                + " GROUP BY s.lab_id,t.id) stats ON stats.lab_id=l.id WHERE 1=1"
                                + (labId == null ? "" : " AND l.id=?")
                                + (teacherId == null ? "" : " AND stats.task_id IS NOT NULL")
                                + " ORDER BY l.lab_code,stats.task_id",
                        labArgs.toArray());
        // EXISTS avoids duplicating a project for every schedule or associated laboratory.
        filter.append(" AND EXISTS (SELECT 1 FROM schedule_detail selected_lab"
                + " WHERE selected_lab.task_id=t.id");
        if (labId != null) {
            filter.append(" AND selected_lab.lab_id=?");
            args.add(labId);
        }
        filter.append(")");
        List<Map<String, Object>> projects =
                db.queryForList(
                        "SELECT CONCAT(tm.start_year,'-',tm.start_year+1,' 第',tm.term_no,'学期')"
                            + " term_name,c.course_code,t.course_name_snapshot"
                            + " course_name,COALESCE((SELECT GROUP_CONCAT(DISTINCT l.lab_name ORDER"
                            + " BY l.lab_name SEPARATOR '、') FROM schedule_detail sd JOIN"
                            + " laboratory l ON l.id=sd.lab_id WHERE sd.task_id=t.id),'未登记')"
                            + " lab_names,p.id project_id,p.project_code,p.name"
                            + " project_name,p.hours,t.enrollment_count,p.participant_count,"
                            + "t.planned_lab_hours,COALESCE((SELECT GROUP_CONCAT(a.display_name"
                            + " ORDER BY a.id SEPARATOR '、') FROM teaching_task_teacher tt"
                            + " JOIN account a ON a.id=tt.teacher_account_id WHERE tt.task_id=t.id),'')"
                            + " teacher_names,p.school_code,p.category_code,p.type_code,p.discipline_code,p.requirement_code,p.participant_type_code,p.group_size,t.task_code"
                                + joins
                                + " JOIN course c ON c.id=t.course_id JOIN experiment_project p ON"
                                + " p.task_id=t.id"
                                + filter
                                + " ORDER BY"
                                + " tm.start_year,tm.term_no,c.course_code,t.id,p.sort_order,p.id",
                        args.toArray());
        List<Map<String, Object>> differences =
                db.queryForList(
                        "SELECT t.task_code,t.planned_lab_hours,COALESCE((SELECT SUM(s.hours) FROM"
                                + " schedule_detail s WHERE s.task_id=t.id),0) scheduled_hours"
                                + joins
                                + filter
                                + " AND t.planned_lab_hours<>COALESCE((SELECT SUM(s.hours) FROM"
                                + " schedule_detail s WHERE s.task_id=t.id),0) ORDER BY t.id",
                        args.toArray());
        List<String> warnings = new ArrayList<>();
        for (Map<String, Object> item : differences) {
            warnings.add(
                    item.get("task_code")
                            + "：计划 "
                            + item.get("planned_lab_hours")
                            + " 学时，排课 "
                            + item.get("scheduled_hours")
                            + " 学时");
        }
        return map(
                "labs",
                aggregateLabs(perTaskLab),
                "projects",
                projects,
                "basis",
                BASIS,
                "warnings",
                warnings);
    }

    static List<Map<String, Object>> aggregateLabs(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Map<String, Set<String>> courses = new HashMap<>(), tasks = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String code = row.get("lab_code").toString();
            Map<String, Object> total =
                    result.computeIfAbsent(
                            code,
                            k ->
                                    map(
                                            "lab_code",
                                            code,
                                            "lab_id",
                                            row.get("lab_id"),
                                            "lab_name",
                                            row.get("lab_name"),
                                            "course_count",
                                            0,
                                            "task_count",
                                            0,
                                            "scheduled_hours",
                                            BigDecimal.ZERO,
                                            "person_hours",
                                            BigDecimal.ZERO));
            if (row.get("task_id") == null) continue;
            BigDecimal hours = new BigDecimal(row.get("scheduled_hours").toString()),
                    people = new BigDecimal(row.get("enrollment_count").toString());
            total.put("scheduled_hours", ((BigDecimal) total.get("scheduled_hours")).add(hours));
            total.put(
                    "person_hours",
                    ((BigDecimal) total.get("person_hours")).add(hours.multiply(people)));
            courses.computeIfAbsent(code, k -> new HashSet<>())
                    .add(row.get("course_id").toString());
            tasks.computeIfAbsent(code, k -> new HashSet<>()).add(row.get("task_id").toString());
            total.put("course_count", courses.get(code).size());
            total.put("task_count", tasks.get(code).size());
        }
        return new ArrayList<>(result.values());
    }

    public byte[] export(HttpServletRequest request, Long yearId, Long termId, String type) {
        return export(request, yearId, termId, null, type);
    }

    @SuppressWarnings("unchecked")
    public byte[] export(
            HttpServletRequest request, Long yearId, Long termId, Long labId, String type) {
        if (!"labs".equals(type) && !"projects".equals(type)) {
            throw new IllegalArgumentException("导出类型只能为labs或projects");
        }
        Map<String, Object> report = report(request, yearId, termId, labId);
        if ("projects".equals(type)) {
            return TeachingProjectReport.workbook(
                    (List<Map<String, Object>>) report.get("projects"));
        }
        String[] columns = {"lab_code", "lab_name", "course_count", "task_count", "scheduled_hours", "person_hours"};
        String[] headers = {"实验室编号", "实验室名称", "课程数", "教学任务数", "排课学时", "排课人时"};
        List<List<?>> rows = new ArrayList<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) report.get(type)) {
            List<Object> cells = new ArrayList<>();
            for (String col : columns) {
                cells.add(row.get(col));
            }
            rows.add(cells);
        }
        return TeachingExcel.workbook("实验室统计", headers, rows, BASIS);
    }
}
