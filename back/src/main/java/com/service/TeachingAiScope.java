package com.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.*;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

/** Resolve known scope before generation; reject SQL which drops its mandatory filters. */
final class TeachingAiScope {
    private final Map<String, Long> filters = new LinkedHashMap<>();
    private final List<String> labels = new ArrayList<>();

    static TeachingAiScope resolve(String question, Object selectedTerm,
            List<Map<String, Object>> terms, List<Map<String, Object>> labs) {
        TeachingAiScope scope = new TeachingAiScope();
        String q = question.replaceAll("\\s+", "");
        Matcher year = Pattern.compile("(?<!\\d)(20\\d{2})[-—–~至](20\\d{2})(?!\\d)").matcher(q);
        Matcher semester = Pattern.compile("(?:第([一二12])学期|20\\d{2}[-—–]20\\d{2}[-—–]([12])(?!\\d))").matcher(q);
        if (year.find()) {
            int start = Integer.parseInt(year.group(1));
            if (Integer.parseInt(year.group(2)) != start + 1)
                throw new IllegalArgumentException("请使用连续年份描述学年，例如2025-2026学年");
            scope.filters.put("academic_term.start_year", (long) start);
            String label = start + "-" + (start + 1) + "学年";
            if (semester.find()) {
                String n = semester.group(1) == null ? semester.group(2) : semester.group(1);
                long no = "一".equals(n) || "1".equals(n) ? 1L : 2L;
                scope.filters.put("academic_term.term_no", no);
                label += "第" + no + "学期";
            }
            scope.labels.add(label + "（问题指定）");
        } else if (q.matches(".*(本学期|当前学期).*")) {
            int[] current = TeachingTermService.calendarTerm(LocalDate.now(ZoneId.of("Asia/Shanghai")));
            scope.filters.put("academic_term.start_year", (long) current[0]);
            scope.filters.put("academic_term.term_no", (long) current[1]);
            scope.labels.add(current[0] + "-" + (current[0] + 1) + "-" + current[1] + "（当前学期）");
        } else if (q.matches(".*(全部学期|所有学期|跨学期|历年|所有学年|全部学年).*")) {
            scope.labels.add("全部学期（问题指定）");
        } else if (q.matches(".*(上学期|下学期|去年|今年|本学年|第[一二12]学期|\\d{4}年).*")) {
            throw new IllegalArgumentException("请明确学年和学期，例如2025-2026学年第2学期");
        } else if (selectedTerm != null && !selectedTerm.toString().trim().isEmpty()) {
            Map<String, Object> term = terms.stream()
                    .filter(t -> String.valueOf(t.get("id")).equals(selectedTerm.toString())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("所选学期不存在，请重新选择"));
            scope.filters.put("academic_term.id", ((Number) term.get("id")).longValue());
            scope.labels.add(term.get("name") + "（页面选择）");
        } else scope.labels.add("全部学期");

        // Support room aliases without fuzzy matching unrelated room numbers.
        Matcher room = Pattern.compile("(?<!\\d)(\\d{1,3})(?:号?楼|栋|[-－—])(?:的)?(\\d{3,4})(?!\\d)").matcher(q);
        Set<Long> matched = new LinkedHashSet<>();
        while (room.find()) {
            String code = room.group(1) + "-" + room.group(2);
            Map<String, Object> lab = labs.stream()
                    .filter(l -> code.equals(String.valueOf(l.get("lab_code"))))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("未找到实验室" + code + "，请核对实验室编号"));
            matched.add(((Number) lab.get("id")).longValue());
        }
        for (Map<String, Object> lab : labs) {
            String name = String.valueOf(lab.get("lab_name"));
            if (!name.isEmpty() && q.contains(name)) matched.add(((Number) lab.get("id")).longValue());
        }
        if (matched.size() > 1) throw new IllegalArgumentException("请一次指定一间实验室，或提问各实验室的汇总统计");
        if (!matched.isEmpty()) {
            Long id = matched.iterator().next();
            scope.filters.put("laboratory.id", id);
            for (Map<String, Object> lab : labs)
                if (((Number) lab.get("id")).longValue() == id) scope.labels.add(String.valueOf(lab.get("lab_name")));
        }
        return scope;
    }

    String description() { return String.join("；", labels); }

    String instructions() {
        return "\n本次已解析的查询范围：" + description()
                + "。必须通过真实外键关联这些表，并在WHERE中用AND包含以下等值条件（使用对应表别名）："
                + filters + "。这些条件不可省略，不可放在OR、HAVING或外连接ON中。"
                + "用户问题有其他条件时也必须保留；无法理解时返回UNSUPPORTED及澄清问题。";
    }

    void validate(String sql) {
        if (filters.isEmpty()) return;
        try {
            PlainSelect select = (PlainSelect) ((Select) CCJSqlParserUtil.parse(sql)).getSelectBody();
            Map<String, String> aliases = new HashMap<>();
            addTable(aliases, select.getFromItem());
            if (select.getJoins() != null)
                for (Join join : select.getJoins()) addTable(aliases, join.getRightItem());
            for (Map.Entry<String, Long> filter : filters.entrySet()) {
                if (!containsRequired(select.getWhere(), aliases, filter.getKey(), filter.getValue()))
                    throw new IllegalArgumentException("SQL遗漏查询范围，请在WHERE中添加 " + filter.getKey()
                            + " = " + filter.getValue() + " 并使用正确表别名及外键关联");
            }
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("无法校验查询范围，请重新生成SQL"); }
    }

    private static void addTable(Map<String, String> aliases, FromItem item) {
        Table table = (Table) item;
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        if (aliases.put(alias.toLowerCase(Locale.ROOT), table.getName().toLowerCase(Locale.ROOT)) != null)
            throw new IllegalArgumentException("不允许重复表别名");
    }

    private static boolean containsRequired(Expression expression, Map<String, String> aliases,
            String field, long value) {
        if (expression instanceof Parenthesis)
            return containsRequired(((Parenthesis) expression).getExpression(), aliases, field, value);
        if (expression instanceof AndExpression) {
            AndExpression and = (AndExpression) expression;
            return containsRequired(and.getLeftExpression(), aliases, field, value)
                    || containsRequired(and.getRightExpression(), aliases, field, value);
        }
        if (!(expression instanceof EqualsTo)) return false;
        EqualsTo equals = (EqualsTo) expression;
        return matches(equals.getLeftExpression(), equals.getRightExpression(), aliases, field, value)
                || matches(equals.getRightExpression(), equals.getLeftExpression(), aliases, field, value);
    }

    private static boolean matches(Expression column, Expression number, Map<String, String> aliases,
            String field, long value) {
        if (!(column instanceof Column) || !(number instanceof LongValue)) return false;
        Column c = (Column) column;
        if (c.getTable() == null || c.getTable().getName() == null) return false;
        String table = aliases.get(c.getTable().getName().toLowerCase(Locale.ROOT));
        return field.equals(table + "." + c.getColumnName().toLowerCase(Locale.ROOT))
                && ((LongValue) number).getValue() == value;
    }
}
