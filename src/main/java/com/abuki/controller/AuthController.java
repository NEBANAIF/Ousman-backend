package com.abuki.controller;

import com.abuki.dto.LoginRequest;
import com.abuki.model.User;
import com.abuki.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private UserService userService;

    // POST /api/auth/login
    // Body: { "email": "admin@abuki.com", "password": "admin123" }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            String token = userService.login(req.getEmail(), req.getPassword());
            User user    = userService.getByEmail(req.getEmail());
            return ResponseEntity.ok(Map.of(
                "token",  token,
                "id",     user.getId(),
                "name",   user.getName(),
                "email",  user.getEmail(),
                "role",   user.getRole(),
                "status", user.getStatus()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/auth/me  (requires token)
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal String email) {
        try {
            return ResponseEntity.ok(userService.getByEmail(email));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}