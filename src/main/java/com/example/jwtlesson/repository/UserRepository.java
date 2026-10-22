package com.example.jwtlesson.repository;

import com.example.jwtlesson.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data builds the query from the method name:
    // SELECT ... FROM users WHERE username = ?
    // @EntityGraph loads the roles in the same query (JOIN) - needed because
    // the JWT filter calls this outside a transaction.
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    // Override findAll so the admin list loads roles in one query (no N+1)
    @Override
    @EntityGraph(attributePaths = "roles")
    List<User> findAll();
}
