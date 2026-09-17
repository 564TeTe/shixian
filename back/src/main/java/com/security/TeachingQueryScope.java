package com.security;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SubSelect;

import java.util.LinkedHashMap;
import java.util.Map;

/** Replace every model-selected table with a server-scoped derived table after AST validation. */
public final class  TeachingQueryScope {
    private TeachingQueryScope() {}

    public static String apply(String validatedSql, Long teacherId, Long termId) {
        if (teacherId == null && termId == null) {
            return validatedSql;
        }
        if ((teacherId != null && teacherId <= 0) || (termId != null && termId <= 0)) {
            throw new IllegalArgumentException("查询范围无效");
        }
        String taskFilter =
                "1=1"
                        + (termId == null ? "" : " AND t.term_id=" + termId)
                        + (teacherId == null
                                ? ""
                                : " AND EXISTS (SELECT 1 FROM teaching_task_teacher own WHERE"
                                      + " own.task_id=t.id AND own.teacher_account_id="
                                        + teacherId
                                        + ")");
        String tasks = "SELECT t.id FROM teaching_task t WHERE " + taskFilter;
        Map<String, String> scopes = new LinkedHashMap<>();
        scopes.put("teaching_task", "SELECT t.* FROM teaching_task t WHERE " + taskFilter);
        for (String table :
                new String[] {"schedule_detail", "experiment_project", "teaching_task_teacher"}) {
            scopes.put(table, "SELECT * FROM " + table + " WHERE task_id IN (" + tasks + ")");
        }
        scopes.put(
                "course",
                "SELECT * FROM course WHERE id IN (SELECT t.course_id FROM teaching_task t WHERE "
                        + taskFilter
                        + ")");
        scopes.put(
                "academic_term",
                "SELECT * FROM academic_term WHERE id IN (SELECT t.term_id FROM teaching_task t"
                    + " WHERE "
                        + taskFilter
                        + ")");
        scopes.put(
                "laboratory",
                "SELECT * FROM laboratory WHERE id IN (SELECT lab_id FROM schedule_detail WHERE"
                    + " task_id IN ("
                        + tasks
                        + "))");
        try {
            Select select = (Select) CCJSqlParserUtil.parse(validatedSql);
            PlainSelect body = (PlainSelect) select.getSelectBody();
            body.setFromItem(scoped(body.getFromItem(), scopes));
            if (body.getJoins() != null) {
                for (Join join : body.getJoins()) {
                    join.setRightItem(scoped(join.getRightItem(), scopes));
                }
            }
            return select.toString();
        } catch (net.sf.jsqlparser.JSQLParserException e) {
            throw new IllegalArgumentException("无法应用查询数据范围", e);
        }
    }

    private static FromItem scoped(FromItem item, Map<String, String> scopes)
            throws net.sf.jsqlparser.JSQLParserException {
        Table table = (Table) item;
        String name = table.getName().replace("`", "").toLowerCase(java.util.Locale.ROOT);
        String sql = scopes.get(name);
        if (sql == null) {
            throw new IllegalArgumentException("不支持的查询表");
        }
        SubSelect result = new SubSelect();
        result.setSelectBody(((Select) CCJSqlParserUtil.parse(sql)).getSelectBody());
        result.setAlias(table.getAlias() == null ? new Alias(table.getName()) : table.getAlias());
        return result;
    }
}
