package ru.mirea.youdo.controller;
import java.net.URI;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mirea.youdo.dto.OrderDtos.*;
import ru.mirea.youdo.dto.AccountDtos.PageResponse;
import ru.mirea.youdo.filter.OrderFilter;
import ru.mirea.youdo.service.*;
@RestController @RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    private final OrderBulkService bulk;
    public OrderController(OrderService orders,OrderBulkService bulk) { this.orders=orders;this.bulk=bulk; }
    @GetMapping public PageResponse<Response> list(@RequestParam Map<String,String> filter,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="createdAt,desc") String sort) { return orders.list(OrderFilter.from(filter),page,size,sort); }
    @GetMapping("/{id}") public Response get(@PathVariable UUID id) { return orders.get(id); }
    @PostMapping public ResponseEntity<Response> create(@Validated(OnCreate.class) @RequestBody Write input) {
        Response result=orders.create(input);return ResponseEntity.created(URI.create("/api/orders/"+result.id())).body(result);
    }
    @PutMapping("/{id}") public Response update(@PathVariable UUID id,@Validated(OnUpdate.class) @RequestBody Write input) { return orders.update(id,input); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> archive(@PathVariable UUID id,@RequestParam Long version) {
        orders.archive(id,version);return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/restore") public Response restore(@PathVariable UUID id,@Valid @RequestBody Version input) { return orders.restore(id,input.version()); }
    @DeleteMapping("/{id}/permanent") public ResponseEntity<Void> permanent(@PathVariable UUID id,@RequestParam Long version) {
        orders.deletePermanently(id,version);return ResponseEntity.noContent().build();
    }
    @PostMapping("/bulk") public BulkResult bulk(@Valid @RequestBody Bulk input) { return bulk.apply(input); }
}
