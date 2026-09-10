package com.teaching;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

@Service
public class TeachingService {
    private final JdbcTemplate jdbc;
    private final TeachingAccess access;
    private final TeachingTermService terms;
    public TeachingService(JdbcTemplate jdbc, TeachingAccess access, TeachingTermService terms) {
        this.jdbc=jdbc; this.access=access; this.terms=terms;
    }

    private static final String TASK_FROM = " FROM teaching_task t JOIN course c ON c.id=t.course_id " +
        "JOIN academic_term a ON a.id=t.term_id JOIN academic_year y ON y.id=a.academic_year_id ";
    private static final String TASK_SELECT = "SELECT t.*,c.course_code,t.course_name_snapshot course_name," +
        "CONCAT(y.name,'-',a.term_no) term_name,a.status,y.start_year,a.term_no," +
        "COALESCE((SELECT GROUP_CONCAT(j.teacher_name ORDER BY j.id SEPARATOR '、') FROM teaching_task_teacher tt " +
        "JOIN teacher j ON j.id=tt.teacher_id WHERE tt.task_id=t.id),'') teacher_names," +
        "COALESCE((SELECT SUM(s.hours) FROM schedule_detail s WHERE s.task_id=t.id),0) scheduled_hours," +
        "COALESCE((SELECT GROUP_CONCAT(DISTINCT l.lab_name ORDER BY l.lab_name SEPARATOR '、') " +
        "FROM schedule_detail s JOIN laboratory l ON l.id=s.lab_id WHERE s.task_id=t.id)," +
        "(SELECT dl.lab_name FROM laboratory dl WHERE dl.id=t.default_lab_id),'') lab_names," +
        "(SELECT COUNT(*) FROM experiment_project p WHERE p.task_id=t.id) project_count";

