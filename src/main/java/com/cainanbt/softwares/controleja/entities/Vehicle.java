package com.cainanbt.softwares.controleja.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamicUpdate
@Setter
@ToString
public class Vehicle {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String brand;
    @Column(nullable = false)
    private String model;
    @Column(nullable = false)
    private Integer year;
    private String plate;
    @Column(nullable = false)
    private BigDecimal currentOdometer;
    @Column(nullable = false)
    private Long createdAt;
    @Column
    private Double avgKmPerLiterGasoline;
    @Column
    private Double avgKmPerLiterEthanol;
    @Column
    private Long updatedAt;
    @Column
    private Long deletedAt;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;
}
