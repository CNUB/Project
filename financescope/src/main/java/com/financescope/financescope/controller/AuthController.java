package com.financescope.financescope.controller;

import com.financescope.financescope.dto.auth.AuthDTO;
import com.financescope.financescope.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "사용자 인증 관련 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "새로운 사용자 계정을 생성합니다.")
    public ResponseEntity<AuthDTO.AuthResponse> register(
            @Valid @RequestBody AuthDTO.RegisterRequest request) {
        log.info("회원가입 요청: {}", request.getEmail());
        
        AuthDTO.AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 로그인을 처리합니다.")
    public ResponseEntity<AuthDTO.AuthResponse> login(
            @Valid @RequestBody AuthDTO.LoginRequest request) {
        log.info("로그인 요청: {}", request.getEmail());
        
        AuthDTO.AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자 로그아웃을 처리합니다.")
    public ResponseEntity<AuthDTO.AuthResponse> logout(
            @RequestHeader("Authorization") String token) {
        log.info("로그아웃 요청");
        
        AuthDTO.AuthResponse response = authService.logout(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "리프레시 토큰을 사용하여 액세스 토큰을 갱신합니다.")
    public ResponseEntity<AuthDTO.AuthResponse> refreshToken(
            @RequestHeader("Authorization") String refreshToken) {
        log.info("토큰 갱신 요청");
        
        AuthDTO.AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate-token")
    @Operation(summary = "토큰 검증", description = "현재 토큰의 유효성을 검증합니다.")
    public ResponseEntity<AuthDTO.AuthResponse> validateToken(
            @RequestHeader("Authorization") String token) {
        log.info("토큰 검증 요청");
        
        AuthDTO.AuthResponse response = authService.validateToken(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset-request")
    @Operation(summary = "비밀번호 재설정 요청", description = "비밀번호 재설정 이메일을 발송합니다.")
    public ResponseEntity<AuthDTO.AuthResponse> requestPasswordReset(
            @Valid @RequestBody AuthDTO.PasswordResetRequest request) {
        log.info("비밀번호 재설정 요청: {}", request.getEmail());
        
        AuthDTO.AuthResponse response = authService.requestPasswordReset(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset")
    @Operation(summary = "비밀번호 재설정", description = "토큰을 사용하여 비밀번호를 재설정합니다.")
    public ResponseEntity<AuthDTO.AuthResponse> resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword) {
        log.info("비밀번호 재설정 실행");
        
        AuthDTO.AuthResponse response = authService.resetPassword(token, newPassword);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 새 비밀번호로 변경합니다.")
    public ResponseEntity<AuthDTO.AuthResponse> changePassword(
            @Valid @RequestBody AuthDTO.PasswordChangeRequest request,
            @RequestHeader("Authorization") String token) {
        log.info("비밀번호 변경 요청");
        
        AuthDTO.AuthResponse response = authService.changePassword(request, token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "이메일 인증", description = "이메일 인증 토큰을 확인합니다.")
    public ResponseEntity<AuthDTO.AuthResponse> verifyEmail(@RequestParam String token) {
        log.info("이메일 인증 요청");
        
        AuthDTO.AuthResponse response = authService.verifyEmail(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "인증 이메일 재발송", description = "이메일 인증 메일을 다시 발송합니다.")
    public ResponseEntity<AuthDTO.AuthResponse> resendVerificationEmail(
            @RequestParam String email) {
        log.info("인증 이메일 재발송 요청: {}", email);
        
        AuthDTO.AuthResponse response = authService.resendVerificationEmail(email);
        return ResponseEntity.ok(response);
    }
}
