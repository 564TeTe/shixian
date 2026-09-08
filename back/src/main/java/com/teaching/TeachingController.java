package com.teaching;

import com.utils.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/teaching")
public class TeachingController {
    private final TeachingService service;
    private final TeachingAccess access;
    private final TeachingTermService terms;
    public TeachingController(TeachingService service,TeachingAccess access,TeachingTermService terms) {
        this.service=service;this.access=access;this.terms=terms;
    }
    @GetMapping("/lookups") public R lookups(HttpServletRequest r){return R.ok().put("data",service.lookups(r));}
    @GetMapping("/dashboard") public R dashboard(HttpServletRequest r){return R.ok().put("data",service.dashboard(r));}
    @GetMapping("/terms") public R terms(HttpServletRequest r){return R.ok().put("data",terms.terms(access.teacherId(r)));}
    @PostMapping("/terms/generate") public R generate(HttpServletRequest r){access.requireAdmin(r);return R.ok().put("data",terms.ensureCurrentTerm());}
    @GetMapping("/tasks") public R tasks(HttpServletRequest r,@RequestParam(required=false)Long termId,
        @RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int limit){
        return R.ok().put("data",service.tasks(r,termId,q,page,limit));
    }
    @GetMapping("/tasks/{id}") public R task(HttpServletRequest r,@PathVariable long id){return R.ok().put("data",service.task(r,id));}
    @PostMapping("/tasks") public R createTask(HttpServletRequest r,@RequestBody Map<String,Object> body){return R.ok().put("data",service.createTask(r,body));}
    @GetMapping("/projects") public R projects(HttpServletRequest r,@RequestParam long taskId){return R.ok().put("data",service.projects(r,taskId));}
    @PostMapping("/projects") public R createProject(HttpServletRequest r,@RequestBody Map<String,Object> body){return R.ok().put("data",service.createProject(r,body));}
    @PutMapping("/projects/{id}") public R updateProject(HttpServletRequest r,@PathVariable long id,@RequestBody Map<String,Object> body){return R.ok().put("data",service.updateProject(r,id,body));}
    @DeleteMapping("/projects/{id}") public R deleteProject(HttpServletRequest r,@PathVariable long id){service.deleteProject(r,id);return R.ok();}
    @PostMapping("/projects/copy") public R copyProjects(HttpServletRequest r,@RequestBody Map<String,Object> body){
        return R.ok().put("data",service.copyProjects(r,TeachingService.positiveId(body,"sourceTaskId"),TeachingService.positiveId(body,"targetTaskId")));
    }
    @GetMapping("/labs") public R labs(HttpServletRequest r,@RequestParam(defaultValue="")String q){return R.ok().put("data",service.labs(r,q));}
    @PostMapping("/labs") public R createLab(HttpServletRequest r,@RequestBody Map<String,Object> body){return R.ok().put("data",service.saveLab(r,null,body));}
    @PutMapping("/labs/{id}") public R updateLab(HttpServletRequest r,@PathVariable long id,@RequestBody Map<String,Object> body){return R.ok().put("data",service.saveLab(r,id,body));}
    @DeleteMapping("/labs/{id}") public R deleteLab(HttpServletRequest r,@PathVariable long id){service.deleteLab(r,id);return R.ok();}
    @GetMapping("/teachers") public R teachers(HttpServletRequest r,@RequestParam(defaultValue="")String q){return R.ok().put("data",service.teachers(r,q));}
    @PostMapping("/teachers") public R createTeacher(HttpServletRequest r,@RequestBody Map<String,Object> body){return R.ok().put("data",service.saveTeacher(r,null,body));}
    @PutMapping("/teachers/{id}") public R updateTeacher(HttpServletRequest r,@PathVariable long id,@RequestBody Map<String,Object> body){return R.ok().put("data",service.saveTeacher(r,id,body));}
    @DeleteMapping("/teachers/{id}") public R deleteTeacher(HttpServletRequest r,@PathVariable long id){service.deleteTeacher(r,id);return R.ok();}
    @PostMapping("/teachers/{id}/reset-password") public R reset(HttpServletRequest r,@PathVariable long id){return R.ok().put("data",service.resetTeacherPassword(r,id));}
    @PostMapping("/account/password") public R password(HttpServletRequest r,@RequestBody Map<String,Object> body){service.changePassword(r,body);return R.ok("密码已更新");}

    /** Includes import/report/AI controllers while leaving the legacy API exception policy untouched. */
    @RestControllerAdvice(basePackages="com.teaching")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class TeachingErrors {
        private static final Logger LOG=LoggerFactory.getLogger(TeachingErrors.class);
        @ExceptionHandler(TeachingAccess.AccessException.class)
        public R denied(TeachingAccess.AccessException e){return R.error(e.getCode(),e.getMessage());}
        @ExceptionHandler(IllegalArgumentException.class)
        public R invalid(IllegalArgumentException e){return R.error(400,e.getMessage());}
        @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
        public R malformed(Exception e){return R.error(400,"请求参数格式不正确或缺少必填字段");}
        @ExceptionHandler(DataIntegrityViolationException.class)
        public R conflict(DataIntegrityViolationException e){return R.error(409,"编号重复、数据超出范围或记录仍有关联，请检查后重试");}
        @ExceptionHandler(DataAccessException.class)
        public R database(DataAccessException e){LOG.error("Teaching database operation failed",e);return R.error(500,"数据库操作失败，请联系管理员查看服务日志");}
    }
}
