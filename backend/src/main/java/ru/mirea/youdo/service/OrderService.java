package ru.mirea.youdo.service;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Sort;
import ru.mirea.youdo.config.YoudoProperties;
import ru.mirea.youdo.domain.*;
import ru.mirea.youdo.dto.OrderDtos.*;
import ru.mirea.youdo.dto.AccountDtos.PageResponse;
import ru.mirea.youdo.error.ApiException;
import ru.mirea.youdo.filter.*;
import ru.mirea.youdo.repository.*;
import ru.mirea.youdo.security.CurrentUser;

@Service @Transactional(readOnly=true)
public class OrderService {
    private final OrderRepository orders;
    private final ParticipantRepository participants;
    private final OrderNoteRepository notes;
    private final CategoryService categories;
    private final AuditWriter audit;
    private final Clock clock;
    private final OrderPriorityCalculator priority;
    private final YoudoProperties properties;
    private final jakarta.validation.Validator validator;
    public OrderService(OrderRepository orders,ParticipantRepository participants,OrderNoteRepository notes,
            CategoryService categories,AuditWriter audit,Clock clock,OrderPriorityCalculator priority,YoudoProperties properties,jakarta.validation.Validator validator) {
        this.orders=orders; this.participants=participants; this.notes=notes; this.categories=categories;
        this.audit=audit; this.clock=clock; this.priority=priority; this.properties=properties; this.validator=validator;
    }
    @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
    public PageResponse<Response> list(OrderFilter filter,int page,int size,String sort) {
        LocalDate today=priority.today();
        var found=orders.findAll(OrderSpecifications.matching(filter,priority,today),
            QueryParameters.page(page,size,sort(sort)));
        return new PageResponse<>(found.getContent().stream().map(o->response(o,today)).toList(),
            page,size,found.getTotalElements(),found.getTotalPages());
    }
    /** All matching rows, not one page. Export/analytics must reuse the same filter and date. */
    @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
    public List<ExportRow> findForExport(OrderFilter filter,String sort) {
        LocalDate today=priority.today();
        return orders.findAll(OrderSpecifications.matching(filter,priority,today),sort(sort)).stream()
            .map(o->new ExportRow(o.getCode(),o.getTitle(),o.getDescription(),o.getCategory().getCode(),
                o.getStatus(),o.getBudget().toPlainString(),o.getDeadline(),o.getCustomer().getEmail(),
                o.getFreelancer()==null?null:o.getFreelancer().getEmail(),o.isUrgent(),o.getTags().stream().sorted().toList())).toList();
    }
    private Sort sort(String sort) {
        return QueryParameters.sort(sort,Set.of("title","budget","deadline","createdAt"),"id");
    }
    @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
    public Response get(UUID id) { return response(find(id),priority.today()); }
    /** External CSV key, exact and case sensitive, like the database UNIQUE constraint. */
    @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
    public Optional<Response> findByCode(String code) {
        return orders.findByCode(code).map(o->response(o,priority.today()));
    }
    @Transactional
    public Response create(Write input) {
        requireEditor();
        Validated v=validate(input,null);
        Order o=new Order(clock.instant()); apply(o,v);
        orders.saveAndFlush(o);
        audit.record("ORDER_CREATED","ORDER",o.getPublicId(),"Создан заказ");
        return response(o,priority.today());
    }
    @Transactional
    public Response update(UUID id,Write input) {
        requireEditor();
        Order o=find(id); Validated v=validate(input,o); apply(o,v);
        return saved(o,"ORDER_UPDATED","Изменён заказ");
    }
    @Transactional
    public Response updateByCode(String code,Write input) {
        requireEditor();
        Order o=orders.findByCode(code).orElseThrow(()->AccountProblem.missing("Заказ"));
        Validated v=validate(input,o); apply(o,v);
        return saved(o,"ORDER_UPDATED","Изменён заказ");
    }
    /** Preview uses the same validation, without modifying a managed entity or writing audit. */
    public void validateCreate(Write input) { requireEditor(); validate(input,null); }
    public void validateUpdate(UUID id,Write input) { requireEditor(); validate(input,find(id)); }
    public void validateUpdateByCode(String code,Write input) {
        requireEditor(); validate(input,orders.findByCode(code).orElseThrow(()->AccountProblem.missing("Заказ")));
    }
    @Transactional
    public Response archive(UUID id,Long version) { return setArchived(id,version,true); }
    @Transactional
    public Response restore(UUID id,Long version) { return setArchived(id,version,false); }
    private Response setArchived(UUID id,Long version,boolean archived) {
        requireEditor(); Order o=find(id); checkVersion(o,version);
        if(o.isArchived()==archived) return response(o,priority.today());
        o.archive(archived,clock.instant());
        return saved(o,archived?"ORDER_ARCHIVED":"ORDER_RESTORED",archived?"Заказ архивирован":"Заказ восстановлен");
    }
    @Transactional
    public void deletePermanently(UUID id,Long version) {
        CurrentUser user=requireEditor();
        if(!user.roles().contains("ADMIN") || !properties.permanentDeleteEnabled())
            throw new ApiException(403,"FORBIDDEN","Постоянное удаление недоступно");
        Order o=find(id); checkVersion(o,version);
        if(!o.isArchived()) throw state("Постоянное удаление доступно только в архиве");
        notes.deleteByOrderId(o.getId()); orders.delete(o); orders.flush();
        audit.record("ORDER_DELETED","ORDER",id,"Заказ удалён навсегда");
    }
    @Transactional
    public Response changeStatus(UUID id,Long version,OrderStatus status) {
        requireEditor(); Order o=find(id); checkVersion(o,version); requireUnarchived(o);
        if(o.getStatus().isFinal()) throw state("Статус финального заказа менять нельзя");
        checkTransition(o.getStatus(),status,o.getFreelancer());
        if(o.getDeadline().isBefore(priority.today())) throw AccountProblem.invalid("deadline","Дедлайн не может быть в прошлом");
        o.changeStatus(status,clock.instant()); return saved(o,"ORDER_STATUS_CHANGED","Изменён статус заказа");
    }
    @Transactional
    public Response changeTag(UUID id,Long version,String tag,boolean add) {
        requireEditor(); Order o=find(id); checkVersion(o,version); requireUnarchived(o);
        Set<String> value=new LinkedHashSet<>(o.getTags());
        String normalized=normalizeTags(Collections.singletonList(tag)).iterator().next();
        if(add) value.add(normalized); else value.remove(normalized);
        if(value.size()>20) throw AccountProblem.invalid("tags","Допускается до 20 тегов");
        o.changeTags(value,clock.instant()); return saved(o,"ORDER_TAGS_CHANGED","Изменены теги заказа");
    }
    private Order find(UUID id) {
        return orders.findByPublicId(id).orElseThrow(()->AccountProblem.missing("Заказ"));
    }
    private Response saved(Order o,String action,String summary) {
        orders.flush(); audit.record(action,"ORDER",o.getPublicId(),summary);
        return response(o,priority.today());
    }
    private void apply(Order o,Validated v) {
        o.edit(v.code(),v.title(),v.description(),v.category(),v.budget(),v.deadline(),v.status(),
            v.customer(),v.freelancer(),v.urgent(),v.tags(),clock.instant());
    }
    private record Validated(String code,String title,String description,Category category,BigDecimal budget,
        LocalDate deadline,OrderStatus status,Participant customer,Participant freelancer,boolean urgent,Set<String> tags) {}
    private Validated validate(Write i,Order old) {
        if(i==null) throw AccountProblem.invalid("order","Укажите заказ");
        var violations=validator.validate(i,old==null?OnCreate.class:OnUpdate.class);
        if(!violations.isEmpty()) {
            var v=violations.stream().sorted(java.util.Comparator.comparing(x->x.getPropertyPath().toString())).findFirst().orElseThrow();
            throw AccountProblem.invalid(v.getPropertyPath().toString(),v.getMessage());
        }
        if(old!=null) { checkVersion(old,i.version()); requireUnarchived(old); }
        else if(i.version()!=null) throw AccountProblem.invalid("version","Версия назначается сервером");
        String code=required(i.code(),50,"code"),title=required(i.title(),150,"title");
        String description=i.description()==null?"":i.description();
        if(description.length()>5000) throw AccountProblem.invalid("description","Описание до 5000 символов");
        BigDecimal budget;
        try { budget=new BigDecimal(i.budget()).setScale(2,RoundingMode.UNNECESSARY); }
        catch(RuntimeException e) { throw AccountProblem.invalid("budget","Укажите бюджет с точностью до копеек"); }
        if(budget.signum()<=0 || budget.compareTo(new BigDecimal("9999999999.99"))>0)
            throw AccountProblem.invalid("budget","Бюджет должен быть положительным и не больше 9999999999.99");
        if(i.deadline()==null) throw AccountProblem.invalid("deadline","Укажите дедлайн");
        if(i.categoryId()==null) throw AccountProblem.invalid("categoryId","Выберите категорию");
        Category category=categories.requireAssignable(i.categoryId(),old==null?null:old.getCategory().getId());
        if(old!=null && !Objects.equals(old.getCustomer().getId(),i.customerId()))
            throw AccountProblem.invalid("customerId","Заказчик после создания неизменен");
        Participant customer=participant(i.customerId(),Participant.Role.CUSTOMER,"customerId");
        Participant freelancer=i.freelancerId()==null?null:participant(i.freelancerId(),Participant.Role.FREELANCER,"freelancerId");
        if(Objects.equals(i.customerId(),i.freelancerId())) throw AccountProblem.invalid("freelancerId","Заказчик не может выполнять свой заказ");
        OrderStatus status=i.status()==null && old==null?OrderStatus.OPEN:i.status();
        if(status==null) throw AccountProblem.invalid("status","Укажите статус");
        // CSV may create a row in an existing lifecycle state; participant invariants still hold.
        if(old!=null) checkTransition(old.getStatus(),status,freelancer);
        else if((status==OrderStatus.IN_PROGRESS || status==OrderStatus.ON_REVIEW || status==OrderStatus.COMPLETED) && freelancer==null)
            throw AccountProblem.invalid("freelancerId","Для работы нужен исполнитель");
        Set<String> tags=normalizeTags(i.tags()); boolean urgent=Boolean.TRUE.equals(i.urgent());
        boolean onlyTags=old!=null && old.getCode().equals(code) && old.getTitle().equals(title)
            && Objects.equals(Objects.requireNonNullElse(old.getDescription(),""),description)
            && old.getCategory().getId().equals(category.getId()) && old.getBudget().compareTo(budget)==0
            && old.getDeadline().equals(i.deadline()) && old.getStatus()==status
            && Objects.equals(old.getFreelancer()==null?null:old.getFreelancer().getId(),i.freelancerId()) && old.isUrgent()==urgent;
        if(old!=null && old.getStatus().isFinal() && !onlyTags) throw state("У финального заказа можно менять только теги, заметки и архив");
        if(!onlyTags && i.deadline().isBefore(priority.today())) throw AccountProblem.invalid("deadline","Дедлайн не может быть в прошлом");
        if(old==null?orders.existsByCode(code):orders.existsByCodeAndIdNot(code,old.getId())) throw AccountProblem.duplicate("code");
        return new Validated(code,title,description,category,budget,i.deadline(),status,customer,freelancer,urgent,tags);
    }
    private Participant participant(Long id,Participant.Role role,String field) {
        if(id==null) throw AccountProblem.invalid(field,"Выберите участника");
        Participant p=participants.findById(id).orElseThrow(()->AccountProblem.invalid(field,"Участник не найден"));
        if(p.getRole()!=role) throw AccountProblem.invalid(field,"Неверная роль участника");
        return p;
    }
    static void checkTransition(OrderStatus current,OrderStatus next,Participant freelancer) {
        if(next==null || !current.canTransitionTo(next)) throw AccountProblem.invalid("status","Переход статуса запрещён");
        if((next==OrderStatus.IN_PROGRESS || next==OrderStatus.ON_REVIEW || next==OrderStatus.COMPLETED) && freelancer==null)
            throw AccountProblem.invalid("freelancerId","Для работы нужен исполнитель");
    }
    static String required(String value,int max,String field) {
        if(value==null || value.isBlank() || value.strip().length()>max) throw AccountProblem.invalid(field,"Обязательное поле, максимум "+max+" символов");
        return value.strip();
    }
    public static Set<String> normalizeTags(List<String> input) {
        var result=new LinkedHashSet<String>();
        if(input!=null) for(String tag:input) {
            String value=required(tag,50,"tags").toLowerCase(Locale.ROOT);
            if(value.contains("|")) throw AccountProblem.invalid("tags","Символ | в теге запрещён");
            result.add(value);
        }
        if(result.size()>20) throw AccountProblem.invalid("tags","Допускается до 20 тегов");
        return result;
    }
    static CurrentUser requireEditor() {
        CurrentUser user=CurrentUser.require();
        if(user.mustChangePassword()) throw new ApiException(403,"PASSWORD_CHANGE_REQUIRED","Сначала смените временный пароль");
        if(Collections.disjoint(user.roles(),Set.of("EDITOR","ADMIN"))) throw new ApiException(403,"FORBIDDEN","Недостаточно прав");
        return user;
    }
    static void checkVersion(Order o,Long version) {
        if(version==null || version<0) throw AccountProblem.invalid("version","Укажите неотрицательную версию");
        if(o.getVersion()!=version) throw new ApiException(409,"VERSION_CONFLICT","Заказ уже изменён");
    }
    static void requireUnarchived(Order o) { if(o.isArchived()) throw state("Архивный заказ нельзя изменять"); }
    static ApiException state(String message) { return new ApiException(409,"STATE_CONFLICT",message); }
    private Response response(Order o,LocalDate today) {
        Participant f=o.getFreelancer();
        return new Response(o.getPublicId(),o.getCode(),o.getTitle(),o.getDescription(),o.getCategory().getId(),o.getCategory().getName(),
            o.getStatus(),o.getBudget().toPlainString(),o.getDeadline(),o.getCustomer().getId(),o.getCustomer().getName(),
            f==null?null:f.getId(),f==null?null:f.getName(),o.isUrgent(),o.getTags().stream().sorted().toList(),
            priority.calculate(o.getStatus(),o.isUrgent(),o.getDeadline(),today),o.isArchived(),o.getCreatedAt(),o.getUpdatedAt(),o.getVersion());
    }
}
