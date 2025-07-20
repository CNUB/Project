package com.financescope.financescope.service;

import com.financescope.financescope.dto.auth.AuthDTO;
import com.financescope.financescope.entity.User;
import com.financescope.financescope.exception.BusinessException;
import com.financescope.financescope.repository.UserRepository;
import com.financescope.financescope.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
//@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public AuthDTO.AuthResponse register(AuthDTO.RegisterRequest request) {
        log.info("회원가입 처리 시작: {}", request.getEmail());

        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("이미 존재하는 이메일입니다.");
        }

        // 비밀번호 강도 검사
        validatePasswordStrength(request.getPassword());

        // 사용자 생성
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .organization(request.getOrganization())
                .jobTitle(request.getJobTitle())
                .emailVerificationToken(UUID.randomUUID().toString())
                .build();

        userRepository.save(user);

        // 이메일 인증 메일 발송
        try {
            sendVerificationEmail(user);
        } catch (Exception e) {
            log.error("인증 이메일 발송 실패: {}", e.getMessage());
        }

        log.info("회원가입 완료: {}", request.getEmail());

        return AuthDTO.AuthResponse.builder()
                .success(true)
                .message("회원가입이 완료되었습니다. 이메일 인증을 완료해주세요.")
                .build();
    }

    public AuthDTO.AuthResponse login(AuthDTO.LoginRequest request) {
        log.info("로그인 처리 시작: {}", request.getEmail());

        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다."));

            // 계정 잠금 확인
            if (!user.isAccountNonLocked()) {
                throw new BusinessException("계정이 잠겨있습니다. 잠시 후 다시 시도해주세요.");
            }

            // 인증 처리
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            // 로그인 성공 처리
            user.incrementLoginCount();
            userRepository.save(user);

            // JWT 토큰 생성
            String token = jwtUtil.generateToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            log.info("로그인 성공: {}", request.getEmail());

            return AuthDTO.AuthResponse.builder()
                    .success(true)
                    .message("로그인 성공")
                    .token(token)
                    .refreshToken(refreshToken)
                    .user(convertToUserProfile(user))
                    .build();

        } catch (AuthenticationException e) {
            // 로그인 실패 처리
            userRepository.findByEmail(request.getEmail())
                    .ifPresent(user -> {
                        user.incrementFailedLoginAttempts();
                        userRepository.save(user);
                    });

            throw new BusinessException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
    }

    public AuthDTO.AuthResponse logout(String token) {
        log.info("로그아웃 처리");

        // JWT 토큰을 블랙리스트에 추가 (Redis 사용)
        // 실제 구현에서는 Redis를 사용하여 토큰을 블랙리스트에 추가
        
        return AuthDTO.AuthResponse.builder()
                .success(true)
                .message("로그아웃되었습니다.")
                .build();
    }

    public AuthDTO.AuthResponse refreshToken(String refreshToken) {
        log.info("토큰 갱신 처리");

        try {
            String userEmail = jwtUtil.extractUsername(refreshToken);
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다."));

            if (jwtUtil.validateToken(refreshToken, user)) {
                String newToken = jwtUtil.generateToken(user);
                String newRefreshToken = jwtUtil.generateRefreshToken(user);

                return AuthDTO.AuthResponse.builder()
                        .success(true)
                        .message("토큰이 갱신되었습니다.")
                        .token(newToken)
                        .refreshToken(newRefreshToken)
                        .user(convertToUserProfile(user))
                        .build();
            } else {
                throw new BusinessException("유효하지 않은 리프레시 토큰입니다.");
            }
        } catch (Exception e) {
            throw new BusinessException("토큰 갱신에 실패했습니다.");
        }
    }

    public AuthDTO.AuthResponse validateToken(String token) {
        log.info("토큰 검증 처리");

        try {
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            String userEmail = jwtUtil.extractUsername(cleanToken);
            
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다."));

            if (jwtUtil.validateToken(cleanToken, user)) {
                return AuthDTO.AuthResponse.builder()
                        .success(true)
                        .message("유효한 토큰입니다.")
                        .user(convertToUserProfile(user))
                        .build();
            } else {
                throw new BusinessException("유효하지 않은 토큰입니다.");
            }
        } catch (Exception e) {
            throw new BusinessException("토큰 검증에 실패했습니다.");
        }
    }

    public AuthDTO.AuthResponse requestPasswordReset(AuthDTO.PasswordResetRequest request) {
        log.info("비밀번호 재설정 요청: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("존재하지 않는 이메일입니다."));

        // 재설정 토큰 생성
        String resetToken = UUID.randomUUID().toString();
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetExpires(LocalDateTime.now().plusHours(1)); // 1시간 유효
        userRepository.save(user);

        // 재설정 이메일 발송
        try {
            sendPasswordResetEmail(user, resetToken);
        } catch (Exception e) {
            log.error("비밀번호 재설정 이메일 발송 실패: {}", e.getMessage());
            throw new BusinessException("이메일 발송에 실패했습니다.");
        }

        return AuthDTO.AuthResponse.builder()
                .success(true)
                .message("비밀번호 재설정 이메일이 발송되었습니다.")
                .build();
    }

    public AuthDTO.AuthResponse resetPassword(String token, String newPassword) {
        log.info("비밀번호 재설정 실행");

        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new BusinessException("유효하지 않은 재설정 토큰입니다."));

        // 토큰 만료 확인
        if (user.getPasswordResetExpires().isBefore(LocalDateTime.now())) {
            throw new BusinessException("재설정 토큰이 만료되었습니다.");
        }

        // 비밀번호 강도 검사
        validatePasswordStrength(newPassword);

        // 비밀번호 업데이트
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);
        user.resetFailedLoginAttempts();
        userRepository.save(user);

        return AuthDTO.AuthResponse.builder()
                .success(true)
                .message("비밀번호가 재설정되었습니다.")
                .build();
    }

    public AuthDTO.AuthResponse changePassword(AuthDTO.PasswordChangeRequest request, String token) {
        log.info("비밀번호 변경 처리");

        String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        String userEmail = jwtUtil.extractUsername(cleanToken);
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다."));

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("현재 비밀번호가 올바르지 않습니다.");
        }

        // 새 비밀번호 강도 검사
        validatePasswordStrength(request.getNewPassword());

        // 비밀번호 업데이트
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return AuthDTO.AuthResponse.builder()
                .success(true)
                .message("비밀번호가 변경되었습니다.")
                .build();
    }

    public AuthDTO.AuthResponse verifyEmail(String token) {
        log.info("이메일 인증 처리");

        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BusinessException("유효하지 않은 인증 토큰입니다."));

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        return AuthDTO.AuthResponse.builder()
                .success(true)
                .message("이메일 인증이 완료되었습니다.")
                .build();
    }

    public AuthDTO.AuthResponse resendVerificationEmail(String email) {
        log.info("인증 이메일 재발송: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("존재하지 않는 이메일입니다."));

        if (user.getEmailVerified()) {
            throw new BusinessException("이미 인증된 이메일입니다.");
        }

        // 새 인증 토큰 생성
        user.setEmailVerificationToken(UUID.randomUUID().toString());
        userRepository.save(user);

        // 인증 이메일 발송
        try {
            sendVerificationEmail(user);
        } catch (Exception e) {
            log.error("인증 이메일 재발송 실패: {}", e.getMessage());
            throw new BusinessException("이메일 발송에 실패했습니다.");
        }

        return AuthDTO.AuthResponse.builder()
                .success(true)
                .message("인증 이메일이 재발송되었습니다.")
                .build();
    }

    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new BusinessException("비밀번호는 최소 8자 이상이어야 합니다.");
        }
        
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException("비밀번호는 대문자를 포함해야 합니다.");
        }
        
        if (!password.matches(".*[0-9].*")) {
            throw new BusinessException("비밀번호는 숫자를 포함해야 합니다.");
        }
        
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            throw new BusinessException("비밀번호는 특수문자를 포함해야 합니다.");
        }
    }

    private void sendVerificationEmail(User user) {
        String verificationUrl = frontendUrl + "/verify-email?token=" + user.getEmailVerificationToken();
        
        String subject = "FinanceScope 이메일 인증";
        String content = String.format(
                "안녕하세요 %s님,\n\n" +
                "FinanceScope 회원가입을 완료하시려면 아래 링크를 클릭해주세요:\n\n" +
                "%s\n\n" +
                "감사합니다.\n" +
                "FinanceScope 팀",
                user.getName(), verificationUrl
        );

        emailService.sendEmail(user.getEmail(), subject, content);
    }

    private void sendPasswordResetEmail(User user, String resetToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;
        
        String subject = "FinanceScope 비밀번호 재설정";
        String content = String.format(
                "안녕하세요 %s님,\n\n" +
                "비밀번호 재설정을 요청하셨습니다. 아래 링크를 클릭하여 새 비밀번호를 설정해주세요:\n\n" +
                "%s\n\n" +
                "이 링크는 1시간 후 만료됩니다.\n\n" +
                "만약 비밀번호 재설정을 요청하지 않으셨다면 이 이메일을 무시하셔도 됩니다.\n\n" +
                "감사합니다.\n" +
                "FinanceScope 팀",
                user.getName(), resetUrl
        );

        emailService.sendEmail(user.getEmail(), subject, content);
    }

    private AuthDTO.UserProfile convertToUserProfile(User user) {
        return AuthDTO.UserProfile.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .organization(user.getOrganization())
                .jobTitle(user.getJobTitle())
                .profileImage(user.getProfileImage())
                .role(user.getRole().name())
                .subscriptionPlan(user.getSubscriptionPlan().name())
                .createdAt(user.getCreatedAt().toString())
                .lastLogin(user.getLastLogin() != null ? user.getLastLogin().toString() : null)
                .build();
    }
}
