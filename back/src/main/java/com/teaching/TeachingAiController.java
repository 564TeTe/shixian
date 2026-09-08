package com.teaching;

import com.utils.R;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/teaching/ai")
public class TeachingAiController {
    private final TeachingAiService service;
    public TeachingAiController(TeachingAiService service) { this.service=service; }
    @GetMapping("/status") public R status(HttpServletRequest request) { return R.ok().put("data",service.status(request)); }
    @PostMapping("/query") public R query(HttpServletRequest request,@RequestBody Map<String,Object> body) { return R.ok().put("data",service.query(request,body)); }
}
