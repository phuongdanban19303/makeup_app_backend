package com.makeup.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "mua_profiles", schema = "user_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MuaProfileEntity {

    @Id
    private Long userId; // Maps 1-1 with UserEntity(id)

    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String identityCardUrl;

    private Boolean isVerified;

    private BigDecimal rating;

    private Integer totalReviews;

    private Integer totalCompletedJobs;

    private String currentStatus; // 'ONLINE', 'OFFLINE', 'BUSY'
}
