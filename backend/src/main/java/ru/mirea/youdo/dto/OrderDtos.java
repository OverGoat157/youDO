package ru.mirea.youdo.dto;
import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.mirea.youdo.domain.*;
public final class OrderDtos {
    private OrderDtos() {}
    /** Shared input for web/CSV. version is absent on create and mandatory on update. */
    public interface OnCreate extends jakarta.validation.groups.Default {}
    public interface OnUpdate extends jakarta.validation.groups.Default {}
    public record Write(@NotBlank @Size(max=50) String code, @NotBlank @Size(max=150) String title,
        @Size(max=5000) String description, @NotNull UUID categoryId,
        @NotNull(groups=OnUpdate.class) OrderStatus status,
        @NotBlank @Size(max=100) String budget, @NotNull LocalDate deadline,
        @NotNull @Positive Long customerId, @Positive Long freelancerId, Boolean urgent,
        @Size(max=20) List<@NotBlank @Size(max=50) String> tags,
        @Null(groups=OnCreate.class) @NotNull(groups=OnUpdate.class) @PositiveOrZero Long version) {}
    public record Response(UUID id, String code, String title, String description, UUID categoryId, String categoryName,
        OrderStatus status, String budget, LocalDate deadline, Long customerId, String customerName,
        Long freelancerId, String freelancerName, boolean urgent, List<String> tags, OrderPriority priority,
        boolean archived, Instant createdAt, Instant updatedAt, long version) {}
    /** CSV columns resolved within the read transaction; no lazy entities escape. */
    public record ExportRow(String code, String title, String description, String categoryCode,
        OrderStatus status, String budget, LocalDate deadline, String customerEmail,
        String freelancerEmail, boolean urgent, List<String> tags) {}
    public record Version(@NotNull @PositiveOrZero Long version) {}
    public enum BulkAction { ARCHIVE, RESTORE, CHANGE_STATUS, ADD_TAG, REMOVE_TAG }
    public record Item(@NotNull UUID id, @NotNull @PositiveOrZero Long version) {}
    public record Bulk(@NotNull BulkAction action, @NotEmpty @Size(max=100) List<@NotNull @Valid Item> items,
        OrderStatus status, String tag) {}
    public record Success(UUID id,long version) {}
    public record Failure(UUID id,String code,String message) {}
    public record BulkResult(List<Success> succeeded,List<Failure> failed) {}
    public record NoteWrite(@NotBlank @Size(max=5000) String text, @NotNull Boolean pinned) {}
    public record NoteResponse(UUID id, String text, boolean pinned, UUID authorId, String authorName,
        Instant createdAt, Instant updatedAt) {
        public static NoteResponse of(OrderNote n) {
            return new NoteResponse(n.getId(), n.getText(), n.isPinned(), n.getAuthor().getId(),
                n.getAuthor().getName(), n.getCreatedAt(), n.getUpdatedAt());
        }
    }
    public record ParticipantResponse(Long id,String name,String email,Participant.Role role) {
        public static ParticipantResponse of(Participant p) { return new ParticipantResponse(p.getId(),p.getName(),p.getEmail(),p.getRole()); }
    }
}
