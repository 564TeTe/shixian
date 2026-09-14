package com.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** Allocate project numbers under a laboratory row lock inside the caller's transaction. */
@Service
public class ProjectCodeService {
    private final JdbcTemplate jdbc;

    public ProjectCodeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next(long taskId) {
        List<Long> labs =
                jdbc.queryForList(
                        "SELECT DISTINCT l.id FROM laboratory l JOIN schedule_detail s ON"
                            + " s.lab_id=l.id WHERE s.task_id=? ORDER BY l.id",
                        Long.class,
                        taskId);
        if (labs.isEmpty()) {
            throw new IllegalArgumentException("任务尚未关联实验室，请先由管理员导入实际课表");
        }
        String labCode =
                jdbc.queryForObject(
                        "SELECT lab_code FROM laboratory WHERE id=? FOR UPDATE",
                        String.class,
                        labs.get(0));
        String prefix = labCode + "-";
        List<String> codes =
                jdbc.queryForList(
                        "SELECT project_code FROM experiment_project WHERE LEFT(project_code,?)=?"
                            + " FOR UPDATE",
                        String.class,
                        prefix.length(),
                        prefix);
        int maximum = 0;
        for (String code : codes) {
            String suffix = code.substring(prefix.length());
            if (suffix.matches("[0-9]{3}")) {
                maximum = Math.max(maximum, Integer.parseInt(suffix));
            }
        }
        if (maximum >= 999) {
            throw new IllegalArgumentException("该实验室三位项目编号已用完，本次操作未保存");
        }
        return prefix + String.format(Locale.ROOT, "%03d", maximum + 1);
    }
}
