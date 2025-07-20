package com.financescope.financescope.repository;

import com.financescope.financescope.entity.UserSettings;
import com.financescope.financescope.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
    
    List<UserSettings> findByUser(User user);
    
    Optional<UserSettings> findByUserAndIsDefaultTrue(User user);
    
    @Query("SELECT us FROM UserSettings us WHERE us.user = :user AND us.name = :name")
    Optional<UserSettings> findByUserAndName(@Param("user") User user, @Param("name") String name);
    
    @Query("SELECT COUNT(us) FROM UserSettings us WHERE us.user = :user")
    Long countByUser(@Param("user") User user);
}