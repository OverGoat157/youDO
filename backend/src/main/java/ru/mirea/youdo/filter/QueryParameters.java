package ru.mirea.youdo.filter;
import java.util.*;
import org.springframework.data.domain.*;
import ru.mirea.youdo.service.AccountProblem;
public final class QueryParameters {
    private QueryParameters() {}
    public static PageRequest page(int page,int size,Sort sort) {
        if(page<0 || size<1 || size>100) throw AccountProblem.invalid("page","page >= 0, size от 1 до 100");
        return PageRequest.of(page,size,sort);
    }
    public static Sort sort(String value, Set<String> fields, String stableId) {
        String[] parts=value.split(",",-1);
        if(parts.length!=2 || !fields.contains(parts[0]) || !Set.of("asc","desc").contains(parts[1]))
            throw AccountProblem.invalid("sort","Недопустимая сортировка");
        var direction=Sort.Direction.fromString(parts[1]);
        return Sort.by(direction,parts[0]).and(Sort.by(direction,stableId));
    }
    public static String like(String value) {
        return "%"+value.strip().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
    }
}
