package ru.mirea.youdo.domain;
import jakarta.persistence.*;
import java.util.*;
@Entity @Table(name="orders")
public class Order {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,updatable=false) private java.util.UUID publicId;
    @Column(nullable=false,unique=true,length=50) private String code;
    @Column(nullable=false,length=150) private String title;
    @Column(columnDefinition="text") private String description;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="category_id") private Category category;
    @Column(nullable=false,precision=12,scale=2) private java.math.BigDecimal budget;
    @Column(nullable=false) private java.time.LocalDate deadline;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private OrderStatus status;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="customer_id",updatable=false) private Participant customer;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="freelancer_id") private Participant freelancer;
    @Column(nullable=false) private boolean urgent;
    @Column(nullable=false) private boolean archived;
    @Version @Column(nullable=false) private long version;
    @Column(nullable=false,updatable=false) private java.time.Instant createdAt;
    @Column(nullable=false) private java.time.Instant updatedAt;
    @ElementCollection @CollectionTable(name="order_tags",joinColumns=@JoinColumn(name="order_id"))
    @Column(name="tag",nullable=false,length=50) private Set<String> tags = new LinkedHashSet<>();
    protected Order() {}
    public Order(java.time.Instant now) { publicId=UUID.randomUUID(); createdAt=now; updatedAt=now; }
    public void edit(String code, String title, String description, Category category, java.math.BigDecimal budget,
            java.time.LocalDate deadline, OrderStatus status, Participant customer, Participant freelancer,
            boolean urgent, Set<String> tags, java.time.Instant now) {
        this.code=code; this.title=title; this.description=description; this.category=category; this.budget=budget;
        this.deadline=deadline; this.status=status; this.customer=customer; this.freelancer=freelancer;
        this.urgent=urgent; this.tags.clear(); this.tags.addAll(tags); touch(now);
    }
    public void archive(boolean value, java.time.Instant now) { archived=value; touch(now); }
    public void changeStatus(OrderStatus value, java.time.Instant now) { status=value; touch(now); }
    public void changeTags(Set<String> value, java.time.Instant now) { tags.clear(); tags.addAll(value); touch(now); }
    // Always dirty the parent, including tag-only/note changes with a fixed test Clock.
    public void touch(java.time.Instant now) { updatedAt=now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000); }
    public Set<String> getTags() { return Collections.unmodifiableSet(tags); }
    public Long getId() { return id; }
    public java.util.UUID getPublicId() { return publicId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public java.math.BigDecimal getBudget() { return budget; }
    public java.time.LocalDate getDeadline() { return deadline; }
    public OrderStatus getStatus() { return status; }
    public Participant getCustomer() { return customer; }
    public Participant getFreelancer() { return freelancer; }
    public boolean isUrgent() { return urgent; }
    public boolean isArchived() { return archived; }
    public long getVersion() { return version; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }
}
