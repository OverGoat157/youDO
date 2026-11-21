package ru.mirea.youdo.controller;

import org.springframework.web.bind.annotation.*;
import ru.mirea.youdo.dto.AnalyticsDtos.Summary;
import ru.mirea.youdo.service.AnalyticsService;

/** Сводка по заказам; доступна любой роли (VIEWER и выше). */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping("/summary")
    public Summary summary() { return analytics.summary(); }
}
