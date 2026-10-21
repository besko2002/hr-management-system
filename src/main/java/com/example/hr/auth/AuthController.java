package com.example.hr.auth;

import com.example.hr.auth.AuthDtos.AuthenticatedUser;
import com.example.hr.auth.AuthDtos.ChangePasswordRequest;
import com.example.hr.auth.AuthDtos.LoginRequest;
import com.example.hr.auth.AuthDtos.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
class AuthController {

    static final String ACCESS_TOKEN_HEADER = "X-Access-Token";

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange e-mail and password for a JWT (no public registration)")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return auth.login(request, http.getRemoteAddr());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change your own password; clears the must-change-password flag. Every token issued "
            + "before the change stops working; a fresh one is returned in the X-Access-Token header")
    ResponseEntity<AuthenticatedUser> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthService.PasswordChanged changed = auth.changePassword(request);
        return ResponseEntity.ok().header(ACCESS_TOKEN_HEADER, changed.accessToken()).body(changed.user());
    }
}
