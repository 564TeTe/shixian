package com.teaching;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against a provisioned local teaching database; every test rolls back all writes. */
@EnabledIfEnvironmentVariable(named="TEACHING_DB_TEST",matches="1")
class TeachingCoreDatabaseTest {
    private JdbcTemplate jdbc;
    private TeachingService service;
    private TeachingTermService terms;
    private DataSourceTransactionManager manager;
    private TransactionStatus transaction;
    private MockHttpServletRequest admin;
    private long teacherId, otherTeacherId, courseId, labId, currentId;

    @BeforeEach void begin() {
        DriverManagerDataSource ds=new DriverManagerDataSource(System.getenv("TEACHING_TEST_DB_URL"),
            System.getenv("TEACHING_TEST_DB_USER"),System.getenv("TEACHING_TEST_DB_PASSWORD"));
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        transaction=manager.getTransaction(new DefaultTransactionDefinition());
        TeachingAccess access=new TeachingAccess(jdbc);terms=new TeachingTermService(jdbc);
        service=new TeachingService(jdbc,access,terms);
        admin=request("users",jdbc.queryForObject("SELECT MIN(id) FROM users",Long.class));
        List<Long> teachers=jdbc.queryForList("SELECT id FROM jiaoshi ORDER BY id LIMIT 2",Long.class);
        teacherId=teachers.get(0);otherTeacherId=teachers.get(1);
        courseId=jdbc.queryForObject("SELECT MIN(id) FROM course",Long.class);
        labId=jdbc.queryForObject("SELECT MIN(id) FROM shiyanshixinxi",Long.class);
        currentId=((Number)terms.ensureCurrentTerm().get("id")).longValue();
    }
    @AfterEach void rollback(){if(transaction!=null)manager.rollback(transaction);}
    private MockHttpServletRequest request(String table,long id){
        MockHttpServletRequest r=new MockHttpServletRequest();r.getSession().setAttribute("tableName",table);r.getSession().setAttribute("userId",id);return r;
    }
    private Map<String,Object> taskInput(){
        Map<String,Object> p=new HashMap<>();p.put("termId",currentId);p.put("courseId",courseId);
        p.put("teacherIds",Collections.singletonList(teacherId));p.put("labId",labId);
        p.put("classComposition","自动化事务测试班");p.put("enrollmentCount",30);p.put("plannedLabHours",12);return p;
    }
    private Map<String,Object> projectInput(long task){
        Map<String,Object> p=new HashMap<>();p.put("task_id",task);p.put("school_code","TEST");p.put("name","事务测试项目");
        p.put("category_code","1");p.put("type_code","2");p.put("discipline_code","0809");p.put("requirement_code","1");
        p.put("participant_type_code","1");p.put("group_size",2);p.put("hours",2);return p;
    }
    @Test void manuallyCreatedTaskHasNoInventedScheduleAndTeacherScopeIsEnforced(){
        Map<String,Object> task=service.createTask(admin,taskInput());long id=((Number)task.get("id")).longValue();
        assertEquals(0,((Number)task.get("scheduled_hours")).intValue());
        assertEquals(0,((List<?>)task.get("schedule")).size());
        assertEquals(labId,((Number)task.get("default_lab_id")).longValue());
        assertFalse(String.valueOf(task.get("lab_names")).isEmpty());
        assertEquals(id,((Number)service.task(request("jiaoshi",teacherId),id).get("id")).longValue());
        assertThrows(TeachingAccess.AccessException.class,()->service.task(request("jiaoshi",otherTeacherId),id));
        Map<String,Object> hidden=service.tasks(request("jiaoshi",otherTeacherId),currentId,(String)task.get("task_code"),1,20);
        assertEquals(0L,hidden.get("total"));
    }
    @Test void projectCrudCopyAndHistoricalReadOnlyWorkAgainstActualSchema(){
        long first=((Number)service.createTask(admin,taskInput()).get("id")).longValue();
        long second=((Number)service.createTask(admin,taskInput()).get("id")).longValue();
        MockHttpServletRequest owner=request("jiaoshi",teacherId),stranger=request("jiaoshi",otherTeacherId);
        Map<String,Object> project=service.createProject(owner,projectInput(first));long projectId=((Number)project.get("id")).longValue();
        assertEquals(teacherId,((Number)project.get("created_by_teacher_id")).longValue());
        assertThrows(TeachingAccess.AccessException.class,()->service.updateProject(stranger,projectId,Collections.singletonMap("name","越权")));
        assertThrows(TeachingAccess.AccessException.class,()->service.deleteProject(stranger,projectId));
        assertThrows(IllegalArgumentException.class,()->service.updateProject(owner,projectId,Collections.singletonMap("task_id",second)));
        assertEquals("已更新",service.updateProject(owner,projectId,Collections.singletonMap("name","已更新")).get("name"));
        assertEquals(1,service.copyProjects(owner,first,second).get("copied"));
        assertEquals(1,service.copyProjects(owner,first,second).get("skipped"));
        Map<String,Object> copied=((List<Map<String,Object>>)service.projects(owner,second).get("list")).get(0);
        assertNotEquals(project.get("project_code"),copied.get("project_code"));
        assertEquals(projectId,((Number)copied.get("copied_from_id")).longValue());
        service.deleteProject(owner,((Number)copied.get("id")).longValue());service.deleteProject(owner,projectId);
        assertEquals(0,service.projects(owner,first).get("total"));
        jdbc.update("UPDATE academic_term SET status='ARCHIVED' WHERE id=?",currentId);
        assertFalse((Boolean)service.projects(owner,first).get("editable"));
        assertThrows(TeachingAccess.AccessException.class,()->service.createProject(admin,projectInput(first)));
    }
    @Test void adminProjectAuditNeverUsesAnAdministratorIdAsTeacherForeignKey(){
        long task=((Number)service.createTask(admin,taskInput()).get("id")).longValue();
        Map<String,Object> project=service.createProject(admin,projectInput(task));
        assertNull(project.get("created_by_teacher_id"));assertNull(project.get("updated_by_teacher_id"));
        assertNotNull(service.lookups(admin).get("terms"));
        assertNotNull(service.dashboard(admin).get("counts"));
    }
    @Test void archivedSourceCopiesIntoCurrentTermWhileRemainingReadOnly(){
        long source=((Number)service.createTask(admin,taskInput()).get("id")).longValue();
        long target=((Number)service.createTask(admin,taskInput()).get("id")).longValue();
        long project=((Number)service.createProject(admin,projectInput(source)).get("id")).longValue();
        long archive=jdbc.queryForObject("SELECT MIN(id) FROM academic_term WHERE status='ARCHIVED'",Long.class);
        jdbc.update("UPDATE teaching_task SET term_id=? WHERE id=?",archive,source);
        MockHttpServletRequest owner=request("jiaoshi",teacherId);
        assertFalse((Boolean)service.projects(owner,source).get("editable"));
        assertEquals(1,service.copyProjects(owner,source,target).get("copied"));
        assertThrows(TeachingAccess.AccessException.class,()->service.updateProject(admin,project,Collections.singletonMap("name","禁止历史修改")));
        assertThrows(TeachingAccess.AccessException.class,()->service.copyProjects(request("jiaoshi",otherTeacherId),source,target));
        assertThrows(TeachingAccess.AccessException.class,()->service.copyProjects(admin,target,source));
    }
    @Test void teacherAndLabCrudPreserveUnknownMetadataAndPasswordsAreHashed(){
        Map<String,Object> teacher=new HashMap<>();String code="TEST"+UUID.randomUUID().toString().substring(0,8);
        teacher.put("gonghao",code);teacher.put("jiaoshixingming","事务测试教师");teacher.put("xueyuan","测试学院");
        Map<String,Object> created=service.saveTeacher(admin,null,teacher);long id=((Number)created.get("id")).longValue();
        String original=(String)created.get("password"),stored=jdbc.queryForObject("SELECT mima FROM jiaoshi WHERE id=?",String.class,id);
        assertTrue(TeachingPasswords.matches(original,stored));
        String reset=(String)service.resetTeacherPassword(admin,id).get("password");assertNotEquals(original,reset);
        assertTrue(TeachingPasswords.matches(reset,jdbc.queryForObject("SELECT mima FROM jiaoshi WHERE id=?",String.class,id)));
        Map<String,Object> password=new HashMap<>();password.put("oldPassword",reset);password.put("newPassword","ChangedPass928!");
        service.changePassword(request("jiaoshi",id),password);
        assertTrue(TeachingPasswords.matches("ChangedPass928!",jdbc.queryForObject("SELECT mima FROM jiaoshi WHERE id=?",String.class,id)));
        Map<String,Object> lab=new HashMap<>();lab.put("shiyanshibianhao",code);lab.put("shiyanshimingcheng","事务测试实验室");
        long newLab=((Number)service.saveLab(admin,null,lab).get("id")).longValue();
        assertNull(jdbc.queryForMap("SELECT equipment_count,manager_teacher_id FROM shiyanshixinxi WHERE id=?",newLab).get("equipment_count"));
        lab.put("manager_teacher_id",id);lab.put("equipment_count",0);service.saveLab(admin,newLab,lab);
        assertThrows(IllegalArgumentException.class,()->service.deleteTeacher(admin,id));
        service.deleteLab(admin,newLab);service.deleteTeacher(admin,id);
    }
}
