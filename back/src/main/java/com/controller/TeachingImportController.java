package com.controller;

import com.model.response.ApiResponse;
import com.security.TeachingAccess;
import com.service.TeachingImportService;
import com.utils.ExcelDownloadUtils;
import com.utils.TeachingExcel;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/teaching")
public class TeachingImportController {

    private final TeachingImportService service;

    private final TeachingAccess access;

    public TeachingImportController(TeachingImportService service, TeachingAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/imports")
    public ApiResponse list(HttpServletRequest r) {
        return ApiResponse.ok().put("data", service.list(r));
    }

    @GetMapping("/imports/{id}")
    public ApiResponse detail(HttpServletRequest r, @PathVariable long id) {
        return ApiResponse.ok().put("data", service.detail(r, id));
    }

    @PostMapping("/imports/{batchId}/rows/{sourceRow}/confirm")
    public ApiResponse confirm(
            HttpServletRequest r,
            @PathVariable long batchId,
            @PathVariable int sourceRow,
            @RequestParam String sheet,
            @RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok()
                .put(
                        "data",
                        service.confirm(
                                r,
                                batchId,
                                sourceRow,
                                sheet,
                                Boolean.TRUE.equals(body.get("confirmDistinctTask"))));
    }

    @PostMapping("/imports/timetable")
    public ApiResponse timetable(HttpServletRequest r, @RequestParam MultipartFile file) {
        access.requireAdmin(r);
        return ApiResponse.ok()
                .put(
                        "data",
                        service.timetable(
                                r, TeachingExcel.bytes(file), file.getOriginalFilename()));
    }

    @PostMapping("/imports/projects")
    public ApiResponse projects(
            HttpServletRequest r, @RequestParam long taskId, @RequestParam MultipartFile file) {
        access.requireTask(r, taskId, true);
        return ApiResponse.ok().put("data", service.projects(r, taskId, TeachingExcel.bytes(file)));
    }

    @PostMapping("/imports/teachers")
    public ApiResponse teachers(HttpServletRequest r, @RequestParam MultipartFile file) {
        access.requireAdmin(r);
        return ApiResponse.ok().put("data", service.teachers(r, TeachingExcel.bytes(file)));
    }

    @PostMapping("/imports/labs")
    public ApiResponse labs(HttpServletRequest r, @RequestParam MultipartFile file) {
        access.requireAdmin(r);
        return ApiResponse.ok().put("data", service.labs(r, TeachingExcel.bytes(file)));
    }

    @GetMapping("/templates/{type}")
    public ResponseEntity<byte[]> template(HttpServletRequest r, @PathVariable String type) {
        access.teacherId(r);
        if (!"projects".equals(type)) {
            access.requireAdmin(r);
        }
        return ExcelDownloadUtils.download(
                service.template(type), "teaching-" + type + "-template.xlsx");
    }
}
