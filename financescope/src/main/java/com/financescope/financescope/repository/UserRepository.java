package com.financescope.financescope.repository;

import com.financescope.financescope.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // 이메일로 사용자 찾기
    Optional<User> findByEmail(String email);
    
    // 이메일 존재 여부 확인
    boolean existsByEmail(String email);
    
    // **새로 추가된 메서드들**
    // 비밀번호 재설정 토큰으로 사용자 찾기
    Optional<User> findByPasswordResetToken(String token);
    
    // 이메일 인증 토큰으로 사용자 찾기
    Optional<User> findByEmailVerificationToken(String token);
    
    // 구독 플랜별 사용자 조회
    List<User> findBySubscriptionPlan(User.SubscriptionPlan subscriptionPlan);
    
    // 구독 만료 예정 사용자 조회
    @Query("SELECT u FROM User u WHERE u.subscriptionExpiresAt BETWEEN :startDate AND :endDate")
    List<User> findUsersWithExpiringSubscription(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
    
    // 최근 로그인 사용자 조회
    @Query("SELECT u FROM User u WHERE u.lastLogin >= :since ORDER BY u.lastLogin DESC")
    List<User> findRecentlyActiveUsers(@Param("since") LocalDateTime since);
    
    // 특정 기간 내 가입한 사용자 수
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate")
    long countUsersByCreatedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
    
    // 계정 잠금된 사용자 조회
    @Query("SELECT u FROM User u WHERE u.accountLocked = true")
    List<User> findLockedUsers();
    
    // 이메일 미인증 사용자 조회
    @Query("SELECT u FROM User u WHERE u.emailVerified = false")
    List<User> findUnverifiedUsers();
    
    // 활성 상태별 사용자 조회
    List<User> findByStatus(User.UserStatus status);
    
    // 역할별 사용자 조회
    List<User> findByRole(User.UserRole role);
}