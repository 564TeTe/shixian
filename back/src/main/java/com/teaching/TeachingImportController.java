package com.teaching;

import com.utils.R;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/teaching")
public class TeachingImportController {
    private final TeachingImportService service;
    private final TeachingAccess access;
    public TeachingImportController(TeachingImportService service,TeachingAccess access) { this.service=service; this.access=access; }
    @GetMapping("/imports") public R list(HttpServletRequest r) { return R.ok().put("data",service.list(r)); }
    @GetMapping("/imports/{id}") public R detail(HttpServletRequest r,@PathVariable long id) { return R.ok().put("data",service.detail(r,id)); }
    @PostMapping("/imports/{batchId}/rows/{sourceRow}/confirm") public R confirm(HttpServletRequest r,@PathVariable long batchId,@PathVariable int sourceRow,@RequestParam String sheet,@RequestBody java.util.Map<String,Object> body) { return R.ok().put("data",service.confirm(r,batchId,sourceRow,sheet,Boolean.TRUE.equals(body.get("confirmDistinctTask")))); }
    @PostMapping("/imports/timetable") public R timetable(HttpServletRequest r,@RequestParam MultipartFile file) { access.requireAdmin(r); return R.ok().put("data",service.timetable(r,TeachingExcel.bytes(file),file.getOriginalFilename())); }
    @PostMapping("/imports/projects") public R projects(HttpServletRequest r,@RequestParam long taskId,@RequestParam MultipartFile file) { access.requireTask(r,taskId,true); return R.ok().put("data",service.projects(r,taskId,TeachingExcel.bytes(file))); }
    @PostMapping("/imports/teachers") public R teachers(HttpServletRequest r,@RequestParam MultipartFile file) { access.requireAdmin(r); return R.ok().put("data",service.teachers(r,TeachingExcel.bytes(file))); }
    @PostMapping("/imports/labs") public R labs(HttpServletRequest r,@RequestParam MultipartFile file) { access.requireAdmin(r); return R.ok().put("data",service.labs(r,TeachingExcel.bytes(file))); }
    @GetMapping("/templates/{type}") public ResponseEntity<byte[]> template(HttpServletRequest r,@PathVariable String type) {
        access.teacherId(r); if(!"projects".equals(type)) access.requireAdmin(r);
        return download(service.template(type),"teaching-"+type+"-template.xlsx");
    }
    static ResponseEntity<byte[]> download(byte[] bytes,String name) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").body(bytes);
    }
}
