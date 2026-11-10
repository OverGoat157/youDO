package ru.mirea.youdo.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ru.mirea.youdo.dto.AuthDtos.*;
import ru.mirea.youdo.security.*;
import ru.mirea.youdo.service.AuthService;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthTransport transport;
    public AuthController(AuthService auth, AuthTransport transport) { this.auth=auth; this.transport=transport; }
    @PostMapping(value="/login", consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Вход: WEB получает HttpOnly cookie, ANDROID — refreshToken в JSON без Origin/cookie")
    public TokenResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        transport.validate(req,body.clientType());
        var issued=auth.login(body,req.getRemoteAddr()); // Never trust client-supplied X-Forwarded-For.
        if (body.clientType() == ClientType.WEB) transport.setCookie(res,issued.refreshToken());
        res.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        return issued.response();
    }
    @PostMapping(value="/refresh", consumes=MediaType.APPLICATION_JSON_VALUE)
    public TokenResponse refresh(@Valid @RequestBody SessionRequest body, HttpServletRequest req, HttpServletResponse res) {
        String raw=transport.refresh(req,body.clientType(),body.refreshToken());
        var issued=auth.refresh(raw,body.clientType());
        if (body.clientType() == ClientType.WEB) transport.setCookie(res,issued.refreshToken());
        res.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        return issued.response();
    }
    @PostMapping(value="/logout", consumes=MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody SessionRequest body, HttpServletRequest req, HttpServletResponse res) {
        String raw=transport.refresh(req,body.clientType(),body.refreshToken());
        auth.logout(raw,body.clientType());
        if (body.clientType() == ClientType.WEB) transport.setCookie(res,null);
    }
    @GetMapping("/me")
    public UserResponse me() { return UserResponse.from(CurrentUser.require()); }
    @PostMapping(value="/change-password", consumes=MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest req, HttpServletResponse res) {
        // Bearer-authenticated Android requests have no ambient cookie; WEB must pass Origin.
        transport.validate(req,req.getHeader("Origin") != null || req.getCookies() != null ? ClientType.WEB : ClientType.ANDROID);
        auth.changePassword(CurrentUser.require(),body);
        transport.setCookie(res,null);
    }
}