    private String scope(Long teacher, String alias, List<Object> args) {
        if (teacher==null) return "";
        args.add(teacher);
        return " AND EXISTS (SELECT 1 FROM teaching_task_teacher own WHERE own.task_id="+alias+".id AND own.teacher_id=?)";
    }
    private long count(String sql, Object... args) {
        Long value=jdbc.queryForObject(sql,Long.class,args); return value==null?0:value;
    }
    private static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) result.put(String.valueOf(pairs[i]),pairs[i+1]);
        return result;
    }

    public Map<String,Object> lookups(HttpServletRequest request) {
        Long teacher=access.teacherId(request);
        List<Object> args=new ArrayList<>();
        String filter=scope(teacher,"t",args);
        List<Map<String,Object>> courses=jdbc.queryForList("SELECT c.id,c.course_code,c.course_name FROM course c WHERE 1=1"+
            (teacher==null?"":" AND EXISTS (SELECT 1 FROM teaching_task t WHERE t.course_id=c.id"+filter+")")+" ORDER BY c.course_code",args.toArray());
        return map("terms",terms.terms(teacher),"teachers",teacherRows(teacher,""),"labs",labRows(teacher,""),"courses",courses);
    }

    public Map<String,Object> dashboard(HttpServletRequest request) {
        Long teacher=access.teacherId(request);
        List<Object> args=new ArrayList<>(); String filter=scope(teacher,"t",args);
        long taskCount=count("SELECT COUNT(*) FROM teaching_task t WHERE 1=1"+filter,args.toArray());
        long courseCount=count("SELECT COUNT(DISTINCT t.course_id) FROM teaching_task t WHERE 1=1"+filter,args.toArray());
        long projectCount=count("SELECT COUNT(*) FROM experiment_project p JOIN teaching_task t ON t.id=p.task_id WHERE 1=1"+filter,args.toArray());
        List<Map<String,Object>> labs=labRows(teacher,"");
        List<Map<String,Object>> teachers=teacherRows(teacher,"");
        List<String> warnings=new ArrayList<>();
        long hoursMismatch=count("SELECT COUNT(*) FROM teaching_task t WHERE t.planned_lab_hours<>"+
            "COALESCE((SELECT SUM(s.hours) FROM schedule_detail s WHERE s.task_id=t.id),0)"+filter,args.toArray());
        if(hoursMismatch>0) warnings.add(hoursMismatch+" 个教学任务的计划实验学时与实际排课学时不同；报表按实际排课统计。");
        long missing=labs.stream().filter(l->l.get("manager_teacher_id")==null||l.get("equipment_count")==null).count();
        if(missing>0) warnings.add(missing+" 间实验室尚未补齐负责人或设备数量。");
        if(teachers.stream().anyMatch(t->Boolean.TRUE.equals(t.get("is_temporary")))) warnings.add("部分教师使用 TMP 临时工号，待管理员核实真实工号。");
        if(projectCount==0) warnings.add("尚未录入实验项目；请在当前学期任务中添加、导入或复制项目。");
        return map("counts",map("tasks",taskCount,"courses",courseCount,"labs",labs.size(),"teachers",teachers.size(),
            "projects",projectCount,"imports",teacher==null?count("SELECT COUNT(*) FROM teaching_import_batch"):0),
            "currentTerm",terms.currentTerm(),"warnings",warnings);
    }

    public Map<String,Object> tasks(HttpServletRequest request, Long termId, String q, int page, int limit) {
        Long teacher=access.teacherId(request);
        if(page<1 || limit<1 || limit>200) throw new IllegalArgumentException("页码须大于0，每页数量为1至200");
        List<Object> args=new ArrayList<>(); String where=" WHERE 1=1"+scope(teacher,"t",args);
        if(termId!=null){where+=" AND t.term_id=?";args.add(termId);}
        if(q!=null&&!q.trim().isEmpty()){
            where+=" AND (c.course_code LIKE ? OR t.course_name_snapshot LIKE ? OR t.class_composition LIKE ? OR t.task_code LIKE ? OR t.teacher_names_original LIKE ?)";
            for(int i=0;i<5;i++)args.add("%"+q.trim()+"%");
        }
        long total=count("SELECT COUNT(*)"+TASK_FROM+where,args.toArray());
        args.add(limit);args.add(((long)page-1)*limit);
        List<Map<String,Object>> rows=jdbc.queryForList(TASK_SELECT+TASK_FROM+where+" ORDER BY y.start_year DESC,a.term_no DESC,t.id DESC LIMIT ? OFFSET ?",args.toArray());
        return map("list",rows,"total",total);
    }

    public Map<String,Object> task(HttpServletRequest request,long id) {
        access.requireTask(request,id,false);
        Map<String,Object> result=jdbc.queryForMap(TASK_SELECT+TASK_FROM+" WHERE t.id=?",id);
        result.put("schedule",jdbc.queryForList("SELECT s.*,l.lab_code lab_code,l.lab_name lab_name " +
            "FROM schedule_detail s JOIN laboratory l ON l.id=s.lab_id WHERE s.task_id=? ORDER BY s.teaching_week,s.weekday,s.period_start",id));
        result.put("teacher_ids",jdbc.queryForList("SELECT teacher_id FROM teaching_task_teacher WHERE task_id=? ORDER BY teacher_id",Long.class,id));
        result.put("editable",TeachingAccess.writable(result));
        return result;
    }

    @Transactional
    public Map<String,Object> createTask(HttpServletRequest request,Map<String,Object> body) {
        access.requireAdmin(request);
        long termId=positiveId(body,"termId"),courseId=positiveId(body,"courseId");
        List<Map<String,Object>> termRows=jdbc.queryForList(TeachingTermService.SELECT_TERMS+" WHERE a.id=?",termId);
        if(termRows.isEmpty()||!TeachingAccess.writable(termRows.get(0))) throw new TeachingAccess.AccessException(403,"只能在当前开放学期创建教学任务");
        List<Map<String,Object>> courses=jdbc.queryForList("SELECT course_name FROM course WHERE id=?",courseId);
        if(courses.isEmpty())throw new IllegalArgumentException("课程不存在");
        Object teacherInput=body.get("teacherIds");
        if(!(teacherInput instanceof Collection)||((Collection<?>)teacherInput).isEmpty())throw new IllegalArgumentException("请至少选择一位授课教师");
        Set<Long> teacherIds=new LinkedHashSet<>(); List<String> names=new ArrayList<>();
        for(Object input:(Collection<?>)teacherInput){
            long id=integer(input,"teacherIds",1,Long.MAX_VALUE);
            if(!teacherIds.add(id))continue;
            List<String> found=jdbc.queryForList("SELECT teacher_name FROM teacher WHERE id=?",String.class,id);
            if(found.isEmpty())throw new IllegalArgumentException("所选教师不存在");names.add(found.get(0));
        }
        Long labId=optionalId(body.get("labId"),"labId"); String location="";
        if(labId!=null){
            List<String> found=jdbc.queryForList("SELECT lab_name FROM laboratory WHERE id=?",String.class,labId);
            if(found.isEmpty())throw new IllegalArgumentException("实验室不存在");location=found.get(0);
        }
        String classes=requiredText(body,"classComposition",4000);
        String majors=optionalText(body,"majorComposition",4000);
        int enrollment=(int)integer(body.get("enrollmentCount"),"enrollmentCount",0,Integer.MAX_VALUE);
        BigDecimal hours=decimal(body.get("plannedLabHours"),"plannedLabHours",new BigDecimal("0.01"),new BigDecimal("999999.99"));
        String code="T"+UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase(Locale.ROOT);
        long id=insert("INSERT INTO teaching_task(task_code,term_id,course_id,course_name_snapshot,department_name,credits,"+
            "class_composition,major_composition,class_size,enrollment_count,planned_lab_hours,weekly_hours,original_week_range,"+
            "scheduled_week_range,start_week,end_week,course_weekly_hours_text,teacher_names_original,locations_original,schedule_original,default_lab_id) "+
            "VALUES (?,?,?,?,?,0,?,?,?,?,?,0,'','',1,1,'',?,?,'',?)",
            code,termId,courseId,courses.get(0).get("course_name"),"待确认",classes,majors,enrollment,enrollment,hours,String.join("、",names),location,labId);
        for(Long teacher:teacherIds)jdbc.update("INSERT INTO teaching_task_teacher(task_id,teacher_id) VALUES (?,?)",id,teacher);
        return task(request,id);
    }

    public Map<String,Object> projects(HttpServletRequest request,long taskId) {
        Map<String,Object> task=access.requireTask(request,taskId,false);
        List<Map<String,Object>> list=jdbc.queryForList("SELECT * FROM experiment_project WHERE task_id=? ORDER BY sort_order,id",taskId);
        return map("list",list,"total",list.size(),"editable",TeachingAccess.writable(task));
    }

    @Transactional
    public Map<String,Object> createProject(HttpServletRequest request,Map<String,Object> body) {
        long taskId=positiveId(body,"task_id");access.requireTask(request,taskId,true);
        validateProject(body);
        Long teacher=access.teacherId(request);
        String code=optionalText(body,"project_code",64);
        if(code.isEmpty())code=newProjectCode();
        long id=insertProject(taskId,code,body,null,teacher);
        return jdbc.queryForMap("SELECT * FROM experiment_project WHERE id=?",id);
    }

    @Transactional
    public Map<String,Object> updateProject(HttpServletRequest request,long id,Map<String,Object> body) {
        Map<String,Object> original=projectAccess(request,id,true);
        if(body.containsKey("task_id")&&positiveId(body,"task_id")!=((Number)original.get("task_id")).longValue())
            throw new IllegalArgumentException("不能更改项目所属教学任务，请使用复制功能");
        if(body.containsKey("project_code")&&!String.valueOf(original.get("project_code")).equals(String.valueOf(body.get("project_code"))))
            throw new IllegalArgumentException("项目编号不可修改");
        Map<String,Object> merged=new HashMap<>(original);merged.putAll(body);validateProject(merged);
        jdbc.update("UPDATE experiment_project SET school_code=?,name=?,category_code=?,type_code=?,discipline_code=?,"+
            "requirement_code=?,participant_type_code=?,group_size=?,hours=?,sort_order=?,updated_by_teacher_id=? WHERE id=?",
            merged.get("school_code"),merged.get("name"),merged.get("category_code"),merged.get("type_code"),merged.get("discipline_code"),
            merged.get("requirement_code"),merged.get("participant_type_code"),merged.get("group_size"),merged.get("hours"),
            merged.get("sort_order"),access.teacherId(request),id);
        return jdbc.queryForMap("SELECT * FROM experiment_project WHERE id=?",id);
    }

    @Transactional
    public void deleteProject(HttpServletRequest request,long id) {
        projectAccess(request,id,true);
        if(count("SELECT COUNT(*) FROM experiment_project WHERE copied_from_id=?",id)>0)
            throw new IllegalArgumentException("该项目已有跨学期复制记录，无法删除，请保留原始项目");
        jdbc.update("DELETE FROM experiment_project WHERE id=?",id);
    }

    @Transactional
    public Map<String,Object> copyProjects(HttpServletRequest request,long sourceTaskId,long targetTaskId) {
        if(sourceTaskId==targetTaskId)throw new IllegalArgumentException("来源任务与目标任务不能相同");
        Map<String,Object> source=access.requireTask(request,sourceTaskId,false);
        Map<String,Object> target=access.requireTask(request,targetTaskId,true);
        if(!source.get("course_id").equals(target.get("course_id")))throw new IllegalArgumentException("只能复制同一课程的实验项目");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM experiment_project WHERE task_id=? ORDER BY sort_order,id",sourceTaskId);
        Long teacher=access.teacherId(request);int copied=0,skipped=0;
        for(Map<String,Object> row:rows){
            long sourceId=((Number)row.get("id")).longValue();
            if(count("SELECT COUNT(*) FROM experiment_project WHERE task_id=? AND copied_from_id=?",targetTaskId,sourceId)>0){skipped++;continue;}
            insertProject(targetTaskId,newProjectCode(),row,sourceId,teacher);copied++;
        }
        return map("copied",copied,"skipped",skipped,"source_total",rows.size());
    }

    private Map<String,Object> projectAccess(HttpServletRequest request,long id,boolean write) {
        // Scope before returning any row, and repeat task authorization before every mutation.
        Long teacher=access.teacherId(request);List<Object> args=new ArrayList<>();args.add(id);
        String filter=scope(teacher,"t",args);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT p.* FROM experiment_project p JOIN teaching_task t ON t.id=p.task_id WHERE p.id=?"+filter,args.toArray());
        if(rows.isEmpty())throw new TeachingAccess.AccessException(404,"实验项目不存在或没有访问权限");
        access.requireTask(request,((Number)rows.get(0).get("task_id")).longValue(),write);
        return rows.get(0);
    }

    private long insertProject(long taskId,String code,Map<String,Object> body,Long copied,Long teacher) {
        return insert("INSERT INTO experiment_project(task_id,project_code,school_code,name,category_code,type_code,discipline_code,"+
            "requirement_code,participant_type_code,group_size,hours,sort_order,copied_from_id,created_by_teacher_id,updated_by_teacher_id) "+
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",taskId,code,body.get("school_code"),body.get("name"),body.get("category_code"),
            body.get("type_code"),body.get("discipline_code"),body.get("requirement_code"),body.get("participant_type_code"),
            body.get("group_size"),body.get("hours"),body.get("sort_order"),copied,teacher,teacher);
    }
    private static String newProjectCode(){return "P"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);}

    public static void validateProject(Map<String,Object> body) {
        for(String key:Arrays.asList("school_code","name","discipline_code"))
            body.put(key,requiredText(body,key,"name".equals(key)?50:"school_code".equals(key)?32:16));
        code(body,"category_code","1","2","3","4");code(body,"type_code","1","2","3","4","5");
        code(body,"requirement_code","1","2","3");code(body,"participant_type_code","1","2","3","4","5");
        body.put("group_size",integer(body.get("group_size"),"group_size",1,99));
        body.put("hours",decimal(body.get("hours"),"hours",new BigDecimal("0.01"),new BigDecimal("9999")));
        body.put("sort_order",integer(body.getOrDefault("sort_order",0),"sort_order",0,Integer.MAX_VALUE));
    }

    public Map<String,Object> labs(HttpServletRequest request,String q) {
        List<Map<String,Object>> rows=labRows(access.teacherId(request),q);return map("list",rows,"total",rows.size());
    }
    private List<Map<String,Object>> labRows(Long teacher,String q) {
        List<Object> args=new ArrayList<>();String where=" WHERE 1=1";
        if(teacher!=null){where+=" AND EXISTS (SELECT 1 FROM teaching_task t JOIN teaching_task_teacher own ON own.task_id=t.id "+
            "WHERE own.teacher_id=? AND (t.default_lab_id=l.id OR EXISTS (SELECT 1 FROM schedule_detail s WHERE s.task_id=t.id AND s.lab_id=l.id)))";args.add(teacher);}
        if(q!=null&&!q.trim().isEmpty()){where+=" AND (l.lab_code LIKE ? OR l.lab_name LIKE ? OR l.location LIKE ?)";
            for(int i=0;i<3;i++)args.add("%"+q.trim()+"%");}
        return jdbc.queryForList("SELECT l.id,l.lab_code AS shiyanshibianhao,l.lab_name AS shiyanshimingcheng,l.location AS shiyanshiweizhi,l.manager_teacher_id,l.equipment_count,"+
            "j.teacher_name manager_name FROM laboratory l LEFT JOIN teacher j ON j.id=l.manager_teacher_id"+where+" ORDER BY l.lab_code",args.toArray());
    }
    @Transactional
    public Map<String,Object> saveLab(HttpServletRequest request,Long id,Map<String,Object> body) {
        access.requireAdmin(request);
        String code=requiredText(body,"shiyanshibianhao",200),name=requiredText(body,"shiyanshimingcheng",200),location=optionalText(body,"shiyanshiweizhi",200);
        Long manager=optionalId(body.get("manager_teacher_id"),"manager_teacher_id");
        Long equipment=body.get("equipment_count")==null||"".equals(body.get("equipment_count"))?null:integer(body.get("equipment_count"),"equipment_count",0,Integer.MAX_VALUE);
        if(manager!=null&&count("SELECT COUNT(*) FROM teacher WHERE id=?",manager)!=1)throw new IllegalArgumentException("负责人教师不存在");
        if(id==null)id=insert("INSERT INTO laboratory(lab_code,lab_name,location,manager_teacher_id,equipment_count,status,lab_size) VALUES (?,?,?,?,?,?,'')",code,name,location,manager,equipment,"可用");
        else {
            requireExists("laboratory",id,"实验室");
            jdbc.update("UPDATE laboratory SET lab_code=?,lab_name=?,location=?,manager_teacher_id=?,equipment_count=? WHERE id=?",code,name,location,manager,equipment,id);
        }
        return map("id",id);
    }
    @Transactional
    public void deleteLab(HttpServletRequest request,long id) {
        access.requireAdmin(request);requireExists("laboratory",id,"实验室");
        if(count("SELECT COUNT(*) FROM schedule_detail WHERE lab_id=?",id)>0||count("SELECT COUNT(*) FROM teaching_task WHERE default_lab_id=?",id)>0)
            throw new IllegalArgumentException("实验室已有教学任务或排课，不能删除");
        jdbc.update("DELETE FROM laboratory WHERE id=?",id);
    }

    public Map<String,Object> teachers(HttpServletRequest request,String q) {
        List<Map<String,Object>> rows=teacherRows(access.teacherId(request),q);return map("list",rows,"total",rows.size());
    }
    private List<Map<String,Object>> teacherRows(Long teacher,String q) {
        List<Object> args=new ArrayList<>();String where=" WHERE 1=1";
        if(teacher!=null){where+=" AND (j.id=? OR EXISTS (SELECT 1 FROM teaching_task_teacher co JOIN teaching_task_teacher own ON own.task_id=co.task_id WHERE co.teacher_id=j.id AND own.teacher_id=?))";args.add(teacher);args.add(teacher);}
        if(q!=null&&!q.trim().isEmpty()){where+=" AND (j.teacher_no LIKE ? OR j.teacher_name LIKE ? OR j.college LIKE ?)";for(int i=0;i<3;i++)args.add("%"+q.trim()+"%");}
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT j.id,j.teacher_no AS gonghao,j.teacher_name AS jiaoshixingming,j.college AS xueyuan FROM teacher j"+where+" ORDER BY j.teacher_no",args.toArray());
        for(Map<String,Object> row:rows)row.put("is_temporary",String.valueOf(row.get("gonghao")).toUpperCase(Locale.ROOT).startsWith("TMP"));
        return rows;
    }
    @Transactional
    public Map<String,Object> saveTeacher(HttpServletRequest request,Long id,Map<String,Object> body) {
        access.requireAdmin(request);
        String code=requiredText(body,"gonghao",200),name=requiredText(body,"jiaoshixingming",200),school=optionalText(body,"xueyuan",200);
        if(id==null){String password=TeachingPasswords.newPassword();
            id=insert("INSERT INTO teacher(teacher_no,teacher_name,college,password) VALUES (?,?,?,?)",code,name,school,TeachingPasswords.hash(password));
            return map("id",id,"password",password);
        }
        requireExists("teacher",id,"教师");
        jdbc.update("UPDATE teacher SET teacher_no=?,teacher_name=?,college=? WHERE id=?",code,name,school,id);
        return map("id",id);
    }
    @Transactional
    public Map<String,Object> resetTeacherPassword(HttpServletRequest request,long id) {
        access.requireAdmin(request);requireExists("teacher",id,"教师");String password=TeachingPasswords.newPassword();
        jdbc.update("UPDATE teacher SET password=? WHERE id=?",TeachingPasswords.hash(password),id);
        jdbc.update("DELETE FROM token WHERE userid=? AND tablename='teacher'",id);
        return map("password",password);
    }
    @Transactional
    public void deleteTeacher(HttpServletRequest request,long id) {
        access.requireAdmin(request);requireExists("teacher",id,"教师");
        if(count("SELECT COUNT(*) FROM teaching_task_teacher WHERE teacher_id=?",id)>0||count("SELECT COUNT(*) FROM laboratory WHERE manager_teacher_id=?",id)>0||
            count("SELECT COUNT(*) FROM experiment_project WHERE created_by_teacher_id=? OR updated_by_teacher_id=?",id,id)>0)
            throw new IllegalArgumentException("教师已关联任务、实验室或项目记录，不能删除");
        jdbc.update("DELETE FROM token WHERE userid=? AND tablename='teacher'",id);jdbc.update("DELETE FROM teacher WHERE id=?",id);
    }

    @Transactional
    public void changePassword(HttpServletRequest request,Map<String,Object> body) {
        Long teacher=access.teacherId(request);
        long id=Long.parseLong(String.valueOf(request.getSession(false).getAttribute("userId")));
        String oldPassword=String.valueOf(body.getOrDefault("oldPassword",""));
        String password=String.valueOf(body.getOrDefault("newPassword",""));
        if(password.length()<8||password.length()>64||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("新密码长度应为8至64位，UTF-8编码不超过72字节");
        if(password.equals(oldPassword))throw new IllegalArgumentException("新密码不能与原密码相同");
        String table=teacher==null?"users":"teacher",column="password";
        String stored=jdbc.queryForObject("SELECT "+column+" FROM "+table+" WHERE id=?",String.class,id);
        if(!TeachingPasswords.matches(oldPassword,stored))throw new IllegalArgumentException("原密码不正确");
        jdbc.update("UPDATE "+table+" SET "+column+"=? WHERE id=?",TeachingPasswords.hash(password),id);
        String token=request.getHeader("Token");
        if(token==null)jdbc.update("DELETE FROM token WHERE userid=? AND tablename=?",id,table);
        else jdbc.update("DELETE FROM token WHERE userid=? AND tablename=? AND token<>?",id,table,token);
    }

    private void requireExists(String table,long id,String label) {
        if(count("SELECT COUNT(*) FROM "+table+" WHERE id=?",id)!=1)throw new TeachingAccess.AccessException(404,label+"不存在");
    }
    private long insert(String sql,Object... values) {
        KeyHolder holder=new GeneratedKeyHolder();
        jdbc.update(connection->{PreparedStatement p=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);return p;},holder);
        if(holder.getKey()==null)throw new IllegalStateException("未获取到新增记录编号");return holder.getKey().longValue();
    }
    public static long positiveId(Map<String,Object> body,String key){return integer(body.get(key),key,1,Long.MAX_VALUE);}
    private static Long optionalId(Object value,String key){return value==null||"".equals(value)?null:integer(value,key,1,Long.MAX_VALUE);}
    private static long integer(Object value,String key,long min,long max) {
        try {long n=new BigDecimal(String.valueOf(value)).longValueExact();if(n<min||n>max)throw new ArithmeticException();return n;}
        catch(RuntimeException e){throw new IllegalArgumentException(key+" 必须为 "+min+" 至 "+max+" 的整数");}
    }
    private static BigDecimal decimal(Object value,String key,BigDecimal min,BigDecimal max) {
        try {BigDecimal n=new BigDecimal(String.valueOf(value));if(n.compareTo(min)<0||n.compareTo(max)>0||n.stripTrailingZeros().scale()>2)throw new ArithmeticException();return n;}
        catch(RuntimeException e){throw new IllegalArgumentException(key+" 必须在 "+min+" 至 "+max+" 之间且最多两位小数");}
    }
    private static String requiredText(Map<String,Object> body,String key,int max) {
        String value=optionalText(body,key,max);if(value.isEmpty())throw new IllegalArgumentException(key+" 不能为空");return value;
    }
    private static String optionalText(Map<String,Object> body,String key,int max) {
        Object raw=body.get(key);if(raw==null)return "";
        if(!(raw instanceof String))throw new IllegalArgumentException(key+" 必须为文本");
        String value=((String)raw).trim();if(value.length()>max)throw new IllegalArgumentException(key+" 长度不能超过 "+max);return value;
    }
    private static void code(Map<String,Object> body,String key,String... allowed) {
        String value=String.valueOf(body.get(key));if(!Arrays.asList(allowed).contains(value))throw new IllegalArgumentException(key+" 代码无效");body.put(key,value);
    }
}
