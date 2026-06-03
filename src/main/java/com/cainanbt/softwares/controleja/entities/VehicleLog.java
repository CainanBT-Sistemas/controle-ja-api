package com.cainanbt.softwares.controleja.entities;

import com.cainanbt.softwares.controleja.enums.DrivingPredominance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "vehicle_logs", indexes = {
        @Index(name = "idx_vehicle_logs_vehicle_date", columnList = "vehicle_id, date"),
        @Index(name = "idx_vehicle_logs_user_date", columnList = "user_id, date")
})
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class VehicleLog {
    @Id
    private UUID id;

    @Column(nullable = false)
    private Long date;

    @Column(nullable = false)
    private BigDecimal odometerReading;

    private Double dashboardKml;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private DrivingPredominance drivingPredominance;

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(nullable = false)
    private Long createdAt;
}
