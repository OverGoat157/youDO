package ru.mirea.youdo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** History API routes load the public shell; protected data still comes from /api. */
@Controller
public class WebPageController {
    @GetMapping({"/login", "/change-password", "/admin/users", "/categories",
        "/orders", "/orders/new", "/orders/{id}", "/orders/{id}/edit",
        "/csv", "/analytics", "/audit", "/docs"})
    public String page() {
        return "forward:/index.html";
    }
}
