package ru.mirea.freelance.exception;

/** Запрошенный объект отсутствует в хранилище. */
public class EntityNotFoundException extends BusinessException {
    private final String entity;
    private final Long id;

    public EntityNotFoundException(String entity, Long id) {
        super(entity + " с ID " + id + " не найден");
        this.entity = entity;
        this.id = id;
    }

    public String getEntity() { return entity; }
    public Long getId() { return id; }
}
