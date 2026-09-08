package com.teaching;

import com.utils.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/teaching/reports")
public class TeachingReportController {
    private final TeachingReportService service;
    public TeachingReportController(TeachingReportService service) { this.service=service; }
    @GetMapping public R report(HttpServletRequest r,@RequestParam(required=false) Long yearId,@RequestParam(required=false) Long termId) { return R.ok().put("data",service.report(r,yearId,termId)); }
    @GetMapping("/export") public ResponseEntity<byte[]> export(HttpServletRequest r,@RequestParam(required=false) Long yearId,@RequestParam(required=false) Long termId,@RequestParam(defaultValue="labs") String type) { return TeachingImportController.download(service.export(r,yearId,termId,type),"teaching-report-"+type+".xlsx"); }
}
