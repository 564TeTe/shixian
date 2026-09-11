package com.controller;

import com.model.response.ApiResponse;
import com.service.AccountService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/** Keep existing frontend login URLs while using one account service internally. */
@RestController
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/users/login")
    public ApiResponse loginAdministrator(
            @RequestParam String username, @RequestParam String password) {
        return login(username, password, AccountService.ADMIN_ROLE);
    }

    @PostMapping("/jiaoshi/login")
    public ApiResponse loginTeacher(@RequestParam String username, @RequestParam String password) {
        return login(username, password, AccountService.TEACHER_ROLE);
    }

    private ApiResponse login(String username, String password, String role) {
        String token = accounts.login(username, password, role);
        return token == null ? ApiResponse.error("账号或密码不正确") : ApiResponse.ok().put("token", token);
    }

    @PostMapping({"/users/logout", "/jiaoshi/logout"})
    public ApiResponse logout(HttpServletRequest request) {
        accounts.logout(request.getHeader("Token"));
        request.getSession().invalidate();
        return ApiResponse.ok("退出成功");
    }
}
