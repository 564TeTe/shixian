package com.security;

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/** Fails closed: only a small, auditable SELECT AST is executable. */
/**
 * TeachingAiSqlGuard 类是一个SQL查询安全防护类，主要用于验证和限制SQL查询的执行。
 * 该类采用"失败关闭"策略，只允许执行经过验证的、安全的SELECT查询。
 */
public final class TeachingAiSqlGuard {
    /**
     * 定义允许访问的表名集合
     * 使用HashSet存储，包含系统中允许查询的所有表名
     */
    private static final Set<String> TABLES =
            new HashSet<>(
                    Arrays.asList(
                            "account",
                            "academic_term",
                            "ai_teacher_workload",
                            "course",
                            "experiment_project",
                            "laboratory",
                            "schedule_detail",
                            "teaching_import_batch",
                            "teaching_import_row",
                            "teaching_task",
                            "teaching_task_teacher",
                            "token"));

    /**
     * 定义禁止访问的敏感列名集合
     * 包含密码、密钥等敏感信息字段
     */
    private static final Set<String> BLOCKED_COLUMNS =
            new HashSet<>(
                    Arrays.asList(
                            "password",
                            "password_hash",
                            "token",
                            "api_key",
                            "secret",
                            "access_key"));

    /**
     * 定义允许使用的SQL函数集合
     * 包含常用的聚合函数、字符串函数、日期函数等
     */
    private static final Set<String> FUNCTIONS =
            new HashSet<>(
                    Arrays.asList(
                            "COUNT",
                            "SUM",
                            "AVG",
                            "MIN",
                            "MAX",
                            "ROUND",
                            "ABS",
                            "COALESCE",
                            "IFNULL",
                            "NULLIF",
                            "CONCAT",
                            "CONCAT_WS",
                            "LOWER",
                            "UPPER",
                            "LENGTH",
                            "CHAR_LENGTH",
                            "YEAR",
                            "MONTH",
                            "DAY",
                            "DATE",
                            "DATE_FORMAT"));

    /**
     * 定义允许使用的二元操作符集合
     * 包含算术运算符、比较运算符、逻辑运算符等
     */
    private static final Set<String> BINARY =
            new HashSet<>(
                    Arrays.asList(
                            "Addition",
                            "Subtraction",
                            "Multiplication",
                            "Division",
                            "Modulo",
                            "EqualsTo",
                            "NotEqualsTo",
                            "GreaterThan",
                            "GreaterThanEquals",
                            "MinorThan",
                            "MinorThanEquals",
                            "AndExpression",
                            "OrExpression",
                            "LikeExpression"));

/**
 * 获取允许访问的表名集合的不可修改视图
 * 该方法返回一个不可修改的Set集合，包含所有允许访问的表名
 *
 * @return 返回一个包含允许访问表名的不可修改Set集合
 *         使用Collections.unmodifiableSet确保返回的集合不能被修改
 */
    public static Set<String> allowedTables() {
        // 返回TABLES集合的不可修改视图
        return java.util.Collections.unmodifiableSet(TABLES);
    }

