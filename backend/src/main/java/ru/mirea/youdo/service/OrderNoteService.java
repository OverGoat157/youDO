package ru.mirea.youdo.service;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.mirea.youdo.domain.*;
import ru.mirea.youdo.dto.OrderDtos.*;
import ru.mirea.youdo.repository.*;
@Service @Transactional(readOnly=true)
public class OrderNoteService {
    private final OrderRepository orders;
    private final OrderNoteRepository notes;
    private final AccountRepository accounts;
    private final AuditWriter audit;
    private final Clock clock;
    public OrderNoteService(OrderRepository orders,OrderNoteRepository notes,AccountRepository accounts,AuditWriter audit,Clock clock) {
        this.orders=orders;this.notes=notes;this.accounts=accounts;this.audit=audit;this.clock=clock;
    }
    private Order find(UUID id) { return orders.findByPublicId(id).orElseThrow(()->AccountProblem.missing("Заказ")); }
    @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
    public List<NoteResponse> list(UUID orderId) {
        return notes.findByOrderIdOrderByPinnedDescCreatedAtAscIdAsc(find(orderId).getId()).stream().map(NoteResponse::of).toList();
    }
    @Transactional
    public NoteResponse create(UUID orderId,NoteWrite input) {
        var actor=OrderService.requireEditor(); Order o=find(orderId); OrderService.requireUnarchived(o);
        String text=OrderService.required(input.text(),5000,"text");
        if(input.pinned()==null) throw AccountProblem.invalid("pinned","Укажите pinned");
        var author=accounts.findById(actor.id()).orElseThrow(()->AccountProblem.missing("Автор"));
        var n=notes.save(new OrderNote(o,author,text,input.pinned(),clock.instant()));
        changed(o,"ORDER_NOTE_CREATED",n.getId()); return NoteResponse.of(n);
    }
    @Transactional
    public NoteResponse update(UUID orderId,UUID noteId,NoteWrite input) {
        OrderService.requireEditor(); Order o=find(orderId); OrderService.requireUnarchived(o);
        OrderNote n=note(o,noteId);
        if(input.pinned()==null) throw AccountProblem.invalid("pinned","Укажите pinned");
        n.edit(OrderService.required(input.text(),5000,"text"),input.pinned(),clock.instant());
        changed(o,"ORDER_NOTE_UPDATED",noteId); return NoteResponse.of(n);
    }
    @Transactional
    public void delete(UUID orderId,UUID noteId) {
        OrderService.requireEditor(); Order o=find(orderId); OrderService.requireUnarchived(o);
        notes.delete(note(o,noteId)); changed(o,"ORDER_NOTE_DELETED",noteId);
    }
    private OrderNote note(Order o,UUID id) { return notes.findByIdAndOrderId(id,o.getId()).orElseThrow(()->AccountProblem.missing("Заметка")); }
    private void changed(Order o,String action,UUID id) {
        o.touch(clock.instant()); orders.flush(); notes.flush();
        audit.record(action,"ORDER_NOTE",id,"Изменена заметка заказа "+o.getPublicId());
    }
}
