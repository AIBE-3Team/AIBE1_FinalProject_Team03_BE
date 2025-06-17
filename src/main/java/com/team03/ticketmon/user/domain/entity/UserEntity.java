package com.team03.ticketmon.user.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.team03.ticketmon.auth.domain.entity.RefreshToken;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String username;

    @JsonIgnore // API 응답(JSON)에 password 포함되지 않음
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false)
    private String address;

    private String profileImage; // 외부 저장소 URL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ApprovalStatus approvalStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus accountStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    @OneToOne(mappedBy = "userEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private RefreshToken refreshToken;

    // Enum 정의
    public enum Role {
        USER,
        SELLER,
        ADMIN
    }

    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        WITHDRAWN,
        REVOKED
    }

    public enum AccountStatus {
        ACTIVE,
        INACTIVE,
        DELETE
    }

}
