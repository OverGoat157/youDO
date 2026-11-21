package ru.mirea.youdo.controller;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mirea.youdo.dto.OrderDtos.*;
import ru.mirea.youdo.service.OrderNoteService;
@RestController @RequestMapping("/api/orders/{orderId}/notes")
public class OrderNoteController {
    private final OrderNoteService notes;
    public OrderNoteController(OrderNoteService notes) { this.notes=notes; }
    @GetMapping public List<NoteResponse> list(@PathVariable UUID orderId) { return notes.list(orderId); }
    @PostMapping public ResponseEntity<NoteResponse> create(@PathVariable UUID orderId,@Valid @RequestBody NoteWrite input) {
        var n=notes.create(orderId,input);return ResponseEntity.created(java.net.URI.create("/api/orders/"+orderId+"/notes/"+n.id())).body(n);
    }
    @PutMapping("/{noteId}") public NoteResponse update(@PathVariable UUID orderId,@PathVariable UUID noteId,@Valid @RequestBody NoteWrite input) { return notes.update(orderId,noteId,input); }
    @DeleteMapping("/{noteId}") public ResponseEntity<Void> delete(@PathVariable UUID orderId,@PathVariable UUID noteId) { notes.delete(orderId,noteId);return ResponseEntity.noContent().build(); }
}
