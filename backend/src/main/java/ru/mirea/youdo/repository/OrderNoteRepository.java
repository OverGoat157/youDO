package ru.mirea.youdo.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.youdo.domain.OrderNote;
public interface OrderNoteRepository extends JpaRepository<OrderNote,UUID> {
    List<OrderNote> findByOrderIdOrderByPinnedDescCreatedAtAscIdAsc(Long orderId);
    Optional<OrderNote> findByIdAndOrderId(UUID id,Long orderId);
    void deleteByOrderId(Long orderId);
}
