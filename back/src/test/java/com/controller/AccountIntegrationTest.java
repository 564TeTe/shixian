package com.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.model.AccountIdentity;
import com.service.AccountService;
import com.utils.TeachingPasswords;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Real schema and HTTP contracts; all account, token and password writes roll back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountService accounts;
    @Autowired private MockMvc mvc;

    private String createAccount(String role) {
        String username = "TEST" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update(
                "INSERT INTO account(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                username,
                TeachingPasswords.hash("TestPassword928!"),
                "事务测试账号",
                role);
        return username;
    }

    @Test
    void selectedRoleAndExpiryAreEnforcedAndLogoutRevokesToken() throws Exception {
        String username = createAccount("TEACHER");
        assertNull(accounts.login(username, "TestPassword928!", "ADMIN"));
        assertNull(accounts.login(username, "incorrect", "TEACHER"));
        String token = accounts.login(username, "TestPassword928!", "TEACHER");
        AccountIdentity identity = accounts.findIdentity(token);
        assertNotNull(identity);
        mvc.perform(get("/teaching/lookups").header("Token", token))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(
                        post("/teaching/teachers/" + identity.getAccountId() + "/reset-password")
                                .header("Token", token))
                .andExpect(jsonPath("$.code").value(403));
        jdbc.update(
                "UPDATE token SET expires_at=? WHERE token=?",
                Timestamp.from(Instant.EPOCH),
                token);
        assertNull(accounts.findIdentity(token));
        String replacement = accounts.login(username, "TestPassword928!", "TEACHER");
        mvc.perform(post("/jiaoshi/logout").header("Token", replacement))
                .andExpect(jsonPath("$.code").value(0));
        assertNull(accounts.findIdentity(replacement));
    }

    @Test
    void administratorResetUsesNewAccountSchemaAndInvalidatesTeacherSession() throws Exception {
        String administrator = createAccount("ADMIN");
        String teacher = createAccount("TEACHER");
        String adminToken = accounts.login(administrator, "TestPassword928!", "ADMIN");
        for (String route :
                new String[] {
                    "/teaching/lookups",
                    "/teaching/imports",
                    "/teaching/reports",
                    "/teaching/ai/status"
                }) {
            mvc.perform(get(route).header("Token", adminToken))
                    .andExpect(jsonPath("$.code").value(0));
        }
        mvc.perform(get("/teaching/reports/export").header("Token", adminToken))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        org.hamcrest.Matchers.containsString("attachment")));
        String teacherToken = accounts.login(teacher, "TestPassword928!", "TEACHER");
        long teacherId = accounts.findIdentity(teacherToken).getAccountId();
        String response =
                mvc.perform(
                                post("/teaching/teachers/" + teacherId + "/reset-password")
                                        .header("Token", adminToken))
                        .andExpect(jsonPath("$.code").value(0))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String password =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(response)
                        .path("data")
                        .path("password")
                        .asText();
        assertNotNull(accounts.login(teacher, password, "TEACHER"));
        assertNull(accounts.findIdentity(teacherToken));
        assertNull(accounts.login(teacher, "TestPassword928!", "TEACHER"));
        long adminId = accounts.findIdentity(adminToken).getAccountId();
        mvc.perform(
                        post("/teaching/teachers/" + adminId + "/reset-password")
                                .header("Token", adminToken))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void legacyEndpointsAreAbsentAndBothLoginFormsKeepTheirContract() throws Exception {
        String username = createAccount("ADMIN");
        mvc.perform(
                        post("/users/login")
                                .param("username", username)
                                .param("password", "TestPassword928!"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.token").isString());
        mvc.perform(
                        post("/jiaoshi/login")
                                .param("username", username)
                                .param("password", "TestPassword928!"))
                .andExpect(jsonPath("$.code").value(500));
        for (String route :
                new String[] {
                    "/users/resetPass",
                    "/jiaoshi/register",
                    "/shiyanshiyuyue/page",
                    "/file/upload",
                    "/users/update"
                }) {
            mvc.perform(post(route)).andExpect(status().is4xxClientError());
        }
    }
}
