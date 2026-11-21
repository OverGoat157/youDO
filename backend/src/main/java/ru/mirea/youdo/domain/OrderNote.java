package ru.mirea.youdo.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="order_notes")
public class OrderNote {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id") private Order order;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="author_id") private UserAccount author;
    @Column(nullable=false,length=5000) private String text;
    @Column(nullable=false) private boolean pinned;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    protected OrderNote() {}
    public OrderNote(Order order, UserAccount author, String text, boolean pinned, Instant now) {
        id=UUID.randomUUID(); this.order=order; this.author=author; createdAt=now; edit(text,pinned,now);
    }
    public void edit(String text, boolean pinned, Instant now) { this.text=text; this.pinned=pinned; updatedAt=now; }
    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public UserAccount getAuthor() { return author; }
    public String getText() { return text; }
    public boolean isPinned() { return pinned; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
