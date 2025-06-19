package com.team03.ticketmon.auth.repository;

import com.team03.ticketmon.auth.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Modifying
    @Query("delete from RefreshToken r where r.userEntity.id = :userId")
    void deleteByUserEntityId(@Param("userId") Long userId);

    @Query("select COUNT(r) > 0 from RefreshToken r where r.userEntity.id = :userId and r.token = :token")
    boolean existsByUserEntityIdAndToken(@Param("userId") Long userId, @Param("token") String token);
}
