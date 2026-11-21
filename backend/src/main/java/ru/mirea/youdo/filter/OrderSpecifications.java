package ru.mirea.youdo.filter;
import java.time.LocalDate;
import java.util.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.youdo.domain.Order;
import ru.mirea.youdo.service.OrderPriorityCalculator;
public final class OrderSpecifications {
    private OrderSpecifications() {}
    public static Specification<Order> matching(OrderFilter f, OrderPriorityCalculator priority, LocalDate today) {
        return (root,query,cb) -> {
            var p=new ArrayList<Predicate>();
            if(f.q()!=null) {
                String pattern=QueryParameters.like(f.q());
                p.add(cb.or(cb.like(cb.lower(root.get("title")),pattern,'\\'),
                    cb.like(cb.lower(root.get("description")),pattern,'\\'),cb.like(cb.lower(root.get("code")),pattern,'\\')));
            }
            if(f.categoryId()!=null) p.add(cb.equal(root.get("category").get("id"),f.categoryId()));
            if(f.status()!=null) p.add(cb.equal(root.get("status"),f.status()));
            if(f.urgent()!=null) p.add(cb.equal(root.get("urgent"),f.urgent()));
            if(f.priority()!=null) p.add(cb.equal(priority.expression(root,cb,today),f.priority().name()));
            if(f.tag()!=null) p.add(cb.isMember(f.tag(),root.get("tags")));
            if(f.archived()!=null) p.add(cb.equal(root.get("archived"),f.archived()));
            if(f.budgetFrom()!=null) p.add(cb.ge(root.get("budget"),f.budgetFrom()));
            if(f.budgetTo()!=null) p.add(cb.le(root.get("budget"),f.budgetTo()));
            if(f.budgetOp()!=null) p.add(switch(f.budgetOp()) {
                case "eq" -> cb.equal(root.get("budget"),f.budgetValue());
                case "gt" -> cb.gt(root.get("budget"),f.budgetValue());
                case "gte" -> cb.ge(root.get("budget"),f.budgetValue());
                case "lt" -> cb.lt(root.get("budget"),f.budgetValue());
                case "lte" -> cb.le(root.get("budget"),f.budgetValue());
                default -> throw new IllegalArgumentException("budgetOp");
            });
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
