package ru.mirea.freelance.repository;

import java.util.List;
import java.util.Optional;

/**
 * Базовый контракт хранилища сущностей.
 *
 * @param <T>  тип сущности (User, Order)
 * @param <ID> тип идентификатора (Long)
 */
public interface Repository<T, ID> {

    /** Сохраняет новую сущность и возвращает её с заполненным id. */
    T save(T entity);

    /** Ищет сущность по id; пустой Optional, если записи нет. */
    Optional<T> findById(ID id);

    /** Возвращает все записи. */
    List<T> findAll();

    /** Обновляет существующую сущность по её id. */
    void update(T entity);

    /** Удаляет запись по id. */
    void deleteById(ID id);
}