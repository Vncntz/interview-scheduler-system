package com.company.iss.auth.repository;

import com.company.iss.auth.entity.PasswordResetRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    @Query("select request.targetUser.id from PasswordResetRequest request where request.publicRequestId = :publicRequestId")
    Optional<Long> findTargetUserIdByPublicRequestId(@Param("publicRequestId") String publicRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PasswordResetRequest request where request.publicRequestId = :publicRequestId")
    Optional<PasswordResetRequest> findByPublicRequestIdForUpdate(@Param("publicRequestId") String publicRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PasswordResetRequest request where request.targetUser.id = :userId and request.consumedAt is null and request.invalidatedAt is null")
    List<PasswordResetRequest> findActiveByTargetUserIdForUpdate(@Param("userId") Long userId);
}
