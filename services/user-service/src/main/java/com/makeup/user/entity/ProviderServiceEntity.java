package com.makeup.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "provider_services", schema = "user_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long providerId; // muaId của Thợ A

    private Long masterServiceId; // Nối với master_services.id

    @Column(nullable = false)
    private String category; // e.g. "Makeup"

    @Column(nullable = false)
    private String serviceName; // e.g. "Gói Makeup Cô Dâu Tiệc Đêm"

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal basePrice; // Tiền công/dịch vụ cốt lõi không thể bỏ (VD: 200.000đ)

    @Column(nullable = false)
    private Integer estimatedDurationMinutes;

    @Column(columnDefinition = "TEXT")
    private String attributesJson; // e.g. {"brand_used": "MAC, Dior", "style": "Tự nhiên"}

    private Boolean isActive;

    @CreationTimestamp
    private ZonedDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "providerService", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ProviderServiceOptionEntity> options = new ArrayList<>();
}
