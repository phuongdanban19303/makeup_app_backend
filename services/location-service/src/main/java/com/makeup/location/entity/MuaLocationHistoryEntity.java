package com.makeup.location.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.ZonedDateTime;

@Entity
@Table(name = "mua_location_history", schema = "location_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MuaLocationHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mua_id", nullable = false)
    private Long muaId;

    @Column(name = "booking_id")
    private String bookingId;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @CreationTimestamp
    @Column(name = "recorded_at", updatable = false)
    private ZonedDateTime recordedAt;
}
