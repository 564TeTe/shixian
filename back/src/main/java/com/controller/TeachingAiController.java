package com.controller;

import com.model.response.ApiResponse;
import com.service.TeachingAiService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/teaching/ai")
public class TeachingAiController {

    private final TeachingAiService service;

    public TeachingAiController(TeachingAiService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ApiResponse status(HttpServletRequest request) {
        return ApiResponse.ok().put("data", service.status(request));
    }

    @PostMapping("/query")
    public ApiResponse query(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().put("data", service.query(request, body));
    }
}
