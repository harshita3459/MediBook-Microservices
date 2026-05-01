package com.medibook.auth.repository;

import com.medibook.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByRole(User.Role role);

    Optional<User> findByPhone(String phone);

    List<User> findByFullNameContainingIgnoreCase(String fullName);

    Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId);

    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.role = :role")
    List<User> findActiveUsersByRole(@Param("role") User.Role role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    long countByRole(@Param("role") User.Role role);
}