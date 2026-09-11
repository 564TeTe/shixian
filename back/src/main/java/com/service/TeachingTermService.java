package com.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class TeachingTermService {

    private final JdbcTemplate jdbc;

    // The frontend uses academic_year_id as a grouping key; the year itself is now that stable key.
    public static final String SELECT_TERMS =
            "SELECT a.id,a.start_year"
                + " academic_year_id,a.term_no,a.starts_on,a.ends_on,a.status,CONCAT(a.start_year,'-',a.start_year+1)"
                + " academic_year_name,a.start_year,CONCAT(a.start_year,'-',a.start_year+1,'-',a.term_no)"
                + " name FROM academic_term a";

    public TeachingTermService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Autumn starts August 31; spring starts February 1 in the following year. */
    public static int[] calendarTerm(LocalDate date) {
        LocalDate autumn = LocalDate.of(date.getYear(), 8, 31);
        if (!date.isBefore(autumn)) {
            return new int[] {date.getYear(), 1};
        }
        return new int[] {date.getYear() - 1, date.getMonthValue() == 1 ? 1 : 2};
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void automaticGeneration() {
        ensureCurrentTerm();
    }

    @Transactional
    public Map<String, Object> ensureCurrentTerm() {
        int[] period = calendarTerm(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        int year = period[0];
        for (int term = 1; term <= 2; term++) {
            LocalDate starts = term == 1 ? LocalDate.of(year, 8, 31) : LocalDate.of(year + 1, 2, 1);
            LocalDate ends =
                    term == 1 ? LocalDate.of(year + 1, 1, 31) : LocalDate.of(year + 1, 8, 30);
            String status = term == period[1] ? "OPEN" : term < period[1] ? "ARCHIVED" : "DRAFT";
            jdbc.update(
                    "INSERT INTO academic_term(start_year,term_no,starts_on,ends_on,status) VALUES"
                        + " (?,?,?,?,?) ON DUPLICATE KEY UPDATE"
                        + " starts_on=COALESCE(starts_on,VALUES(starts_on)),ends_on=COALESCE(ends_on,VALUES(ends_on)),status=VALUES(status)",
                    year,
                    term,
                    Date.valueOf(starts),
                    Date.valueOf(ends),
                    status);
        }
        jdbc.update(
                "UPDATE academic_term SET status='ARCHIVED' "
                        + "WHERE start_year<? OR (start_year=? AND term_no<?)",
                year,
                year,
                period[1]);
        return currentTerm();
    }

    public Map<String, Object> currentTerm() {
        int[] period = calendarTerm(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        SELECT_TERMS + " WHERE a.start_year=? AND a.term_no=?",
                        period[0],
                        period[1]);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> terms(Long teacherId) {
        if (teacherId == null) {
            return jdbc.queryForList(SELECT_TERMS + " ORDER BY a.start_year DESC,a.term_no DESC");
        }
        int[] period = calendarTerm(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        return jdbc.queryForList(
                SELECT_TERMS
                        + " WHERE (a.start_year=? AND a.term_no=?) OR EXISTS (SELECT 1 FROM"
                        + " teaching_task t JOIN teaching_task_teacher tt ON tt.task_id=t.id WHERE"
                        + " t.term_id=a.id AND tt.teacher_account_id=?) ORDER BY a.start_year"
                        + " DESC,a.term_no DESC",
                period[0],
                period[1],
                teacherId);
    }
}
