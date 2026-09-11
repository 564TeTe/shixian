package com.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.*;
import static com.teaching.TeachingExcel.*;

/** Opt-in integration tests; all temporary business rows are rolled back. */
@EnabledIfEnvironmentVariable(named="TEACHING_DB_TEST",matches="1")
class TeachingImportDatabaseTest {
    JdbcTemplate db; DataSourceTransactionManager manager; TransactionStatus tx;
    TeachingImportService imports; TeachingReportService reports; MockHttpServletRequest admin;
    @BeforeEach void begin() {
        DriverManagerDataSource ds=new DriverManagerDataSource(System.getenv("TEACHING_TEST_DB_URL"),System.getenv("TEACHING_TEST_DB_USER"),System.getenv("TEACHING_TEST_DB_PASSWORD"));
        db=new JdbcTemplate(ds); manager=new DataSourceTransactionManager(ds); tx=manager.getTransaction(new DefaultTransactionDefinition());
        TeachingAccess access=new TeachingAccess(db); imports=new TeachingImportService(db,new ObjectMapper(),access); reports=new TeachingReportService(db,access);
        admin=request("users",db.queryForObject("SELECT MIN(id) FROM users",Long.class));
    }
    @AfterEach void rollback() { if(tx!=null) manager.rollback(tx); }
    private MockHttpServletRequest request(String table,long id) { MockHttpServletRequest r=new MockHttpServletRequest();r.getSession().setAttribute("tableName",table);r.getSession().setAttribute("userId",id);return r; }
    @Test void timetableImportGeneratesMappedBusinessDataAndRequiresConfirmationForSecondFile() {
        String unique=UUID.randomUUID().toString().substring(0,8),name="导入验证甲"+unique,second="导入验证乙"+unique;
        int[] current=TeachingTermService.calendarTerm(LocalDate.now());
        List<Object> row=new ArrayList<>(Arrays.asList(current[0]+"-"+(current[0]+1),current[1],"验证学院","IMPTEST"+unique,"导入验证课程",1,"测试班"+unique,30,name+"、"+second,2,"1-2周",8,"TEST-LAB-"+unique,"测试专业",25,1,2,"1-2周","2027-01-01","2","星期一第1-2节{1-2周}"));
        byte[] first=workbook("课表",TIMETABLE_HEADERS,Collections.singletonList(row),null);
        Map<String,Object> imported=imports.timetable(admin,first,"test.xlsx"); long batch=((Number)imported.get("batchId")).longValue();
        assertEquals(1,((Number)imported.get("promoted")).intValue());
        long taskId=db.queryForObject("SELECT t.id FROM teaching_task t JOIN teaching_import_row r ON r.id=t.source_import_row_id WHERE r.batch_id=?",Long.class,batch);
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM teaching_task_teacher WHERE task_id=?",Integer.class,taskId));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM schedule_detail WHERE task_id=?",Integer.class,taskId));
        Map<String,Object> teacher=db.queryForMap("SELECT teacher_no,password FROM teacher WHERE teacher_name=?",name);
        assertTrue(teacher.get("teacher_no").toString().startsWith("TMP")); assertTrue(teacher.get("password").toString().startsWith("$2"));
        assertNull(db.queryForObject("SELECT equipment_count FROM laboratory WHERE lab_code=?",Integer.class,"TEST-LAB-"+unique));
        assertEquals(batch,imports.timetable(admin,first,"renamed.xlsx").get("batchId"));
        row.set(13,"改动专业"); byte[] secondFile=workbook("课表",TIMETABLE_HEADERS,Collections.singletonList(row),null);
        Map<String,Object> review=imports.timetable(admin,secondFile,"second.xlsx");
        assertEquals(0,((Number)review.get("promoted")).intValue()); assertEquals(1,((Number)review.get("review_count")).intValue());
        long reviewId=((Number)review.get("batchId")).longValue(); Map<String,Object> confirmed=imports.confirm(admin,reviewId,2,"课表",true);
        assertNotEquals(taskId,((Number)confirmed.get("task_id")).longValue());
        assertEquals(confirmed.get("task_id"),imports.confirm(admin,reviewId,2,"课表",true).get("task_id"));
        List<?> projectRow=Arrays.asList("11059","","真实项目"+unique,"3","2","0809","1","3","",2,2,"IMPTEST"+unique,"导入验证课程",8,name);
        Map<String,Object> projects=imports.projects(admin,taskId,workbook("真实项目",PROJECT_HEADERS,Collections.singletonList(projectRow),"填写说明"));
        assertEquals(1,projects.get("imported"));
        assertEquals("0809",db.queryForObject("SELECT discipline_code FROM experiment_project WHERE task_id=?",String.class,taskId));
        assertThrows(IllegalArgumentException.class,()->imports.projects(admin,taskId,workbook("真实项目",PROJECT_HEADERS,Collections.singletonList(projectRow),null)));
    }
    @Test void teacherReportTotalsMatchOnlyTheirOwnScheduleRows() {
        long teacher=db.queryForObject("SELECT MIN(teacher_id) FROM teaching_task_teacher",Long.class);
        Map<String,Object> report=reports.report(request("teacher",teacher),null,null);
        BigDecimal expected=db.queryForObject("SELECT COALESCE(SUM(s.hours*t.enrollment_count),0) FROM schedule_detail s JOIN teaching_task t ON t.id=s.task_id WHERE EXISTS(SELECT 1 FROM teaching_task_teacher x WHERE x.task_id=t.id AND x.teacher_id=?)",BigDecimal.class,teacher);
        BigDecimal actual=BigDecimal.ZERO;
        for(Object row:(List<?>)report.get("labs")) actual=actual.add((BigDecimal)((Map<?,?>)row).get("person_hours"));
        assertEquals(0,expected.compareTo(actual)); assertTrue(report.get("basis").toString().contains("课程关联地点"));
        assertTrue(reports.export(request("teacher",teacher),null,null,"labs").length>100);
    }
    @Test void projectTemplateContainsNoRealDataAndOriginalTimetableParses() throws Exception {
        String template=System.getenv("TEACHING_PROJECT_FIXTURE"),timetable=System.getenv("TEACHING_TIMETABLE_FIXTURE");
        Assumptions.assumeTrue(template!=null && timetable!=null);
        assertTrue(tableRows(Files.readAllBytes(Paths.get(template)),PROJECT_HEADERS,true).isEmpty());
        List<ExcelRow> rows=tableRows(Files.readAllBytes(Paths.get(timetable)),TIMETABLE_HEADERS,false);
        assertEquals(255,rows.size()); int expanded=0;
        for(ExcelRow row:rows) { expanded+=parseSchedule(row.at("教学地点"),row.at("上课时间")).size(); assertNotNull(dateTime(row.at("课程结束时间"))); }
        assertEquals(2208,expanded);
    }
}
