/*
 * Copyright (c) 2008 Mysema Ltd.
 * All rights reserved.
 * 
 */
package com.mysema.query.grammar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mysema.query.JoinExpression;
import com.mysema.query.QueryBase;
import com.mysema.query.grammar.types.*;
import com.mysema.query.grammar.types.Alias.ASimple;
import com.mysema.query.grammar.types.Expr.EBoolean;
import com.mysema.query.serialization.BaseSerializer;

/**
 * SqlSerializer serializes Querydsl queries into SQL
 *
 * @author tiwe
 * @version $Id$
 */
public class SqlSerializer extends BaseSerializer<SqlSerializer>{
    
    protected final SqlOps ops;
    
    public SqlSerializer(SqlOps ops){
        super(ops);
        this.ops = ops;
    }
        
    public void serialize(
            List<Expr<?>> select, 
            List<JoinExpression<SqlJoinMeta>> joins,
            Expr.EBoolean where, 
            List<Expr<?>> groupBy, 
            Expr.EBoolean having,
            List<OrderSpecifier<?>> orderBy, 
            int limit, 
            int offset, 
            boolean forCountRow){
         if (forCountRow){
//            _append("select count(*)\n");
             append(ops.select()).append(ops.countStar());
        }else if (!select.isEmpty()){
            append(ops.select());           
            List<Expr<?>> sqlSelect = new ArrayList<Expr<?>>();
            for (Expr<?> selectExpr : select){
                if (selectExpr instanceof Constructor){
                    // transforms constructor arguments into individual select
                    // expressions
                    sqlSelect.addAll(Arrays.<Expr<?>>asList(((Constructor<?>)selectExpr).getArgs()));
                }else{
                    sqlSelect.add(selectExpr);
                }
            }
            append(", ", sqlSelect);
        }
        append(ops.from());
        if (joins.isEmpty()){
            // TODO : disallow usage of dummy table ?!?
            append(ops.dummyTable());
            
        }        
        for (int i=0; i < joins.size(); i++){
            JoinExpression<SqlJoinMeta> je = joins.get(i);            
            if (i > 0){
                String sep = ", ";
                    switch(je.getType()){
                    case FULLJOIN:  sep = ops.fullJoin(); break;
                    case INNERJOIN: sep = ops.innerJoin(); break;
                    case JOIN:      sep = ops.join(); break;
                    case LEFTJOIN:  sep = ops.leftJoin(); break;                                
                    }    
                append(sep);
            }
            
            // type specifier
            if (je.getTarget() instanceof Path.PEntity && ops.supportsAlias()){
                Path.PEntity<?> pe = (Path.PEntity<?>)je.getTarget();
                if (pe.getMetadata().getParent() == null){ 
                    append(pe.getEntityName()).append(ops.tableAlias());    
                }                
            }            
            handle(je.getTarget());
            if (je.getCondition() != null){
                append(ops.on()).handle(je.getCondition());
            }
        }
        
        if (where != null){            
            append(ops.where()).handle(where);                        
        }
        if (!groupBy.isEmpty()){
            append(ops.groupBy()).append(", ",groupBy);
        }
        if (having != null){
            if (groupBy.isEmpty()) {
                throw new IllegalArgumentException("having, but not groupBy was given");
            }                
            append(ops.having()).handle(having);
        }
        
        beforeOrderBy();
        
        if (!ops.limitAndOffsetSymbols() && (limit > 0 || offset > 0)){
            if (where == null) append(ops.where());
            append(ops.limitOffsetCondition(limit, offset));
        }
        
        if (!orderBy.isEmpty() && !forCountRow){
            append(ops.orderBy());
            boolean first = true;
            for (OrderSpecifier<?> os : orderBy){            
                if (!first) builder.append(", ");
                handle(os.target);
                append(os.order == Order.ASC ? ops.asc() : ops.desc());
                first = false;
            }
        }
        if (ops.limitAndOffsetSymbols()){
            if (limit > 0){
                append(ops.limit()).append(String.valueOf(limit));
            }
            if (offset > 0){
                append(ops.offset()).append(String.valueOf(offset));
            }    
        }               
    }
    
    protected void beforeOrderBy() {
        // template method, for subclasses do override
        
    }

    public void serializeUnion(List<Expr<?>> select,
            SubQuery<SqlJoinMeta, ?>[] sqs, EBoolean self,
            List<OrderSpecifier<?>> orderBy) {
        // union
        append(ops.union(), Arrays.asList(sqs));
        
        
        // order by
        if (!orderBy.isEmpty()){
            append(ops.orderBy());
            boolean first = true;
            for (OrderSpecifier<?> os : orderBy){            
                if (!first) builder.append(", ");
                handle(os.target);
                append(os.order == Order.ASC ? ops.asc() : ops.desc());
                first = false;
            }
        }
        
    }
    
    protected void visit(CountExpression expr) {
        if (expr.getTarget() == null){
            append(ops.countStar());    
        }else{
            append(ops.count()).append("(").handle(expr.getTarget()).append(")");
        }                
    }
               
    @Override
    protected void visit(Expr.EConstant<?> expr) {
        append("?");
        constants.add(expr.getConstant());
    }
    
    @Override
    protected void visit(ASimple<?> expr) {
        handle(expr.getFrom()).append(ops.columnAlias()).append(expr.getTo());
    }
    
    protected void visit(SumOver<?> expr) {
        append(ops.sum()).append("(").handle(expr.getTarget()).append(") ");
        append(ops.over());
        append(" (");
        if (expr.getPartitionBy() != null){
            append(ops.partitionBy()).handle(expr.getPartitionBy());
        }
        if (!expr.getOrderBy().isEmpty()){
            append(ops.orderBy()).append(", ", expr.getOrderBy());
        }
        append(")");        
    }

    protected void visit(SubQuery<SqlJoinMeta,?> query) {
        QueryBase<SqlJoinMeta,?>.Metadata md = query.getQuery().getMetadata();
        append("(");
        serialize(md.getSelect(), md.getJoins(),
            md.getWhere(), md.getGroupBy(), md.getHaving(), 
            md.getOrderBy(), 0, 0, false);
        append(")");
    }

}
