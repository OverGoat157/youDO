package ru.mirea.youdo.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.youdo.dto.CsvDtos.*;
import ru.mirea.youdo.service.CsvOrderService;
import ru.mirea.youdo.service.OrderPriorityCalculator;

/** Экспорт и двухшаговый импорт заказов. Права: экспорт — VIEWER, импорт — EDITOR (SecurityConfig + сервис). */
@RestController
@RequestMapping("/api/orders")
public class CsvController {
    private final CsvOrderService csv;
    private final OrderPriorityCalculator priority;

    public CsvController(CsvOrderService csv, OrderPriorityCalculator priority) { this.csv = csv; this.priority = priority; }

    /** Те же параметры фильтра и sort, что у GET /api/orders, но без page/size: выгружаются все совпавшие заказы. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Map<String, String> params) {
        LocalDate today = priority.today();
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("orders-" + today + ".csv").build().toString())
            .body(csv.export(params));
    }

    @PostMapping(path = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview preview(@RequestParam(value = "file", required = false) MultipartFile file,
                                 @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.preview(file, mode);
    }

    @PostMapping("/import/apply")
    public ImportResult apply(@Valid @RequestBody ApplyRequest request) {
        return csv.apply(request.previewId());
    }
}
