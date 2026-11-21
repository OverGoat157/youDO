package ru.mirea.youdo.filter;
import java.math.BigDecimal;
import java.util.*;
import ru.mirea.youdo.domain.*;
import ru.mirea.youdo.service.AccountProblem;
public record OrderFilter(String q, UUID categoryId, OrderStatus status, Boolean urgent, OrderPriority priority,
        String tag, Boolean archived, String budgetOp, BigDecimal budgetValue, BigDecimal budgetFrom, BigDecimal budgetTo) {
    public static OrderFilter from(Map<String,String> values) {
        try {
            String archive=text(values,"archived");
            Boolean archived=archive==null ? false : archive.equals("all") ? null : bool(archive);
            String op=text(values,"budgetOp"); BigDecimal value=decimal(values,"budgetValue");
            if ((op==null)!=(value==null) || op!=null && !Set.of("eq","gt","gte","lt","lte").contains(op))
                throw AccountProblem.invalid("budgetOp","Укажите допустимую операцию и budgetValue");
            BigDecimal from=decimal(values,"budgetFrom"),to=decimal(values,"budgetTo");
            if (from!=null && to!=null && from.compareTo(to)>0) throw AccountProblem.invalid("budgetFrom","Начало диапазона больше конца");
            if (op!=null && (from!=null || to!=null)) throw AccountProblem.invalid("budgetOp","Задайте операцию либо диапазон");
            String category=text(values,"categoryId"), status=text(values,"status"), urgent=text(values,"urgent"), priority=text(values,"priority"),tag=text(values,"tag");
            return new OrderFilter(text(values,"q"),category==null?null:UUID.fromString(category),
                status==null?null:OrderStatus.valueOf(status),urgent==null?null:bool(urgent),
                priority==null?null:OrderPriority.valueOf(priority),tag==null?null:tag.toLowerCase(Locale.ROOT),
                archived,op,value,from,to);
        } catch(IllegalArgumentException e) { throw AccountProblem.invalid("filter","Неверный формат фильтра"); }
    }
    private static String text(Map<String,String> v,String k) { String s=v.get(k);return s==null||s.isBlank()?null:s.strip(); }
    private static Boolean bool(String s) { if(!s.equals("true")&&!s.equals("false")) throw new IllegalArgumentException();return Boolean.valueOf(s); }
    private static BigDecimal decimal(Map<String,String> v,String k) { String s=text(v,k);return s==null?null:new BigDecimal(s); }
}
