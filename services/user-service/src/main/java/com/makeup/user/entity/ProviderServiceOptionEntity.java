package com.makeup.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "provider_service_options", schema = "user_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderServiceOptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_service_id", nullable = false)
    @JsonIgnore
    private ProviderServiceEntity providerService;

    @Column(nullable = false)
    private String optionType; // 'COMPONENT' hoặc 'ADD_ON'

    @Column(nullable = false)
    private String optionName; // e.g. "Đánh Kem Nền", "Che Khuyết Điểm", "Làm Tóc Cô Dâu"

    @Column(nullable = false)
    private BigDecimal price; // e.g. 100000.0

    @Column(nullable = false)
    private Boolean isDefault; // true: mặc định có trong gói / false: add-on chọn thêm

    @Column(nullable = false)
    private Boolean isRemovable; // true: cho phép khách bỏ trừ tiền / false: bắt buộc không cho bỏ
}
