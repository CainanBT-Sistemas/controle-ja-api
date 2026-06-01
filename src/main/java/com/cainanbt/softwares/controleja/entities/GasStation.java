package com.cainanbt.softwares.controleja.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
import org.hibernate.annotations.Where;

import java.util.UUID;

@Entity
@Table(name = "gas_stations", indexes = {
        @Index(name = "idx_gas_stations_user_deleted", columnList = "user_id, deletedAt"),
        @Index(name = "idx_gas_stations_user_name_deleted", columnList = "user_id, name, deletedAt")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Where(clause = "deleted_at IS NULL")
public class GasStation {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String address;
    private String city;
    private String state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(nullable = false)
    private Long createdAt;
    private Long updatedAt;
    private Long deletedAt;
}
