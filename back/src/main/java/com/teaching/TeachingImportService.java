package com.teaching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import static com.teaching.TeachingExcel.*;

@Service
public class TeachingImportService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final TeachingAccess access;
    public TeachingImportService(JdbcTemplate db,ObjectMapper json,TeachingAccess access) { this.db=db; this.json=json; this.access=access; }

    public Map<String,Object> list(HttpServletRequest request) {
        access.requireAdmin(request);
        List<Map<String,Object>> rows=db.queryForList("SELECT b.*, SUM(r.status='PROMOTED') promoted,SUM(r.status='REVIEW') warnings,SUM(r.status='ERROR') errors FROM teaching_import_batch b LEFT JOIN teaching_import_row r ON r.batch_id=b.id GROUP BY b.id ORDER BY b.id DESC LIMIT 200");
        return map("list",rows,"total",db.queryForObject("SELECT COUNT(*) FROM teaching_import_batch",Long.class));
    }
    public Map<String,Object> detail(HttpServletRequest request,long id) {
        access.requireAdmin(request);
        List<Map<String,Object>> batches=db.queryForList("SELECT * FROM teaching_import_batch WHERE id=?",id);
        if(batches.isEmpty()) throw new IllegalArgumentException("导入批次不存在");
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM teaching_import_row WHERE batch_id=? ORDER BY sheet_name,source_row",id);
        for(Map<String,Object> row:rows) for(String key:Arrays.asList("raw_data","parsed_schedule","issues")) if(row.get(key)!=null) row.put(key,readJson(row.get(key).toString()));
        return map("batch",batches.get(0),"rows",rows,"summary",summary(id));
    }
    @Transactional
    public Map<String,Object> timetable(HttpServletRequest request,byte[] content,String filename) {
        access.requireAdmin(request);
        String digest=sha256(content);
        List<Long> old=db.queryForList("SELECT id FROM teaching_import_batch WHERE file_sha256=?",Long.class,digest);
        if(!old.isEmpty()) return summary(old.get(0));
        List<ExcelRow> rows=tableRows(content,TIMETABLE_HEADERS,false);
        if(rows.isEmpty()) throw new IllegalArgumentException("没有可导入的课表数据");
        List<Candidate> candidates=new ArrayList<>(); Map<String,Integer> identities=new HashMap<>(); Map<String,Set<String>> courseNames=new HashMap<>();
        for(ExcelRow row:rows) {
            Candidate c=new Candidate(row); candidates.add(c);
            try { validate(c); } catch(IllegalArgumentException e) { c.issues.add("INVALID: "+e.getMessage()); c.error=true; }
            String identity=row.at("学年")+"|"+row.at("学期")+"|"+row.at("课程号")+"|"+row.at("教学班组成")+"|"+teacherNames(row.at("教师名称"));
            c.identity=identity; identities.put(identity,identities.getOrDefault(identity,0)+1);
            courseNames.computeIfAbsent(row.at("课程号"),k->new HashSet<>()).add(row.at("课程名称"));
        }
        // The unique digest also protects two simultaneous uploads of the same file.
        db.update("INSERT INTO teaching_import_batch(file_sha256,file_name,parser_version,row_count) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)",digest,filename==null?"timetable.xlsx":filename.substring(0,Math.min(255,filename.length())),"java-1.0",rows.size());
        long batchId=db.queryForObject("SELECT id FROM teaching_import_batch WHERE file_sha256=?",Long.class,digest);
        if(db.queryForObject("SELECT COUNT(*) FROM teaching_import_row WHERE batch_id=?",Long.class,batchId)>0) return summary(batchId);
        for(Candidate c:candidates) {
            ExcelRow row=c.row;
            if(identities.get(c.identity)>1) { c.issues.add("TASK_IDENTITY_AMBIGUOUS: 同文件存在相同学期、课程、班级和教师组合，保留待核对"); c.review=true; }
            if(courseNames.get(row.at("课程号")).size()>1) { c.issues.add("COURSE_NAME_CONFLICT: 同一课程号对应多个名称"); c.review=true; }
            if(!c.error && !c.review) checkExisting(c);
            long rowId=insert("INSERT INTO teaching_import_row(batch_id,sheet_name,source_row,raw_data,parsed_schedule,issues,status) VALUES(?,?,?,?,?,?,?)",batchId,row.sheet,row.sourceRow,toJson(row.values),c.schedule==null?null:toJson(c.schedule),toJson(c.issues),c.error?"ERROR":"REVIEW");
            if(!c.error && !c.review) {
                promote(c,rowId,batchId);
                db.update("UPDATE teaching_import_row SET status='PROMOTED',issues=? WHERE id=?",toJson(c.issues),rowId);
            }
        }
        return summary(batchId);
    }
    private void validate(Candidate c) {
        ExcelRow r=c.row;
        for(String key:TIMETABLE_HEADERS) if(!key.equals("专业组成")) required(r.at(key),key,key.equals("课程号")?64:16000);
        if(!r.at("学年").matches("\\d{4}-\\d{4}")) throw new IllegalArgumentException("学年格式应为2025-2026");
        int start=Integer.parseInt(r.at("学年").substring(0,4)),end=Integer.parseInt(r.at("学年").substring(5));
        if(start<1900 || start>9998 || end!=start+1) throw new IllegalArgumentException("学年范围不正确");
        int term=number(r.at("学期"),"学期",true).intValue(); if(term!=1 && term!=2) throw new IllegalArgumentException("学期必须为1或2");
        for(String key:Arrays.asList("教学班人数","选课人数","起始周","结束周")) number(r.at(key),key,true);
        for(String key:Arrays.asList("学分","周学时","课程实验总学时")) number(r.at(key),key,false);
        if(number(r.at("学分"),"学分",false).compareTo(new BigDecimal("9999.99"))>0 || number(r.at("周学时"),"周学时",false).compareTo(new BigDecimal("9999.99"))>0) throw new IllegalArgumentException("学分或周学时超出范围");
        if(number(r.at("课程实验总学时"),"实验总学时",false).signum()==0) throw new IllegalArgumentException("实验总学时必须大于0");
        int first=Integer.parseInt(r.at("起始周")),last=Integer.parseInt(r.at("结束周"));
        if(first<1 || first>last || last>53) throw new IllegalArgumentException("起始周或结束周不合法");
        c.endsAt=Timestamp.valueOf(dateTime(r.at("课程结束时间")));
        for(String key:Arrays.asList("课程名称","开课学院")) required(r.at(key),key,200);
        for(String key:Arrays.asList("起始结束周","课程周学时")) required(r.at(key),key,255);
        c.schedule=parseSchedule(r.at("教学地点"),r.at("上课时间"));
        int hours=c.schedule.stream().mapToInt(s->((Number)s.get("hours")).intValue()).sum();
        if(number(r.at("课程实验总学时"),"实验总学时",false).compareTo(BigDecimal.valueOf(hours))!=0) c.issues.add("HOURS_MISMATCH: 计划实验学时与排课节次学时不同");
        if(!r.at("教学班人数").equals(r.at("选课人数"))) c.issues.add("HEADCOUNT_DIFFERS: 班级人数与选课人数不同，报表采用选课人数");
        if(r.at("专业组成").isEmpty()) c.issues.add("MAJOR_MISSING: 原表未填写专业组成");
        List<String> names=teacherNames(r.at("教师名称"));
        if(names.isEmpty()) throw new IllegalArgumentException("缺少教师姓名");
        if(names.size()>1) c.issues.add("CO_TEACHING: 已拆分合授教师关联");
        for(String name:names) required(name,"教师姓名",200);
    }
    private void checkExisting(Candidate c) {
        ExcelRow r=c.row;
        List<Map<String,Object>> courses=db.queryForList("SELECT id,course_name FROM course WHERE course_code=?",r.at("课程号"));
        if(!courses.isEmpty() && !r.at("课程名称").equals(courses.get(0).get("course_name"))) { c.review=true; c.issues.add("COURSE_NAME_CONFLICT: 已有课程名称不同，未自动覆盖"); }
        List<String> previous=db.queryForList("SELECT t.teacher_names_original FROM teaching_task t JOIN course c ON c.id=t.course_id JOIN academic_term tm ON tm.id=t.term_id JOIN academic_year y ON y.id=tm.academic_year_id WHERE y.name=? AND tm.term_no=? AND c.course_code=? AND t.class_composition=?",String.class,r.at("学年"),r.at("学期"),r.at("课程号"),r.at("教学班组成"));
        for(String teachers:previous) if(teacherNames(teachers).equals(teacherNames(r.at("教师名称")))) { c.review=true; c.issues.add("EXISTING_TASK_REVIEW: 疑似已有教学任务，未新增或覆盖"); break; }
        for(String name:teacherNames(r.at("教师名称"))) if(db.queryForObject("SELECT COUNT(*) FROM jiaoshi WHERE jiaoshixingming=?",Long.class,name)>1) { c.review=true; c.issues.add("TEACHER_NAME_AMBIGUOUS: “"+name+"”对应多个账户，须核对工号"); }
    }
    @Transactional
    public Map<String,Object> confirm(HttpServletRequest request,long batchId,int sourceRow,String sheet,boolean confirmDistinctTask) {
        access.requireAdmin(request);
        if(!confirmDistinctTask) throw new IllegalArgumentException("须明确确认该行是独立的新教学任务；确认将新增任务和排课，不覆盖已有任务");
        List<Map<String,Object>> records=db.queryForList("SELECT * FROM teaching_import_row WHERE batch_id=? AND source_row=? AND sheet_name=? FOR UPDATE",batchId,sourceRow,sheet);
        if(records.isEmpty()) throw new IllegalArgumentException("未找到指定批次和工作表的暂存行");
        Map<String,Object> saved=records.get(0); long rowId=((Number)saved.get("id")).longValue();
        if("PROMOTED".equals(saved.get("status"))) return map("task_id",db.queryForObject("SELECT id FROM teaching_task WHERE source_import_row_id=?",Long.class,rowId),"summary",summary(batchId));
        if(!"REVIEW".equals(saved.get("status"))) throw new IllegalArgumentException("数据错误行不能直接确认，请修正原文件后重新上传");
        Object raw=readJson(saved.get("raw_data").toString());
        if(!(raw instanceof Map)) throw new IllegalArgumentException("暂存原始数据无法读取");
        Map<String,String> values=new LinkedHashMap<>();
        for(String header:TIMETABLE_HEADERS) { Object value=((Map<?,?>)raw).get(header); values.put(header,value==null?"":value.toString().trim()); }
        Candidate candidate=new Candidate(new ExcelRow(sheet,sourceRow,values)); validate(candidate); checkExisting(candidate);
        for(String issue:candidate.issues) if(issue.startsWith("COURSE_NAME_CONFLICT") || issue.startsWith("TEACHER_NAME_AMBIGUOUS")) throw new IllegalArgumentException("尚有未解决的身份或课程冲突："+issue+"。请先在课程或教师管理中核对，再重新确认。");
        Object oldIssues=readJson(saved.get("issues").toString());
        if(oldIssues instanceof List) for(Object issue:(List<?>)oldIssues) if(!candidate.issues.contains(issue.toString())) candidate.issues.add("ORIGINAL: "+issue);
        candidate.issues.add("ADMIN_CONFIRMED_DISTINCT_TASK: 管理员明确确认此源行为独立教学任务，保留原始疑点，未覆盖其他任务");
        promote(candidate,rowId,batchId);
        db.update("UPDATE teaching_import_row SET status='PROMOTED',parsed_schedule=?,issues=? WHERE id=?",toJson(candidate.schedule),toJson(candidate.issues),rowId);
        return map("task_id",db.queryForObject("SELECT id FROM teaching_task WHERE source_import_row_id=?",Long.class,rowId),"summary",summary(batchId));
    }
    private void promote(Candidate c,long rowId,long batchId) {
        ExcelRow r=c.row; int startYear=Integer.parseInt(r.at("学年").substring(0,4)),termNo=Integer.parseInt(r.at("学期"));
        db.update("INSERT INTO academic_year(name,start_year) VALUES(?,?) ON DUPLICATE KEY UPDATE id=id",r.at("学年"),startYear);
        long yearId=db.queryForObject("SELECT id FROM academic_year WHERE start_year=?",Long.class,startYear);
        String state=importTermStatus(startYear,termNo,LocalDate.now(ZoneId.of("Asia/Shanghai")));
        db.update("INSERT INTO academic_term(academic_year_id,term_no,status) VALUES(?,?,?) ON DUPLICATE KEY UPDATE id=id",yearId,termNo,state);
        long termId=db.queryForObject("SELECT id FROM academic_term WHERE academic_year_id=? AND term_no=?",Long.class,yearId,termNo);
        db.update("INSERT INTO course(course_code,course_name) VALUES(?,?) ON DUPLICATE KEY UPDATE id=id",r.at("课程号"),r.at("课程名称"));
        long courseId=db.queryForObject("SELECT id FROM course WHERE course_code=?",Long.class,r.at("课程号"));
        long taskId=insert("INSERT INTO teaching_task(task_code,term_id,course_id,source_import_row_id,course_name_snapshot,department_name,credits,class_composition,major_composition,class_size,enrollment_count,planned_lab_hours,weekly_hours,original_week_range,scheduled_week_range,start_week,end_week,course_ends_at,course_weekly_hours_text,teacher_names_original,locations_original,schedule_original) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            "IMP-"+batchId+"-"+rowId,termId,courseId,rowId,r.at("课程名称"),r.at("开课学院"),r.at("学分"),r.at("教学班组成"),r.at("专业组成"),r.at("教学班人数"),r.at("选课人数"),r.at("课程实验总学时"),r.at("周学时"),r.at("起始结束周"),r.at("排课起始结束周"),r.at("起始周"),r.at("结束周"),c.endsAt,r.at("课程周学时"),r.at("教师名称"),r.at("教学地点"),r.at("上课时间"));
        for(String name:teacherNames(r.at("教师名称"))) {
            List<Long> ids=db.queryForList("SELECT id FROM jiaoshi WHERE jiaoshixingming=?",Long.class,name); long id;
            if(ids.isEmpty()) { String code="TMP"+UUID.randomUUID().toString().replace("-","").substring(0,16); id=insert("INSERT INTO jiaoshi(gonghao,mima,jiaoshixingming,xueyuan) VALUES(?,?,?,?)",code,TeachingPasswords.hash(TeachingPasswords.newPassword()),name,r.at("开课学院")); c.issues.add("TEMPORARY_TEACHER: "+name+" 生成临时账户 "+code+"，待管理员核对真实工号并重置初始密码"); }
            else { id=ids.get(0); c.issues.add("TEACHER_NAME_MATCH: "+name+" 按唯一姓名关联，原课表未提供工号"); }
            db.update("INSERT INTO teaching_task_teacher(task_id,teacher_id) VALUES(?,?)",taskId,id);
        }
        for(Map<String,Object> s:c.schedule) {
            String code=s.get("lab_code").toString();
            db.update("INSERT INTO shiyanshixinxi(shiyanshibianhao,shiyanshimingcheng,shiyanshiguimo,shiyanshizhuangtai,equipment_count) VALUES(?,?,'未知','使用中',NULL) ON DUPLICATE KEY UPDATE id=id",code,code);
            long labId=db.queryForObject("SELECT id FROM shiyanshixinxi WHERE shiyanshibianhao=?",Long.class,code);
            db.update("INSERT INTO schedule_detail(task_id,lab_id,teaching_week,weekday,period_start,period_end,hours,source_segment) VALUES(?,?,?,?,?,?,?,?)",taskId,labId,s.get("week"),s.get("weekday"),s.get("period_start"),s.get("period_end"),s.get("hours"),s.get("source_segment"));
        }
    }
    @Transactional
    public Map<String,Object> projects(HttpServletRequest request,long taskId,byte[] content) {
        access.requireTask(request,taskId,true); Long teacherId=access.teacherId(request);
        Map<String,Object> task=db.queryForMap("SELECT t.*,c.course_code FROM teaching_task t JOIN course c ON c.id=t.course_id WHERE t.id=?",taskId);
        Set<String> names=new TreeSet<>(db.queryForList("SELECT j.jiaoshixingming FROM jiaoshi j JOIN teaching_task_teacher tt ON tt.teacher_id=j.id WHERE tt.task_id=?",String.class,taskId));
        List<ExcelRow> rows=tableRows(content,PROJECT_HEADERS,true); List<String> warnings=new ArrayList<>(); int imported=0;
        for(ExcelRow r:rows) {
            try {
                required(r.at("实验名称"),"实验名称",50); required(r.at("学校代码"),"学校代码",32); required(r.at("实验所属学科"),"实验所属学科",16);
                if(!r.at("实验所属学科").matches("\\d{4,16}")) throw new IllegalArgumentException("实验所属学科应为4至16位数字代码（保留前导0）");
                code(r.at("实验类别"),"实验类别","1234"); code(r.at("实验类型"),"实验类型","12345"); code(r.at("实验要求"),"实验要求","123"); code(r.at("实验者类别"),"实验者类别","12345");
                int group=number(r.at("每组人数"),"每组人数",true).intValue(); if(group<1 || group>99) throw new IllegalArgumentException("每组人数必须为1至99");
                BigDecimal hours=number(r.at("实验学时数"),"实验学时数",false); if(hours.signum()<=0 || hours.compareTo(new BigDecimal("9999"))>0) throw new IllegalArgumentException("实验学时数必须大于0且不超过9999");
                if(!r.at("课程号").equals(task.get("course_code").toString())) throw new IllegalArgumentException("课程号与目标教学任务不一致");
                if(!r.at("课程名称").equals(task.get("course_name_snapshot").toString())) throw new IllegalArgumentException("课程名称与目标教学任务不一致");
                List<String> supplied=teacherNames(r.at("授课教师")); if(supplied.isEmpty() || !names.containsAll(supplied)) throw new IllegalArgumentException("授课教师与目标任务教师不对应");
                if(!r.at("实验总学时").isEmpty() && number(r.at("实验总学时"),"实验总学时",false).compareTo(new BigDecimal(task.get("planned_lab_hours").toString()))!=0) throw new IllegalArgumentException("实验总学时与目标任务不一致");
                if(!r.at("实验者人数").isEmpty()) { number(r.at("实验者人数"),"实验者人数",true); warnings.add(r.position()+"：实验者人数不进入人时统计，项目实际参与人数尚无独立采集字段"); }
                String projectCode=r.at("实验编号").isEmpty()?"EXP-"+UUID.randomUUID().toString().replace("-",""):required(r.at("实验编号"),"实验编号",64);
                if(db.queryForObject("SELECT COUNT(*) FROM experiment_project WHERE project_code=? OR (task_id=? AND name=?)",Long.class,projectCode,taskId,r.at("实验名称"))>0) throw new IllegalArgumentException("实验编号或该任务同名项目已存在，未自动覆盖");
                db.update("INSERT INTO experiment_project(task_id,project_code,school_code,name,category_code,type_code,discipline_code,requirement_code,participant_type_code,group_size,hours,sort_order,created_by_teacher_id,updated_by_teacher_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",taskId,projectCode,r.at("学校代码"),r.at("实验名称"),r.at("实验类别"),r.at("实验类型"),r.at("实验所属学科"),r.at("实验要求"),r.at("实验者类别"),group,hours,r.sourceRow,teacherId,teacherId); imported++;
            } catch(IllegalArgumentException e) { throw new IllegalArgumentException(r.position()+"："+e.getMessage()+"；本次文件未写入任何项目"); }
        }
        if(rows.isEmpty()) warnings.add("没有真实实验项目数据；已跳过示例、填写说明和预填空白行");
        return map("imported",imported,"warnings",warnings);
    }
    private static void code(String value,String label,String choices) { if(value.length()!=1 || !choices.contains(value)) throw new IllegalArgumentException(label+"代码不合法"); }
    static String importTermStatus(int startYear,int termNo,LocalDate now) {
        int[] current=TeachingTermService.calendarTerm(now);
        int order=Integer.compare(startYear*2+termNo,current[0]*2+current[1]);
        return order<0?"ARCHIVED":order==0?"OPEN":"DRAFT";
    }

    @Transactional
    public Map<String,Object> teachers(HttpServletRequest request,byte[] content) {
        access.requireAdmin(request); List<String> warnings=new ArrayList<>(); int imported=0;
        for(ExcelRow r:tableRows(content,TEACHER_HEADERS,false)) {
            String name=required(r.at("教师姓名"),"教师姓名",200),account=required(r.at("工号"),"工号",200);
            if(account.toUpperCase(Locale.ROOT).startsWith("TMP")) throw new IllegalArgumentException(r.position()+"：TMP前缀保留给临时账户，请填写核实后的真实工号");
            List<Map<String,Object>> existing=db.queryForList("SELECT id,jiaoshixingming FROM jiaoshi WHERE gonghao=?",account);
            if(!existing.isEmpty()) {
                if(!name.equals(existing.get(0).get("jiaoshixingming"))) throw new IllegalArgumentException(r.position()+"：该工号已属于另一姓名，请人工核对");
                db.update("UPDATE jiaoshi SET xueyuan=? WHERE id=?",r.at("学院"),existing.get(0).get("id"));
            } else {
                List<Map<String,Object>> same=db.queryForList("SELECT id,gonghao FROM jiaoshi WHERE jiaoshixingming=?",name);
                if(same.size()==1 && same.get(0).get("gonghao").toString().startsWith("TMP")) { db.update("UPDATE jiaoshi SET gonghao=?,xueyuan=? WHERE id=?",account,r.at("学院"),same.get(0).get("id")); warnings.add(name+"：已将唯一同名临时账户更新为真实工号，教学关联保留"); }
                else if(!same.isEmpty()) throw new IllegalArgumentException(r.position()+"：存在同名账户，请在教师账号页面核实身份后修改");
                else { insert("INSERT INTO jiaoshi(gonghao,mima,jiaoshixingming,xueyuan) VALUES(?,?,?,?)",account,TeachingPasswords.hash(TeachingPasswords.newPassword()),name,r.at("学院")); warnings.add(name+"：账户已创建，请在教师账号页面重置并分发初始密码"); }
            }
            imported++;
        }
        return map("imported",imported,"warnings",warnings);
    }
    @Transactional
    public Map<String,Object> labs(HttpServletRequest request,byte[] content) {
        access.requireAdmin(request); int imported=0; List<String> warnings=new ArrayList<>();
        for(ExcelRow r:tableRows(content,LAB_HEADERS,false)) {
            String labCode=required(r.at("实验室编号"),"实验室编号",200),name=required(r.at("实验室名称"),"实验室名称",200);
            if(r.at("实验室位置").length()>200) throw new IllegalArgumentException("实验室位置长度不能超过200");
            Long manager=null; Integer equipment=null;
            if(!r.at("负责人教师工号").isEmpty()) { List<Long> ids=db.queryForList("SELECT id FROM jiaoshi WHERE gonghao=?",Long.class,r.at("负责人教师工号")); if(ids.isEmpty()) throw new IllegalArgumentException(r.position()+"：负责人教师工号不存在"); manager=ids.get(0); }
            if(!r.at("设备数").isEmpty()) equipment=number(r.at("设备数"),"设备数",true).intValue();
            db.update("INSERT INTO shiyanshixinxi(shiyanshibianhao,shiyanshimingcheng,shiyanshiguimo,shiyanshizhuangtai,shiyanshiweizhi,manager_teacher_id,equipment_count) VALUES(?,?,'未知','使用中',?,?,?) ON DUPLICATE KEY UPDATE shiyanshimingcheng=VALUES(shiyanshimingcheng),shiyanshiweizhi=VALUES(shiyanshiweizhi),manager_teacher_id=VALUES(manager_teacher_id),equipment_count=VALUES(equipment_count)",labCode,name,r.at("实验室位置"),manager,equipment);
            imported++;
        }
        return map("imported",imported,"warnings",warnings);
    }
    public byte[] template(String type) {
        String[] headers; String note;
        switch(type) {
            case "projects": headers=PROJECT_HEADERS; note="按选定教学任务填写真实项目，课程号、课程名称、授课教师必须对应。实验编号空白时自动生成。类别1基础2专业基础3专业4其他；类型1演示2验证3综合4设计研究5其他；要求1必修2选修3其他；实验者1博士2硕士3本科4专科5其他；每组人数1-99；学时大于0。示例工作表不导入。学科代码请设为文本保留前导0。实验者人数不用于当前人时统计。"; break;
            case "teachers": headers=TEACHER_HEADERS; note="填写真实工号和姓名。唯一同名TMP临时账户可在此次导入更新真实工号并保留关联；其他同名冲突须人工核对。新账号密码请由管理员在教师账号页面重置分发。"; break;
            case "labs": headers=LAB_HEADERS; note="以实验室编号更新已有实验室；负责人填已存在教师的真实工号。未知负责人、设备数留空，系统保存为未知（NULL），不填写虚构数值。"; break;
            case "timetable": headers=TIMETABLE_HEADERS; note="表头与教务原课表21列完全一致。学年2025-2026，学期1或2。多个教学地点和时间按分号一一对应。时间示例：星期一第1-2节{1-16周(单)}。课程结束时间yyyy-MM-dd HH:mm:ss。禁止公式。姓名无工号时生成TMP临时账户，需管理员核对。疑似重复任务保留待核对。"; break;
            default: throw new IllegalArgumentException("不支持的模板类型");
        }
        return workbook("数据",headers,Collections.emptyList(),note);
    }
    private Map<String,Object> summary(long id) {
        Map<String,Object> count=db.queryForMap("SELECT COUNT(*) rows_count,COALESCE(SUM(status='PROMOTED'),0) promoted,COALESCE(SUM(status='ERROR'),0) errors,COALESCE(SUM(status='REVIEW'),0) review_count FROM teaching_import_row WHERE batch_id=?",id);
        List<String> warnings=new ArrayList<>();
        for(Map<String,Object> item:db.queryForList("SELECT sheet_name,source_row,issues FROM teaching_import_row WHERE batch_id=?",id)) {
            Object parsed=readJson(item.get("issues").toString());
            if(parsed instanceof List) for(Object issue:(List<?>)parsed) if(!issue.toString().startsWith("INVALID:")) warnings.add(item.get("sheet_name")+"第"+item.get("source_row")+"行："+issue);
        }
        return map("batchId",id,"rows",count.get("rows_count"),"promoted",count.get("promoted"),"errors",count.get("errors"),"review_count",count.get("review_count"),"warnings",warnings);
    }
    private long insert(String sql,Object... args) {
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        db.update(connection->{PreparedStatement ps=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); for(int i=0;i<args.length;i++) ps.setObject(i+1,args[i]); return ps;},keys);
        if(keys.getKey()==null) throw new IllegalStateException("未取得新增记录编号"); return keys.getKey().longValue();
    }
    private String toJson(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalArgumentException("无法序列化导入记录"); } }
    private Object readJson(String value) { try { return json.readValue(value,new TypeReference<Object>(){}); } catch(Exception e) { return value; } }
    private static String sha256(byte[] bytes) { try { StringBuilder out=new StringBuilder(); for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x",b)); return out.toString(); } catch(Exception e) { throw new IllegalStateException(e); } }
    private static final class Candidate { final ExcelRow row; String identity; List<Map<String,Object>> schedule; Timestamp endsAt; final List<String> issues=new ArrayList<>(); boolean error,review; Candidate(ExcelRow row){this.row=row;} }
}
