package ru.mirea.youdo.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.dao.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mirea.youdo.dto.OrderDtos.*;
import ru.mirea.youdo.error.ApiException;
@Service
public class OrderBulkService {
    private final OrderService orders;
    private final TransactionTemplate transaction;
    public OrderBulkService(OrderService orders,PlatformTransactionManager manager) {
        this.orders=orders;transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public BulkResult apply(Bulk input) {
        OrderService.requireEditor();
        if(input==null || input.action()==null || input.items()==null || input.items().isEmpty() || input.items().size()>100)
            throw AccountProblem.invalid("items","Укажите действие и от 1 до 100 заказов");
        var ids=new HashSet<UUID>();
        for(Item i:input.items()) if(i==null || i.id()==null || i.version()==null || i.version()<0 || !ids.add(i.id()))
            throw AccountProblem.invalid("items","ID должны быть уникальны, версии неотрицательны");
        if(input.action()==BulkAction.CHANGE_STATUS && input.status()==null) throw AccountProblem.invalid("status","Укажите статус");
        if(input.action()==BulkAction.ADD_TAG || input.action()==BulkAction.REMOVE_TAG)
            OrderService.normalizeTags(Collections.singletonList(input.tag()));
        var succeeded=new ArrayList<Success>();var failed=new ArrayList<Failure>();
        for(Item i:input.items()) {
            try {
                Response r=transaction.execute(s -> switch(input.action()) {
                    case ARCHIVE -> orders.archive(i.id(),i.version());
                    case RESTORE -> orders.restore(i.id(),i.version());
                    case CHANGE_STATUS -> orders.changeStatus(i.id(),i.version(),input.status());
                    case ADD_TAG -> orders.changeTag(i.id(),i.version(),input.tag(),true);
                    case REMOVE_TAG -> orders.changeTag(i.id(),i.version(),input.tag(),false);
                });
                succeeded.add(new Success(i.id(),r.version()));
            } catch(AccountProblem e) { failed.add(new Failure(i.id(),e.code(),e.getMessage())); }
            catch(ApiException e) { failed.add(new Failure(i.id(),e.code(),e.getMessage())); }
            catch(OptimisticLockingFailureException e) { failed.add(new Failure(i.id(),"VERSION_CONFLICT","Заказ уже изменён")); }
            catch(DataIntegrityViolationException e) { failed.add(new Failure(i.id(),"DATA_CONFLICT","Изменение нарушает связи данных")); }
        }
        return new BulkResult(succeeded,failed);
    }
}
