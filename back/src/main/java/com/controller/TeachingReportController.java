package com.controller;

import com.model.response.ApiResponse;
import com.service.TeachingReportService;
import com.utils.ExcelDownloadUtils;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/teaching/reports")
public class TeachingReportController {

    private final TeachingReportService service;

    public TeachingReportController(TeachingReportService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse report(
            HttpServletRequest r,
            @RequestParam(required = false) Long yearId,
            @RequestParam(required = false) Long termId) {
        return ApiResponse.ok().put("data", service.report(r, yearId, termId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            HttpServletRequest r,
            @RequestParam(required = false) Long yearId,
            @RequestParam(required = false) Long termId,
            @RequestParam(defaultValue = "labs") String type) {
        return ExcelDownloadUtils.download(
                service.export(r, yearId, termId, type), "teaching-report-" + type + ".xlsx");
    }
}