    /**
     * 验证SQL查询语句的安全性
     * 该方法会检查SQL语句的语法、结构、使用的表、列、函数等是否符合安全策略
     *
     * @param source 输入的SQL查询语句
     * @return 验证通过后的规范化SQL语句
     * @throws IllegalArgumentException 当SQL语句不符合安全策略时抛出
     */
    public static String validate(String source) {
        // 检查SQL是否为空或过长
        if (source == null || source.length() > 12000) {
            throw bad("SQL为空或过长");
        }
        String sql = source.trim();
        // 移除末尾的分号
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        // 检查是否包含多语句、注释、变量或转义序列
        if (sql.contains(";")
                || sql.contains("--")
                || sql.contains("/*")
                || sql.contains("#")
                || sql.contains("@")
                || sql.contains("\\")) {
            throw bad("不允许多语句、注释、变量或转义序列");
        }
        // 检查是否包含危险的SQL指令
        if (sql.matches(
                        "(?is).*\\b(INTO|OUTFILE|DUMPFILE|PROCEDURE|LOCK|UNLOCK|SLEEP|BENCHMARK|LOAD_FILE)\\b.*")
                || sql.matches("(?is).*\\bFOR\\s+SHARE\\b.*")) {
            throw bad("包含不允许的SQL指令");
        }
        try {
            // 解析SQL语句
            Statement statement = CCJSqlParserUtil.parse(sql);
            // 检查是否为SELECT语句
            if (!(statement instanceof Select)) {
                throw bad("仅允许SELECT查询");
            }
            Select select = (Select) statement;
            // 检查是否包含CTE
            if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
                throw bad("暂不支持CTE");
            }
            // 检查是否为普通SELECT语句
            if (!(select.getSelectBody() instanceof PlainSelect)) {
                throw bad("暂不支持UNION或嵌套查询");
            }
            PlainSelect body = (PlainSelect) select.getSelectBody();
            // 检查是否包含不允许的特殊查询选项
            if (body.isForUpdate()
                    || body.getIntoTables() != null
                    || body.getOracleHint() != null
                    || body.getOracleHierarchical() != null
                    || body.getWindowDefinitions() != null
                    || body.getForXmlPath() != null
                    || body.getKsqlWindow() != null
                    || body.isEmitChanges()
                    || body.getWithIsolation() != null
                    || body.getFetch() != null
                    || body.getTop() != null
                    || body.getSkip() != null
                    || body.getFirst() != null
                    || body.getMySqlSqlCalcFoundRows()) {
                throw bad("不允许锁定、导出或特殊查询选项");
            }
            // 检查主表
            table(body.getFromItem());
            // 检查JOIN表
            if (body.getJoins() != null) {
                if (body.getJoins().size() > 6) {
                    throw bad("关联表过多");
                }
                for (Join join : body.getJoins()) {
                    if (join.isCross()
                            || join.isSimple()
                            || join.isNatural()
                            || join.isApply()
                            || join.isWindowJoin()) {
                        throw bad("仅允许带ON条件的JOIN");
                    }
                    table(join.getRightItem());
                    if (join.getOnExpressions() == null || join.getOnExpressions().isEmpty()) {
                        throw bad("JOIN必须包含ON条件");
                    }
                    for (Expression on : join.getOnExpressions()) {
                        expression(on, 0);
                    }
                }
            }
            // 检查查询列数
            if (body.getSelectItems().size() > 30) {
                throw bad("一次最多查询30列");
            }
            // 检查查询列
            for (SelectItem item : body.getSelectItems()) {
                if (item instanceof AllColumns || item instanceof AllTableColumns) {
                    throw bad("请明确指定需要查询的字段，不允许使用SELECT *");
                }
                if (item instanceof SelectExpressionItem) {
                    expression(((SelectExpressionItem) item).getExpression(), 0);
                } else {
                    throw bad("不支持的查询列");
                }
            }
            // 检查WHERE条件
            expression(body.getWhere(), 0);
            // 检查HAVING条件
            expression(body.getHaving(), 0);
            // 检查GROUP BY
            if (body.getGroupBy() != null) {
                if (body.getGroupBy().getGroupingSets() != null
                        && !body.getGroupBy().getGroupingSets().isEmpty()) {
                    throw bad("不支持GROUPING SETS");
                }
                for (Expression e : body.getGroupBy().getGroupByExpressions()) {
                    expression(e, 0);
                }
            }
            // 检查ORDER BY
            if (body.getOrderByElements() != null) {
                for (OrderByElement e : body.getOrderByElements()) {
                    expression(e.getExpression(), 0);
                }
            }
            // 检查DISTINCT
            if (body.getDistinct() != null && body.getDistinct().getOnSelectItems() != null) {
                throw bad("不支持DISTINCT ON");
            }
            // 处理分页
            long requestedRows = 201;
            if (body.getOffset() != null) {
                throw bad("暂不支持OFFSET分页，请重新描述查询范围");
            }
            if (body.getLimit() != null) {
                if (body.getLimit().getOffset() != null) {
                    throw bad("暂不支持LIMIT偏移分页");
                }
                if (!(body.getLimit().getRowCount() instanceof LongValue)) {
                    throw bad("LIMIT必须是非负整数字面量");
                }
                requestedRows = ((LongValue) body.getLimit().getRowCount()).getValue();
                if (requestedRows < 0) {
                    throw bad("LIMIT不能为负数");
                }
            }
            // 设置限制
            Limit limit = new Limit();
            limit.setRowCount(new LongValue(Math.min(requestedRows, 201)));
            body.setLimit(limit);
            return select.toString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (net.sf.jsqlparser.JSQLParserException e) {
            throw new IllegalArgumentException("模型生成的SQL无法安全解析，请换一种方式提问", e);
        }
    }
    /**
     * 验证表名的合法性
     * 检查表是否在允许的表列表中，并验证表的特殊变换或提示
     *
     * @param item SQL解析中的表项
     * @throws IllegalArgumentException 当表名不合法时抛出
     */
    private static void table(FromItem item) {
        if (!(item instanceof Table)) {
            throw bad("仅允许业务表，不支持子查询和表函数");
        }
        Table table = (Table) item;
        if (table.getPivot() != null
                || table.getUnPivot() != null
                || table.getSqlServerHints() != null
                || table.getIndexHint() != null) {
            throw bad("不允许特殊表变换或索引提示");
        }
        String name = table.getFullyQualifiedName().replace("`", "").toLowerCase(Locale.ROOT);
        int schemaSeparator = name.lastIndexOf('.');
        if (schemaSeparator >= 0) {
            name = name.substring(schemaSeparator + 1);
        }
        if (!TABLES.contains(name)) {
            throw bad("不允许访问表：" + name);
        }
    }

