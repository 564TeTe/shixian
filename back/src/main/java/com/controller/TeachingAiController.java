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

/**
 * 获取系统状态接口
 * @param request HTTP请求对象，用于获取请求相关信息
 * @return 返回API响应对象，包含系统状态数据
 */
    @GetMapping("/status")
    public ApiResponse status(HttpServletRequest request) {
    // 调用service层的status方法获取状态数据，并封装到API响应中返回
        return ApiResponse.ok().put("data", service.status(request));
    }

    @PostMapping("/query")
    public ApiResponse query(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().put("data", service.query(request, body));
    }
}
