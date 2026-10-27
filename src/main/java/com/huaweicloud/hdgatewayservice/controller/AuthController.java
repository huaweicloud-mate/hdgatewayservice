package com.huaweicloud.hdgatewayservice.controller;

import com.huaweicloud.hdgatewayservice.model.LoginRequest;
import com.huaweicloud.hdgatewayservice.model.LoginResponse;
import com.huaweicloud.hdgatewayservice.service.JwtService;
import com.huaweicloud.hdgatewayservice.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/rest/developer/server/hdgatewayservice/auth")
public class AuthController {

    @Value("${hdgateway.auth.admin-username}")
    private String adminUsername;

    @Value("${hdgateway.auth.admin-password}")
    private String adminPassword;

    private final JwtService jwtService;
    private final LoginAttemptService loginAttempts;

    public AuthController(JwtService jwtService, LoginAttemptService loginAttempts) {
        this.jwtService = jwtService;
        this.loginAttempts = loginAttempts;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.username();
        String password = request.password();
        String ip = clientIp(httpRequest);

        if (loginAttempts.isLocked(username, ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "尝试次数过多，请稍后再试"));
        }

        boolean ok = username != null && password != null
                && MessageDigest.isEqual(
                        adminUsername.getBytes(StandardCharsets.UTF_8),
                        username.getBytes(StandardCharsets.UTF_8))
                && MessageDigest.isEqual(
                        adminPassword.getBytes(StandardCharsets.UTF_8),
                        password.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            loginAttempts.recordFailure(username, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "用户名或密码错误"));
        }

        loginAttempts.recordSuccess(username, ip);
        String token = jwtService.generateToken(adminUsername);
        return ResponseEntity.ok(new LoginResponse(token, adminUsername, 24));
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
