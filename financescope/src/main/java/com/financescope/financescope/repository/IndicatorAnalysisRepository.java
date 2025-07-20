package com.financescope.financescope.repository;

import com.financescope.financescope.entity.IndicatorAnalysis;
import com.financescope.financescope.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IndicatorAnalysisRepository extends JpaRepository<IndicatorAnalysis, Long> {
    
    Page<IndicatorAnalysis> findByUser(User user, Pageable pageable);
    
    @Query("SELECT ia FROM IndicatorAnalysis ia WHERE ia.user = :user AND ia.indicator = :indicator " +
           "ORDER BY ia.analysisDate DESC")
    List<IndicatorAnalysis> findByUserAndIndicator(@Param("user") User user, @Param("indicator") String indicator);
    
    @Query("SELECT ia FROM IndicatorAnalysis ia WHERE ia.user = :user " +
           "AND ia.analysisDate >= :startDate AND ia.analysisDate <= :endDate")
    List<IndicatorAnalysis> findByUserAndDateRange(
            @Param("user") User user,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT DISTINCT ia.indicator FROM IndicatorAnalysis ia WHERE ia.user = :user")
    List<String> findDistinctIndicatorsByUser(@Param("user") User user);
    
    Optional<IndicatorAnalysis> findTopByUserAndIndicatorOrderByAnalysisDateDesc(User user, String indicator);
    
    @Query("SELECT COUNT(ia) FROM IndicatorAnalysis ia WHERE ia.user = :user")
    Long countByUser(@Param("user") User user);
}

