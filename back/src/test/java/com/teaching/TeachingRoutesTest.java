package com.teaching;

import com.interceptor.AuthorizationInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import com.controller.UserController;
import static org.junit.jupiter.api.Assertions.*;

class TeachingRoutesTest {
    @Test void authorizationUsesResolvedRouteInsteadOfRawPrefix() throws Exception {
        for (String raw : new String[]{"/teaching/../users/list", "/teaching/%2e%2e/users/list"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", raw);
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/users/list");
            MockHttpServletResponse response = new MockHttpServletResponse();
            HandlerMethod handler = new HandlerMethod(new UserController(),
                java.util.Arrays.stream(UserController.class.getDeclaredMethods()).filter(m -> m.getName().equals("login")).findFirst().get());
            assertFalse(new AuthorizationInterceptor().preHandle(request, response, handler));
            assertTrue(response.getContentAsString().contains("404"));
        }
    }
    @Test void oldAnonymousResetAndGenericDatabaseRoutesAreClosed() {
        for (String path : new String[]{"/users/resetPass", "/jiaoshi/resetPass", "/users/register",
                "/shiyankecheng/detail/1", "/option/jiaoshi/gonghao", "/option/teacher/teacher_no", "/users/update", "/jiaoshi/info/1"}) {
            assertFalse(AuthorizationInterceptor.isApplicationPath(path), path);
            assertFalse(AuthorizationInterceptor.isLogin(path, "POST"), path);
        }
        assertTrue(AuthorizationInterceptor.isApplicationPath("/teaching/tasks"));
        assertTrue(AuthorizationInterceptor.isLogin("/users/login", "POST"));
        assertFalse(AuthorizationInterceptor.isLogin("/users/login", "GET"));
    }
}
