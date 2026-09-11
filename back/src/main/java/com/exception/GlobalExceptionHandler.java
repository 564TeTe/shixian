package com.exception;

import com.model.response.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Shared error responses for teaching and authentication endpoints. */
@RestControllerAdvice(basePackages = "com.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessException.class)
    public ApiResponse denied(AccessException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse invalid(IllegalArgumentException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ApiResponse malformed(Exception e) {
        return ApiResponse.error(400, "请求参数格式不正确或缺少必填字段");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResponse conflict(DataIntegrityViolationException e) {
        return ApiResponse.error(409, "编号重复、数据超出范围或记录仍有关联，请检查后重试");
    }

    @ExceptionHandler(DataAccessException.class)
    public ApiResponse database(DataAccessException e) {
        LOG.error("Teaching database operation failed", e);
        return ApiResponse.error(500, "数据库操作失败，请联系管理员查看服务日志");
    }
}
