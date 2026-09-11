package com.service;

import com.utils.TeachingPasswords;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Properties;
import java.util.UUID;

/**
 * Opt-in database tests use explicit environment settings or the local application configuration.
 */
final class TeachingTestDatabase {
    private TeachingTestDatabase() {}

    static long createAccount(JdbcTemplate jdbc, String role) {
        String username = "TEST" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update(
                "INSERT INTO account(username,password_hash,display_name,role) VALUES (?,?,?,?)",
                username,
                TeachingPasswords.hash("TestPassword928!"),
                username,
                role);
        return jdbc.queryForObject("SELECT id FROM account WHERE username=?", Long.class, username);
    }

    static long createCourse(JdbcTemplate jdbc) {
        String code = "TEST" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO course(course_code,course_name) VALUES (?,?)", code, "事务测试课程");
        return jdbc.queryForObject("SELECT id FROM course WHERE course_code=?", Long.class, code);
    }

    static long createLab(JdbcTemplate jdbc) {
        String code = "TEST" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO laboratory(lab_code,lab_name) VALUES (?,?)", code, "事务测试实验室");
        return jdbc.queryForObject("SELECT id FROM laboratory WHERE lab_code=?", Long.class, code);
    }

    static DriverManagerDataSource dataSource() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties settings = yaml.getObject();
        return new DriverManagerDataSource(
                setting(settings, "TEACHING_TEST_DB_URL", "spring.datasource.url"),
                setting(settings, "TEACHING_TEST_DB_USER", "spring.datasource.username"),
                setting(settings, "TEACHING_TEST_DB_PASSWORD", "spring.datasource.password"));
    }

    private static String setting(Properties settings, String variable, String property) {
        String override = System.getenv(variable);
        return override == null ? settings.getProperty(property) : override;
    }
}
