package com.model.response;

import java.util.HashMap;
import java.util.Map;

/** 返回数据 */
public class ApiResponse extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    public ApiResponse() {
        put("code", 0);
    }

    public static ApiResponse error() {
        return error(500, "未知异常，请联系管理员");
    }

    public static ApiResponse error(String msg) {
        return error(500, msg);
    }

    public static ApiResponse error(int code, String msg) {
        ApiResponse r = new ApiResponse();
        r.put("code", code);
        r.put("msg", msg);
        return r;
    }

    public static ApiResponse ok(String msg) {
        ApiResponse r = new ApiResponse();
        r.put("msg", msg);
        return r;
    }

    public static ApiResponse ok(Map<String, Object> map) {
        ApiResponse r = new ApiResponse();
        r.putAll(map);
        return r;
    }

    public static ApiResponse ok() {
        return new ApiResponse();
    }

    public ApiResponse put(String key, Object value) {
        super.put(key, value);
        return this;
    }
}
