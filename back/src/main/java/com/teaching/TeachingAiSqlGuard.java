package com.teaching;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.schema.*;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import java.util.*;

/** Fails closed: only a small, auditable SELECT AST is executable. */
public final class TeachingAiSqlGuard {
    static final Set<String> TABLES=new HashSet<>(Arrays.asList("academic_year","academic_term","course","teaching_task","schedule_detail","experiment_project","teaching_task_teacher","laboratory"));
    private static final Set<String> FUNCTIONS=new HashSet<>(Arrays.asList("COUNT","SUM","AVG","MIN","MAX","ROUND","ABS","COALESCE","IFNULL","NULLIF","CONCAT","CONCAT_WS","LOWER","UPPER","LENGTH","CHAR_LENGTH","YEAR","MONTH","DAY","DATE","DATE_FORMAT"));
    private static final Set<String> BINARY=new HashSet<>(Arrays.asList("Addition","Subtraction","Multiplication","Division","Modulo","EqualsTo","NotEqualsTo","GreaterThan","GreaterThanEquals","MinorThan","MinorThanEquals","AndExpression","OrExpression","LikeExpression"));
    private TeachingAiSqlGuard() {}
    public static String validate(String source) {
        if(source==null || source.length()>12000) throw bad("SQL为空或过长");
        String sql=source.trim(); if(sql.endsWith(";")) sql=sql.substring(0,sql.length()-1).trim();
        if(sql.contains(";") || sql.contains("--") || sql.contains("/*") || sql.contains("#") || sql.contains("@") || sql.contains("\\")) throw bad("不允许多语句、注释、变量或转义序列");
        if(sql.matches("(?is).*\\b(INTO|OUTFILE|DUMPFILE|PROCEDURE|LOCK|UNLOCK|SLEEP|BENCHMARK|LOAD_FILE)\\b.*") || sql.matches("(?is).*\\bFOR\\s+SHARE\\b.*")) throw bad("包含不允许的SQL指令");
        try {
            Statement statement=CCJSqlParserUtil.parse(sql);
            if(!(statement instanceof Select)) throw bad("仅允许SELECT查询");
            Select select=(Select)statement;
            if(select.getWithItemsList()!=null && !select.getWithItemsList().isEmpty()) throw bad("暂不支持CTE");
            if(!(select.getSelectBody() instanceof PlainSelect)) throw bad("暂不支持UNION或嵌套查询");
            PlainSelect body=(PlainSelect)select.getSelectBody();
            if(body.isForUpdate() || body.getIntoTables()!=null || body.getOracleHint()!=null || body.getOracleHierarchical()!=null || body.getWindowDefinitions()!=null || body.getForXmlPath()!=null || body.getKsqlWindow()!=null || body.isEmitChanges() || body.getWithIsolation()!=null || body.getFetch()!=null || body.getTop()!=null || body.getSkip()!=null || body.getFirst()!=null || body.getMySqlSqlCalcFoundRows()) throw bad("不允许锁定、导出或特殊查询选项");
            table(body.getFromItem());
            if(body.getJoins()!=null) {
                if(body.getJoins().size()>6) throw bad("关联表过多");
                for(Join join:body.getJoins()) {
                    if(join.isCross() || join.isSimple() || join.isNatural() || join.isApply() || join.isWindowJoin()) throw bad("仅允许带ON条件的JOIN");
                    table(join.getRightItem());
                    if(join.getOnExpressions()==null || join.getOnExpressions().isEmpty()) throw bad("JOIN必须包含ON条件");
                    for(Expression on:join.getOnExpressions()) expression(on,0);
                }
            }
            if(body.getSelectItems().size()>30) throw bad("一次最多查询30列");
            for(SelectItem item:body.getSelectItems()) {
                if(item instanceof SelectExpressionItem) expression(((SelectExpressionItem)item).getExpression(),0);
                else if(!(item instanceof AllColumns) && !(item instanceof AllTableColumns)) throw bad("不支持的查询列");
            }
            expression(body.getWhere(),0); expression(body.getHaving(),0);
            if(body.getGroupBy()!=null) {
                if(body.getGroupBy().getGroupingSets()!=null && !body.getGroupBy().getGroupingSets().isEmpty()) throw bad("不支持GROUPING SETS");
                for(Expression e:body.getGroupBy().getGroupByExpressions()) expression(e,0);
            }
            if(body.getOrderByElements()!=null) for(OrderByElement e:body.getOrderByElements()) expression(e.getExpression(),0);
            if(body.getDistinct()!=null && body.getDistinct().getOnSelectItems()!=null) throw bad("不支持DISTINCT ON");
            long requestedRows=201;
            if(body.getOffset()!=null) throw bad("暂不支持OFFSET分页，请重新描述查询范围");
            if(body.getLimit()!=null) {
                if(body.getLimit().getOffset()!=null) throw bad("暂不支持LIMIT偏移分页");
                if(!(body.getLimit().getRowCount() instanceof LongValue)) throw bad("LIMIT必须是非负整数字面量");
                requestedRows=((LongValue)body.getLimit().getRowCount()).getValue();
                if(requestedRows<0) throw bad("LIMIT不能为负数");
            }
            Limit limit=new Limit(); limit.setRowCount(new LongValue(Math.min(requestedRows,201))); body.setLimit(limit);
            return select.toString();
        } catch(IllegalArgumentException e) { throw e; }
        catch(Exception e) { throw bad("模型生成的SQL无法安全解析，请换一种方式提问"); }
    }
    private static void table(FromItem item) {
        if(!(item instanceof Table)) throw bad("仅允许业务表，不支持子查询和表函数");
        Table table=(Table)item;
        if(table.getPivot()!=null || table.getUnPivot()!=null || table.getSqlServerHints()!=null || table.getIndexHint()!=null) throw bad("不允许特殊表变换或索引提示");
        String name=table.getFullyQualifiedName().replace("`","").toLowerCase(Locale.ROOT);
        if(!TABLES.contains(name)) throw bad("不允许访问表："+name);
    }
    private static void expression(Expression e,int depth) {
        if(e==null) return;
        if(depth>30) throw bad("表达式嵌套过深");
        if(e instanceof Column || e instanceof LongValue || e instanceof DoubleValue || e instanceof StringValue || e instanceof NullValue || e instanceof DateValue || e instanceof TimeValue || e instanceof TimestampValue || e instanceof AllColumns) return;
        if(e instanceof BinaryExpression && BINARY.contains(e.getClass().getSimpleName())) {
            expression(((BinaryExpression)e).getLeftExpression(),depth+1); expression(((BinaryExpression)e).getRightExpression(),depth+1);
            if(e instanceof LikeExpression) expression(((LikeExpression)e).getEscape(),depth+1);
            return;
        }
        if(e instanceof Parenthesis) { expression(((Parenthesis)e).getExpression(),depth+1); return; }
        if(e instanceof SignedExpression) { expression(((SignedExpression)e).getExpression(),depth+1); return; }
        if(e instanceof NotExpression) { expression(((NotExpression)e).getExpression(),depth+1); return; }
        if(e instanceof Function) {
            Function f=(Function)e;
            if(!FUNCTIONS.contains(f.getName().toUpperCase(Locale.ROOT)) || f.getAttribute()!=null || f.getKeep()!=null || f.getNamedParameters()!=null || f.getOrderByElements()!=null) throw bad("不允许的函数："+f.getName());
            if(f.getParameters()!=null) for(Expression arg:f.getParameters().getExpressions()) expression(arg,depth+1);
            return;
        }
        if(e instanceof IsNullExpression) { expression(((IsNullExpression)e).getLeftExpression(),depth+1); return; }
        if(e instanceof Between) { Between b=(Between)e; expression(b.getLeftExpression(),depth+1); expression(b.getBetweenExpressionStart(),depth+1); expression(b.getBetweenExpressionEnd(),depth+1); return; }
        if(e instanceof InExpression) {
            InExpression in=(InExpression)e; expression(in.getLeftExpression(),depth+1);
            if(!(in.getRightItemsList() instanceof ExpressionList) || in.getRightExpression()!=null) throw bad("IN仅允许值列表");
            List<Expression> values=((ExpressionList)in.getRightItemsList()).getExpressions(); if(values.size()>100) throw bad("IN列表过长");
            for(Expression value:values) expression(value,depth+1); return;
        }
        if(e instanceof CaseExpression) { CaseExpression c=(CaseExpression)e; expression(c.getSwitchExpression(),depth+1); expression(c.getElseExpression(),depth+1); for(WhenClause w:c.getWhenClauses()) { expression(w.getWhenExpression(),depth+1); expression(w.getThenExpression(),depth+1); } return; }
        throw bad("不支持的SQL表达式："+e.getClass().getSimpleName());
    }
    private static IllegalArgumentException bad(String text) { return new IllegalArgumentException(text); }
}