    /**
     * 验证表达式的安全性
     * 递归检查表达式的各个部分，包括列、函数、操作符等
     *
     * @param e 要检查的表达式
     * @param depth 当前递归深度，用于防止无限递归
     * @throws IllegalArgumentException 当表达式不安全时抛出
     */
    private static void expression(Expression e, int depth) {
        if (e == null) {
            return;
        }
        // 防止表达式嵌套过深
        if (depth > 30) {
            throw bad("表达式嵌套过深");
        }
        // 检查列
        if (e instanceof Column) {
            String name = ((Column) e).getColumnName().replace("`", "").toLowerCase(Locale.ROOT);
            if (BLOCKED_COLUMNS.contains(name)) {
                throw bad("不允许查询敏感字段：" + name);
            }
            return;
        }
        // 检查是否使用SELECT *
        if (e instanceof AllColumns || e instanceof AllTableColumns) {
            throw bad("请明确指定需要查询的字段，不允许使用SELECT *");
        }
        // 检查字面值
        if (e instanceof LongValue
                || e instanceof DoubleValue
                || e instanceof StringValue
                || e instanceof NullValue
                || e instanceof JdbcParameter
                || e instanceof DateValue
                || e instanceof TimeValue
                || e instanceof TimestampValue) {
            return;
        }
        // 检查二元表达式
        if (e instanceof BinaryExpression && BINARY.contains(e.getClass().getSimpleName())) {
            expression(((BinaryExpression) e).getLeftExpression(), depth + 1);
            expression(((BinaryExpression) e).getRightExpression(), depth + 1);
            if (e instanceof LikeExpression) {
                expression(((LikeExpression) e).getEscape(), depth + 1);
            }
            return;
        }
        // 检查括号表达式
        if (e instanceof Parenthesis) {
            expression(((Parenthesis) e).getExpression(), depth + 1);
            return;
        }
        // 检查带符号表达式
        if (e instanceof SignedExpression) {
            expression(((SignedExpression) e).getExpression(), depth + 1);
            return;
        }
        // 检查NOT表达式
        if (e instanceof NotExpression) {
            expression(((NotExpression) e).getExpression(), depth + 1);
            return;
        }
        // 检查函数
        if (e instanceof Function) {
            Function f = (Function) e;
            if (!FUNCTIONS.contains(f.getName().toUpperCase(Locale.ROOT))
                    || f.getAttribute() != null
                    || f.getKeep() != null
                    || f.getNamedParameters() != null
                    || f.getOrderByElements() != null) {
                throw bad("不允许的函数：" + f.getName());
            }
            if (f.getParameters() != null) {
                for (Expression arg : f.getParameters().getExpressions()) {
                    if ("COUNT".equalsIgnoreCase(f.getName()) && arg instanceof AllColumns) {
                        continue;
                    }
                    expression(arg, depth + 1);
                }
            }
            return;
        }
        // 检查IS NULL表达式
        if (e instanceof IsNullExpression) {
            expression(((IsNullExpression) e).getLeftExpression(), depth + 1);
            return;
        }
        // 检查BETWEEN表达式
        if (e instanceof Between) {
            Between b = (Between) e;
            expression(b.getLeftExpression(), depth + 1);
            expression(b.getBetweenExpressionStart(), depth + 1);
            expression(b.getBetweenExpressionEnd(), depth + 1);
            return;
        }
        // 检查IN表达式
        if (e instanceof InExpression) {
            InExpression in = (InExpression) e;
            expression(in.getLeftExpression(), depth + 1);
            if (!(in.getRightItemsList() instanceof ExpressionList)
                    || in.getRightExpression() != null) {
                throw bad("IN仅允许值列表");
            }
            List<Expression> values = ((ExpressionList) in.getRightItemsList()).getExpressions();
            if (values.size() > 100) {
                throw bad("IN列表过长");
            }
            for (Expression value : values) {
                expression(value, depth + 1);
            }
            return;
        }
        // 检查CASE表达式
        if (e instanceof CaseExpression) {
            CaseExpression c = (CaseExpression) e;
            expression(c.getSwitchExpression(), depth + 1);
            expression(c.getElseExpression(), depth + 1);
            for (WhenClause w : c.getWhenClauses()) {
                expression(w.getWhenExpression(), depth + 1);
                expression(w.getThenExpression(), depth + 1);
            }
            return;
        }
        throw bad("不支持的SQL表达式：" + e.getClass().getSimpleName());
    }

    /**
     * 创建异常实例
     *
     * @param text 异常信息
     * @return IllegalArgumentException实例
     */
    private static IllegalArgumentException bad(String text) {
        return new IllegalArgumentException(text);
    }
}
