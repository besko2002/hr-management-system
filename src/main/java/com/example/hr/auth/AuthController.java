package com.example.hr.auth;

import com.example.hr.auth.AuthDtos.AuthenticatedUser;
import com.example.hr.auth.AuthDtos.ChangePasswordRequest;
import com.example.hr.auth.AuthDtos.LoginRequest;
import com.example.hr.auth.AuthDtos.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange e-mail and password for a JWT (no public registration)")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change your own password; clears the must-change-password flag")
    AuthenticatedUser changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return auth.changePassword(request);
    }
}
