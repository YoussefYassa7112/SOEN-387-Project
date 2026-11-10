package com.squared.locus.repository;

import com.squared.locus.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository
        extends JpaRepository<User, UUID> {
    
    // Finds a user in the database by their Google subject (sub) identifier.
    Optional<User> findByGoogleSub(String googleSub);
}

