package com.interceptor;

import com.entity.TokenEntity;
import com.service.TokenService;
import com.utils.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** Expose the teaching application and two login endpoints only. */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {
    public static final String LOGIN_TOKEN_KEY = "Token";
    private static final Set<String> LOGIN_PATHS = new HashSet<>(Arrays.asList("/users/login", "/jiaoshi/login"));
    private static final Set<String> LOGOUT_PATHS = new HashSet<>(Arrays.asList("/users/logout", "/jiaoshi/logout"));
    @Autowired private TokenService tokenService;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public static boolean isLogin(String path, String method) {
        return "POST".equals(method) && LOGIN_PATHS.contains(path);
    }

    public static boolean isApplicationPath(String path) {
        return path.startsWith("/teaching/") || LOGOUT_PATHS.contains(path);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) return true;
        HandlerMethod method = (HandlerMethod) handler;
        Object matchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = matchingPattern == null ? "" : matchingPattern.toString();
        String controller = method.getBeanType().getName();
        boolean accountController = controller.equals("com.controller.UserController") || controller.equals("com.controller.JiaoshiController");
        boolean teachingController = new HashSet<>(Arrays.asList("com.teaching.TeachingController", "com.teaching.TeachingImportController",
            "com.teaching.TeachingReportController", "com.teaching.TeachingAiController")).contains(controller);
        if (accountController && isLogin(path, request.getMethod())) return true;
        if (!(teachingController && path.startsWith("/teaching/")) &&
            !(accountController && LOGOUT_PATHS.contains(path) && "POST".equals(request.getMethod())))
            return reject(response, 404, "此入口已停用，请使用实验教学管理页面");
        String token = request.getHeader(LOGIN_TOKEN_KEY);
        TokenEntity identity = token == null || token.trim().isEmpty() ? null : tokenService.getTokenEntity(token);
        if (identity == null) return reject(response, 401, "请先登录");
        String table = identity.getTablename();
        if (!"users".equals(table) && !"jiaoshi".equals(table)) return reject(response, 403, "该角色无权访问教学系统");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=?", Integer.class, identity.getUserid());
        if (count == null || count != 1) return reject(response, 401, "账号已失效，请重新登录");
        request.getSession().setAttribute("userId", identity.getUserid());
        request.getSession().setAttribute("role", "users".equals(table) ? "管理员" : "教师");
        request.getSession().setAttribute("tableName", table);
        request.getSession().setAttribute("username", identity.getUsername());
        return true;
    }

    private boolean reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), R.error(code, message));
        return false;
    }
}
