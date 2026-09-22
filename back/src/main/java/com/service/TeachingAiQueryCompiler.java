package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.TeachingAiSqlGuard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parses the AI response containing a single SQL statement. */
final class TeachingAiQueryCompiler {

    static final int MAX_LIMIT = 200;

/**
 * 编译SQL查询方法
 * 该方法接收一个JSON对象映射器和模型输出字符串，解析后返回一个编译后的查询对象
 *
 * @param json ObjectMapper对象，用于处理JSON数据
 * @param modelOutput AI模型返回的JSON格式字符串
 * @return CompiledQuery 编译后的查询对象
 * @throws IllegalArgumentException 当AI返回内容缺少SQL字段时抛出
 */
    CompiledQuery modelSql(ObjectMapper json, String modelOutput) {
    // 从JSON响应中读取数据
        JsonNode response = readJson(json, modelOutput);
    // 获取SQL字段节点
        JsonNode sqlNode = response.get("sql");
    // 检查SQL字段是否存在且为文本类型
        if (sqlNode == null || !sqlNode.isTextual()) {
            throw new IllegalArgumentException("AI返回内容缺少SQL字段");
        }
    // 验证SQL语句的安全性并去除前后空格
        String safeSql = TeachingAiSqlGuard.validate(sqlNode.asText().trim());
    // 创建并返回编译后的查询对象
        return CompiledQuery.model(safeSql);
    }

    String sqlInstructions(String metadata) {
        return "你是数据库自然语言查询助手。请根据用户问题生成一条可执行的MySQL SELECT查询，"
                + "只返回JSON对象，不要使用Markdown，不要解释。JSON格式必须是"
                + "{\"sql\":\"SELECT ...\"}。只能生成一条SELECT，禁止INSERT、UPDATE、DELETE、DDL、"
                + "存储过程、变量、注释、UNION、子查询、CTE和多语句。请使用表中真实存在的字段，"
                + "只查询回答问题所需的最少列和行，结果最多200行。列别名使用英文蛇形命名，"
                + "不要使用中文标识符或反引号。不要查询任何密码、令牌或密钥字段。"
                + "如果问题涉及名称，优先使用LIKE进行模糊匹配；无法回答时返回{\"sql\":\"\"}。"
                + "\n数据库结构：\n"
                + metadata;
    }

    private static JsonNode readJson(ObjectMapper json, String modelOutput) {
        if (modelOutput == null || modelOutput.trim().isEmpty()) {
            throw new IllegalArgumentException("AI没有返回SQL");
        }
        String text = modelOutput.trim();
        if (text.startsWith("```")) {
            text =
                    text.replaceFirst("^```(?:json|JSON)?\\s*", "")
                            .replaceFirst("\\s*```$", "")
                            .trim();
        }
        if (!text.startsWith("{") || !text.endsWith("}")) {
            throw new IllegalArgumentException("AI返回内容不是JSON对象");
        }
        try {
            JsonNode result = json.readTree(text);
            if (!result.isObject()) {
                throw new IllegalArgumentException("AI返回内容不是JSON对象");
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("AI返回的JSON无法解析", e);
        }
    }

    static final class CompiledQuery {
        private final String sql;
        private final String executionSql;
        private final List<Object> parameters;
        private final int resultLimit;

        private CompiledQuery(
                String sql,
                String executionSql,
                List<Object> parameters,
                int resultLimit) {
            this.sql = sql;
            this.executionSql = executionSql;
            this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
            this.resultLimit = resultLimit;
        }

        static CompiledQuery model(String sql) {
            return new CompiledQuery(
                    sql, sql, Collections.emptyList(), MAX_LIMIT);
        }

        static CompiledQuery fixed(
                String sql,
                String executionSql,
                List<Object> parameters,
                int resultLimit) {
            return new CompiledQuery(sql, executionSql, parameters, resultLimit);
        }

        String getSql() {
            return sql;
        }

        String getExecutionSql() {
            return executionSql;
        }

        List<Object> getParameters() {
            return parameters;
        }

        int getResultLimit() {
            return resultLimit;
        }
    }
}
