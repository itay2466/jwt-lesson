package com.example.jwtlesson.repository;

import com.example.jwtlesson.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    // Serialize refreshes so a token can be consumed only once, including concurrent requests.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findForUpdate(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId")
    void revokeForUser(@Param("userId") Long userId);
}
