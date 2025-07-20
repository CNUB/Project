package com.financescope.financescope.service;

import com.financescope.financescope.dto.user.UserDTO;
import com.financescope.financescope.entity.User;
import com.financescope.financescope.entity.UserSettings;
import com.financescope.financescope.exception.BusinessException;
import com.financescope.financescope.repository.UserRepository;
import com.financescope.financescope.repository.UserSettingsRepository;
import com.financescope.financescope.repository.AnalysisHistoryRepository;
import com.financescope.financescope.repository.PredictionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final PredictionRepository predictionRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UserDTO.UserProfileResponse getUserProfile(String userEmail) {
        User user = findUserByEmail(userEmail);
        return convertToUserProfileResponse(user);
    }

    public UserDTO.UserProfileResponse updateUserProfile(UserDTO.UpdateProfileRequest request, String userEmail) {
        log.info("사용자 프로필 업데이트 - 사용자: {}", userEmail);

        User user = findUserByEmail(userEmail);
        
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getOrganization() != null) {
            user.setOrganization(request.getOrganization());
        }
        if (request.getJobTitle() != null) {
            user.setJobTitle(request.getJobTitle());
        }
        
        user = userRepository.save(user);
        
        log.info("사용자 프로필 업데이트 완료 - 사용자: {}", userEmail);
        return convertToUserProfileResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserDTO.AnalysisHistoryResponse> getAnalysisHistory(String userEmail) {
        User user = findUserByEmail(userEmail);
        
        return analysisHistoryRepository.findByUser(user, org.springframework.data.domain.Pageable.unpaged())
                .getContent()
                .stream()
                .map(this::convertToAnalysisHistoryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDTO.SavedSettingsResponse> getSavedSettings(String userEmail) {
        User user = findUserByEmail(userEmail);
        
        return userSettingsRepository.findByUser(user)
                .stream()
                .map(this::convertToSavedSettingsResponse)
                .collect(Collectors.toList());
    }

    public UserDTO.SavedSettingsResponse saveUserSettings(Map<String, Object> settingsData, String userEmail) {
        log.info("사용자 설정 저장 - 사용자: {}", userEmail);

        User user = findUserByEmail(userEmail);
        
        try {
            String name = (String) settingsData.get("name");
            Boolean isDefault = (Boolean) settingsData.getOrDefault("isDefault", false);
            
            // 기본 설정으로 설정하는 경우 기존 기본 설정 해제
            if (isDefault) {
                userSettingsRepository.findByUserAndIsDefaultTrue(user)
                        .ifPresent(existing -> {
                            existing.setIsDefault(false);
                            userSettingsRepository.save(existing);
                        });
            }
            
            UserSettings settings = UserSettings.builder()
                    .user(user)
                    .name(name)
                    .newsConfig(objectMapper.writeValueAsString(settingsData.get("newsConfig")))
                    .indicatorConfig(objectMapper.writeValueAsString(settingsData.get("indicatorConfig")))
                    .predictionConfig(objectMapper.writeValueAsString(settingsData.get("predictionConfig")))
                    .isDefault(isDefault)
                    .build();
            
            settings = userSettingsRepository.save(settings);
            
            log.info("사용자 설정 저장 완료 - ID: {}", settings.getId());
            return convertToSavedSettingsResponse(settings);

        } catch (Exception e) {
            log.error("사용자 설정 저장 실패: {}", e.getMessage());
            throw new BusinessException("설정 저장에 실패했습니다: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public UserDTO.ModelPerformanceStatsResponse getModelPerformanceStats(String userEmail) {
        User user = findUserByEmail(userEmail);
        
        // 전체 예측 수
        Long totalPredictions = predictionRepository.countByUser(user);
        
        // 평균 정확도
        Double averageAccuracy = predictionRepository.findAverageAccuracyByUser(user).orElse(0.0);
        
        // 모델별 성능 통계
        List<Object[]> modelPerformance = predictionRepository.findModelPerformanceByUser(user);
        Map<String, UserDTO.ModelStats> modelStats = new HashMap<>();
        
        for (Object[] row : modelPerformance) {
            String modelType = row[0].toString();
            Long count = (Long) row[1];
            Double accuracy = (Double) row[2];
            
            UserDTO.ModelStats stats = UserDTO.ModelStats.builder()
                    .count(count.intValue())
                    .accuracy(accuracy != null ? accuracy : 0.0)
                    .indicators(new HashMap<>()) // 지표별 성능은 추가 구현 필요
                    .build();
            
            modelStats.put(modelType.toLowerCase(), stats);
        }
        
        // 최근 예측들
        List<UserDTO.RecentPrediction> recentPredictions = predictionRepository
                .findByUser(user, org.springframework.data.domain.Pageable.ofSize(10))
                .getContent()
                .stream()
                .map(prediction -> UserDTO.RecentPrediction.builder()
                        .id(prediction.getId())
                        .date(prediction.getPredictionDate())
                        .indicator(prediction.getIndicator())
                        .model(prediction.getModelType().name().toLowerCase())
                        .accuracy(prediction.getAccuracyScore())
                        .build())
                .collect(Collectors.toList());
        
        return UserDTO.ModelPerformanceStatsResponse.builder()
                .predictionsTotal(totalPredictions.intValue())
                .averageAccuracy(averageAccuracy)
                .models(modelStats)
                .recentPredictions(recentPredictions)
                .build();
    }

    public void deleteUserSettings(Long settingsId, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        UserSettings settings = userSettingsRepository.findById(settingsId)
                .orElseThrow(() -> new BusinessException("설정을 찾을 수 없습니다."));
        
        if (!settings.getUser().equals(user)) {
            throw new BusinessException("삭제 권한이 없습니다.");
        }
        
        userSettingsRepository.delete(settings);
        log.info("사용자 설정 삭제 완료 - ID: {}", settingsId);
    }

    public void deleteUser(String userEmail, String password, String reason) {
        log.info("사용자 탈퇴 처리 - 사용자: {}", userEmail);

        User user = findUserByEmail(userEmail);
        
        // 비밀번호 확인
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("비밀번호가 올바르지 않습니다.");
        }
        
        // 사용자 상태를 DELETED로 변경 (실제 삭제 대신)
        user.setStatus(User.UserStatus.DELETED);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        
        // 탈퇴 사유 로깅
        if (reason != null && !reason.trim().isEmpty()) {
            log.info("탈퇴 사유 - 사용자: {}, 사유: {}", userEmail, reason);
        }
        
        log.info("사용자 탈퇴 처리 완료 - 사용자: {}", userEmail);
    }

    // Private helper methods
    
    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
    }

    private UserDTO.UserProfileResponse convertToUserProfileResponse(User user) {
    UserDTO.UserPreferences preferences = UserDTO.UserPreferences.builder()
            .defaultIndicators(parseJsonToList(user.getDefaultIndicators()))
            .defaultModel(user.getDefaultModel())
            .emailNotifications(user.getEmailNotifications())
            .theme(user.getTheme() != null ? user.getTheme().name() : "LIGHT") // ✅ enum → String
            .build();

    return UserDTO.UserProfileResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .name(user.getName())
            .organization(user.getOrganization())
            .jobTitle(user.getJobTitle())
            .profileImage(user.getProfileImage())
            .role(user.getRole().name())
            .status(user.getStatus().name())
            .subscriptionPlan(user.getSubscriptionPlan().name())
            .subscriptionExpiresAt(user.getSubscriptionExpiresAt())
            .createdAt(user.getCreatedAt())
            .lastLogin(user.getLastLogin())
            .loginCount(user.getLoginCount() != null ? user.getLoginCount().longValue() : 0L) // ✅ Integer → Long
            .preferences(preferences)
            .build();
}

    private UserDTO.AnalysisHistoryResponse convertToAnalysisHistoryResponse(
            com.financescope.financescope.entity.AnalysisHistory history) {
        try {
            List<String> indicators = objectMapper.readValue(history.getIndicators(), List.class);
            
            return UserDTO.AnalysisHistoryResponse.builder()
                    .id(history.getId())
                    .name(history.getName())
                    .date(history.getCreatedAt())
                    .indicators(indicators)
                    .newsCount(history.getNewsCount())
                    .accuracy(history.getAccuracyScore())
                    .analysisType(history.getAnalysisType().name())
                    .build();
        } catch (Exception e) {
            log.warn("분석 히스토리 변환 실패: {}", e.getMessage());
            return UserDTO.AnalysisHistoryResponse.builder()
                    .id(history.getId())
                    .name(history.getName())
                    .date(history.getCreatedAt())
                    .indicators(Collections.emptyList())
                    .newsCount(history.getNewsCount())
                    .accuracy(history.getAccuracyScore())
                    .analysisType(history.getAnalysisType().name())
                    .build();
        }
    }

    private UserDTO.SavedSettingsResponse convertToSavedSettingsResponse(UserSettings settings) {
        try {
            UserDTO.NewsConfig newsConfig = objectMapper.readValue(settings.getNewsConfig(), UserDTO.NewsConfig.class);
            UserDTO.IndicatorConfig indicatorConfig = objectMapper.readValue(settings.getIndicatorConfig(), UserDTO.IndicatorConfig.class);
            UserDTO.PredictionConfig predictionConfig = objectMapper.readValue(settings.getPredictionConfig(), UserDTO.PredictionConfig.class);
            
            return UserDTO.SavedSettingsResponse.builder()
                    .id(settings.getId())
                    .name(settings.getName())
                    .created(settings.getCreatedAt())
                    .isDefault(settings.getIsDefault())
                    .newsConfig(newsConfig)
                    .indicatorConfig(indicatorConfig)
                    .predictionConfig(predictionConfig)
                    .build();
        } catch (Exception e) {
            log.warn("사용자 설정 변환 실패: {}", e.getMessage());
            return UserDTO.SavedSettingsResponse.builder()
                    .id(settings.getId())
                    .name(settings.getName())
                    .created(settings.getCreatedAt())
                    .isDefault(settings.getIsDefault())
                    .build();
        }
    }

    private List<String> parseJsonToList(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(jsonString, List.class);
        } catch (Exception e) {
            log.warn("JSON 파싱 실패: {}", jsonString);
            return new ArrayList<>();
        }
    }
}