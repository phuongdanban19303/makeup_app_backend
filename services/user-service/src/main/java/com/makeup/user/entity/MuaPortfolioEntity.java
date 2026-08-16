package com.makeup.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "mua_portfolios", schema = "user_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MuaPortfolioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long muaId;

    @Column(nullable = false)
    private String imageUrl;

    private String caption;

    @CreationTimestamp
    private ZonedDateTime createdAt;
}
