package com.financescope.financescope.repository;

import com.financescope.financescope.entity.AnalysisHistory;
import com.financescope.financescope.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, Long> {
    
    Page<AnalysisHistory> findByUser(User user, Pageable pageable);
    
    @Query("SELECT ah FROM AnalysisHistory ah WHERE ah.user = :user AND ah.analysisType = :type")
    List<AnalysisHistory> findByUserAndType(@Param("user") User user, @Param("type") AnalysisHistory.AnalysisType type);
    
    @Query("SELECT ah FROM AnalysisHistory ah WHERE ah.user = :user " +
           "AND ah.createdAt >= :startDate AND ah.createdAt <= :endDate")
    List<AnalysisHistory> findByUserAndDateRange(
            @Param("user") User user,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(ah) FROM AnalysisHistory ah WHERE ah.user = :user")
    Long countByUser(@Param("user") User user);
    
    @Query("SELECT ah FROM AnalysisHistory ah WHERE ah.user = :user ORDER BY ah.createdAt DESC")
    List<AnalysisHistory> findRecentAnalysesByUser(@Param("user") User user, Pageable pageable);
}

