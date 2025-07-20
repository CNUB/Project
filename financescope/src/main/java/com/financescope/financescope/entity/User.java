// User.java
package com.financescope.financescope.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"newsItems", "indicatorAnalyses", "predictions"}) // 순환참조 방지
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String name;
    
    @Column(length = 500)
    private String organization;
    
    @Column(length = 200)
    private String jobTitle;
    
    @Column(length = 1000)
    private String profileImage;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;
    
    private LocalDateTime subscriptionExpiresAt;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean emailNotifications = true;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Theme theme = Theme.LIGHT;
    
    @Column(columnDefinition = "TEXT")
    private String defaultIndicators;
    
    @Column(length = 50)
    @Builder.Default
    private String defaultModel = "prophet";
    
    private LocalDateTime lastLogin;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer loginCount = 0;
    
    // **새로 추가된 필드들**
    @Column(name = "email_verification_token")
    private String emailVerificationToken;
    
    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;
    
    @Column(name = "password_reset_token")
    private String passwordResetToken;
    
    @Column(name = "password_reset_expires")
    private LocalDateTime passwordResetExpires;
    
    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;
    
    @Column(name = "account_locked")
    @Builder.Default
    private Boolean accountLocked = false;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    // // 연관관계 매핑 - 성능을 위해 LAZY 로딩 사용
    // @OneToMany(mappedBy = "userId", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    // @Builder.Default
    // private List<News> newsItems = new ArrayList<>();
    
    // @OneToMany(mappedBy = "userId", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    // @Builder.Default
    // private List<IndicatorAnalysis> indicatorAnalyses = new ArrayList<>();
    
    // @OneToMany(mappedBy = "userId", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    // @Builder.Default
    // private List<Prediction> predictions = new ArrayList<>();
    
    // **기존 편의 메서드들**
    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
        this.loginCount = (this.loginCount == null ? 0 : this.loginCount) + 1;
    }
    
    public boolean isActive() {
        return UserStatus.ACTIVE.equals(this.status);
    }
    
    public boolean isDeleted() {
        return UserStatus.DELETED.equals(this.status);
    }
    
    public boolean isPremiumUser() {
        return SubscriptionPlan.PREMIUM.equals(this.subscriptionPlan) || 
               SubscriptionPlan.ENTERPRISE.equals(this.subscriptionPlan);
    }
    
    public boolean isSubscriptionExpired() {
        return subscriptionExpiresAt != null && 
               subscriptionExpiresAt.isBefore(LocalDateTime.now());
    }
    
    // **새로 추가된 편의 메서드들**
    public boolean isAccountNonLocked() {
        return !Boolean.TRUE.equals(this.accountLocked);
    }
    
    public void incrementLoginCount() {
        this.loginCount = (this.loginCount == null ? 0 : this.loginCount) + 1;
    }
    
    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts = (this.failedLoginAttempts == null ? 0 : this.failedLoginAttempts) + 1;
        if (this.failedLoginAttempts >= 5) { // 5회 실패 시 계정 잠금
            this.accountLocked = true;
        }
    }
    
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.accountLocked = false;
    }
    
    public Boolean getEmailVerified() {
        return this.emailVerified;
    }
    
    public String getEmailVerificationToken() {
        return this.emailVerificationToken;
    }
    
    public LocalDateTime getPasswordResetExpires() {
        return this.passwordResetExpires;
    }
    
    // Enum 정의
    public enum UserRole {
        USER("사용자"),
        ADMIN("관리자"), 
        MODERATOR("중재자");
        
        private final String description;
        
        UserRole(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum UserStatus {
        ACTIVE("활성"),
        INACTIVE("비활성"), 
        DELETED("삭제됨"), 
        SUSPENDED("정지됨");
        
        private final String description;
        
        UserStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum SubscriptionPlan {
        FREE("무료", 0, 10),
        PREMIUM("프리미엄", 9900, 100),
        ENTERPRISE("기업", 29900, -1); // -1은 무제한
        
        private final String displayName;
        private final int monthlyPrice;
        private final int maxAnalysisPerMonth;
        
        SubscriptionPlan(String displayName, int monthlyPrice, int maxAnalysisPerMonth) {
            this.displayName = displayName;
            this.monthlyPrice = monthlyPrice;
            this.maxAnalysisPerMonth = maxAnalysisPerMonth;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getMonthlyPrice() {
            return monthlyPrice;
        }
        
        public int getMaxAnalysisPerMonth() {
            return maxAnalysisPerMonth;
        }
        
        public boolean isUnlimited() {
            return maxAnalysisPerMonth == -1;
        }
    }
    
    public enum Theme {
        LIGHT("밝은 테마"),
        DARK("어두운 테마");
        
        private final String description;
        
        Theme(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}