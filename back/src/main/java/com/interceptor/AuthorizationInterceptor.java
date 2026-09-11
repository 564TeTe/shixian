package com.interceptor;

import com.controller.AccountController;
import com.controller.TeachingAiController;
import com.controller.TeachingController;
import com.controller.TeachingImportController;
import com.controller.TeachingReportController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.AccountIdentity;
import com.model.response.ApiResponse;
import com.service.AccountService;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Expose the teaching application and two login endpoints only. */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    public static final String LOGIN_TOKEN_KEY = "Token";

    private static final Set<String> LOGIN_PATHS =
            new HashSet<>(Arrays.asList("/users/login", "/jiaoshi/login"));

    private static final Set<String> LOGOUT_PATHS =
            new HashSet<>(Arrays.asList("/users/logout", "/jiaoshi/logout"));

    private final AccountService accounts;

    private static final Set<Class<?>> TEACHING_CONTROLLERS =
            new HashSet<>(
                    Arrays.asList(
                            TeachingController.class,
                            TeachingImportController.class,
                            TeachingReportController.class,
                            TeachingAiController.class));

    private final ObjectMapper mapper = new ObjectMapper();

    public AuthorizationInterceptor(AccountService accounts) {
        this.accounts = accounts;
    }

    public static boolean isLogin(String path, String method) {
        return "POST".equals(method) && LOGIN_PATHS.contains(path);
    }

    public static boolean isApplicationPath(String path) {
        return path.startsWith("/teaching/") || LOGOUT_PATHS.contains(path);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod method = (HandlerMethod) handler;
        Object matchingPattern =
                request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = matchingPattern == null ? "" : matchingPattern.toString();
        Class<?> controller = method.getBeanType();
        boolean accountController = AccountController.class.equals(controller);
        boolean teachingController = TEACHING_CONTROLLERS.contains(controller);
        if (accountController && isLogin(path, request.getMethod())) {
            return true;
        }
        if (!(teachingController && path.startsWith("/teaching/"))
                && !(accountController
                        && LOGOUT_PATHS.contains(path)
                        && "POST".equals(request.getMethod()))) {
            return reject(response, 404, "此入口已停用，请使用实验教学管理页面");
        }
        String token = request.getHeader(LOGIN_TOKEN_KEY);
        AccountIdentity identity = accounts.findIdentity(token);
        if (identity == null) {
            return reject(response, 401, "请先登录");
        }
        if (!AccountService.ADMIN_ROLE.equals(identity.getRole())
                && !AccountService.TEACHER_ROLE.equals(identity.getRole())) {
            return reject(response, 403, "该角色无权访问教学系统");
        }
        String table = AccountService.ADMIN_ROLE.equals(identity.getRole()) ? "users" : "teacher";
        request.getSession().setAttribute("userId", identity.getAccountId());
        request.getSession().setAttribute("role", "users".equals(table) ? "管理员" : "教师");
        request.getSession().setAttribute("tableName", table);
        request.getSession().setAttribute("username", identity.getUsername());
        return true;
    }

    private boolean reject(HttpServletResponse response, int code, String message)
            throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
        return false;
    }
}
