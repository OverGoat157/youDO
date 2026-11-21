package ru.mirea.youdo.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import ru.mirea.youdo.domain.Order;
public interface OrderRepository extends JpaRepository<Order,Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByPublicId(UUID id);
    Optional<Order> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
}
