package com.example.jwtlesson.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users") // "user" is a reserved word in PostgreSQL - CREATE TABLE user fails
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // NOT @Data - see the lesson
@ToString(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    @EqualsAndHashCode.Include   // natural key: the username is unique
    @ToString.Include
    private String username;

    // BCrypt hash (60 chars), never the plain password. Never sent to the client.
    @Column(nullable = false, length = 100)
    @JsonIgnore
    private String password;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    // Owning side of the many-to-many: Hibernate writes the join table from here.
    // LAZY (the default) - the repository loads roles explicitly with @EntityGraph.
    @ManyToMany
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
