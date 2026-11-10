package ru.mirea.youdo.repository;

import ru.mirea.youdo.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findAllByOrderByNameAscIdAsc();
    List<Category> findByActiveOrderByNameAscIdAsc(boolean active);
    Optional<Category> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, UUID id);
}
