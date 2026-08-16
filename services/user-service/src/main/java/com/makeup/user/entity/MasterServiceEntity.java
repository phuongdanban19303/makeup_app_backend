package com.makeup.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "master_services", schema = "user_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String categoryName; // e.g. "Makeup", "Làm Tóc", "Nail"

    @Column(nullable = false)
    private String serviceName;  // e.g. "Makeup Cô Dâu", "Makeup Đi Tiệc"

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    private ZonedDateTime createdAt;
}
