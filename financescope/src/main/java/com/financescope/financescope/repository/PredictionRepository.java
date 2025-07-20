package com.financescope.financescope.repository;

import com.financescope.financescope.entity.Prediction;
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
public interface PredictionRepository extends JpaRepository<Prediction, Long> {
    
    Page<Prediction> findByUser(User user, Pageable pageable);
    
    @Query("SELECT p FROM Prediction p WHERE p.user = :user " +
           "AND (:indicator IS NULL OR p.indicator = :indicator) " +
           "AND (:model IS NULL OR p.modelType = :model) " +
           "AND (:startDate IS NULL OR p.predictionDate >= :startDate) " +
           "AND (:endDate IS NULL OR p.predictionDate <= :endDate)")
    Page<Prediction> findPredictionsByFilters(
            @Param("user") User user,
            @Param("indicator") String indicator,
            @Param("model") Prediction.ModelType model,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
    
    @Query("SELECT p FROM Prediction p WHERE p.user = :user AND p.indicator = :indicator " +
           "ORDER BY p.predictionDate DESC")
    List<Prediction> findByUserAndIndicator(@Param("user") User user, @Param("indicator") String indicator);
    
    @Query("SELECT p FROM Prediction p WHERE p.user = :user AND p.status = :status")
    List<Prediction> findByUserAndStatus(@Param("user") User user, @Param("status") Prediction.PredictionStatus status);
    
    @Query("SELECT p FROM Prediction p WHERE p.predictionPeriodEnd < :now AND p.actualValues IS NULL")
    List<Prediction> findPredictionsReadyForValidation(@Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(p) FROM Prediction p WHERE p.user = :user")
    Long countByUser(@Param("user") User user);
    
    @Query("SELECT AVG(p.accuracyScore) FROM Prediction p WHERE p.user = :user AND p.accuracyScore IS NOT NULL")
    Optional<Double> findAverageAccuracyByUser(@Param("user") User user);
    
    @Query("SELECT p.modelType, COUNT(p), AVG(p.accuracyScore) FROM Prediction p " +
           "WHERE p.user = :user AND p.accuracyScore IS NOT NULL GROUP BY p.modelType")
    List<Object[]> findModelPerformanceByUser(@Param("user") User user);
}
