package com.controller;

import com.model.response.ApiResponse;
import com.security.TeachingAccess;
import com.service.TeachingService;
import com.service.TeachingTermService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/teaching")
public class TeachingController {

    private final TeachingService service;

    private final TeachingAccess access;

    private final TeachingTermService terms;

    public TeachingController(
            TeachingService service, TeachingAccess access, TeachingTermService terms) {
        this.service = service;
        this.access = access;
        this.terms = terms;
    }

    @GetMapping("/lookups")
    public ApiResponse lookups(HttpServletRequest r) {
        return ApiResponse.ok().put("data", service.lookups(r));
    }

    @GetMapping("/dashboard")
    public ApiResponse dashboard(HttpServletRequest r) {
        return ApiResponse.ok().put("data", service.dashboard(r));
    }

    @GetMapping("/terms")
    public ApiResponse terms(HttpServletRequest r) {
        return ApiResponse.ok().put("data", terms.terms(access.teacherId(r)));
    }

    @PostMapping("/terms/generate")
    public ApiResponse generate(HttpServletRequest r) {
        access.requireAdmin(r);
        return ApiResponse.ok().put("data", terms.ensureCurrentTerm());
    }

    @GetMapping("/tasks")
    public ApiResponse tasks(
            HttpServletRequest r,
            @RequestParam(required = false) Long termId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok().put("data", service.tasks(r, termId, q, page, limit));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse task(HttpServletRequest r, @PathVariable long id) {
        return ApiResponse.ok().put("data", service.task(r, id));
    }

    @PostMapping("/tasks")
    public ApiResponse createTask(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().put("data", service.createTask(r, body));
    }

    @GetMapping("/projects")
    public ApiResponse projects(HttpServletRequest r, @RequestParam long taskId) {
        return ApiResponse.ok().put("data", service.projects(r, taskId));
    }

    @PostMapping("/projects")
    public ApiResponse createProject(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().put("data", service.createProject(r, body));
    }

    @PutMapping("/projects/{id}")
    public ApiResponse updateProject(
            HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().put("data", service.updateProject(r, id, body));
    }

    @DeleteMapping("/projects/{id}")
    public ApiResponse deleteProject(HttpServletRequest r, @PathVariable long id) {
        service.deleteProject(r, id);
        return ApiResponse.ok();
    }

    @PostMapping("/projects/copy")
    public ApiResponse copyProjects(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok()
                .put(
                        "data",
                        service.copyProjects(
                                r,
                                TeachingService.positiveId(body, "sourceTaskId"),
                                TeachingService.positiveId(body, "targetTaskId")));
    }

    @GetMapping("/labs")
    public ApiResponse labs(HttpServletRequest r, @RequestParam(defaultValue = "") String q) {
        return ApiResponse.ok().put("data", service.labs(r, q));
    }

    @PostMapping("/labs")
    public ApiResponse createLab(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().put("data", service.saveLab(r, null, body));
    }

    @PutMapping("/labs/{id}")
    public ApiResponse updateLab(
            HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().put("data", service.saveLab(r, id, body));
    }

    @DeleteMapping("/labs/{id}")
    public ApiResponse deleteLab(HttpServletRequest r, @PathVariable long id) {
        service.deleteLab(r, id);
        return ApiResponse.ok();
    }

    @GetMapping("/teachers")
    public ApiResponse teachers(HttpServletRequest r, @RequestParam(defaultValue = "") String q) {
        return ApiResponse.ok().put("data", service.teachers(r, q));
    }

    @PostMapping("/teachers")
    public ApiResponse createTeacher(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().put("data", service.saveTeacher(r, null, body));
    }

    @PutMapping("/teachers/{id}")
    public ApiResponse updateTeacher(
            HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().put("data", service.saveTeacher(r, id, body));
    }

    @DeleteMapping("/teachers/{id}")
    public ApiResponse deleteTeacher(HttpServletRequest r, @PathVariable long id) {
        service.deleteTeacher(r, id);
        return ApiResponse.ok();
    }

    @PostMapping("/teachers/{id}/reset-password")
    public ApiResponse reset(HttpServletRequest r, @PathVariable long id) {
        return ApiResponse.ok().put("data", service.resetTeacherPassword(r, id));
    }

    @PostMapping("/account/password")
    public ApiResponse password(HttpServletRequest r, @RequestBody Map<String, Object> body) {
        service.changePassword(r, body);
        return ApiResponse.ok("密码已更新");
    }
}
