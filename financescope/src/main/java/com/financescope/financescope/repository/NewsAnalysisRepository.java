package com.financescope.financescope.repository;

import com.financescope.financescope.entity.NewsAnalysis;
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
public interface NewsAnalysisRepository extends JpaRepository<NewsAnalysis, Long> {
    
    Page<NewsAnalysis> findByUser(User user, Pageable pageable);
    
    @Query("SELECT na FROM NewsAnalysis na WHERE na.user = :user AND na.analysisType = :type")
    List<NewsAnalysis> findByUserAndType(@Param("user") User user, @Param("type") String type);
    
    @Query("SELECT na FROM NewsAnalysis na WHERE na.user = :user " +
           "AND na.createdAt >= :startDate AND na.createdAt <= :endDate")
    List<NewsAnalysis> findByUserAndDateRange(
            @Param("user") User user,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(na) FROM NewsAnalysis na WHERE na.user = :user")
    Long countByUser(@Param("user") User user);
}