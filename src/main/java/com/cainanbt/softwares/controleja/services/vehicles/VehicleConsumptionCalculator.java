package com.cainanbt.softwares.controleja.services.vehicles;

import com.cainanbt.softwares.controleja.enums.FuelType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Concentra cálculos de consumo e médias de combustível do veículo.
 */
@Component
public class VehicleConsumptionCalculator {

    /**
     * Calcula KM/L usando a distância percorrida desde o último odômetro e litros abastecidos.
     */
    public Double calculateConsumption(BigDecimal previousOdometer, BigDecimal newOdometer, Double liters) {
        if (previousOdometer == null || newOdometer == null || liters == null || liters <= 0) {
            return null;
        }
        if (newOdometer.compareTo(previousOdometer) <= 0) {
            return null;
        }
        return newOdometer.subtract(previousOdometer).doubleValue() / liters;
    }

    /**
     * Atualiza a média móvel simples para o tipo de combustível informado.
     */
    public Double calculateRollingAverage(Double currentAverage, double newConsumption) {
        if (currentAverage == null || currentAverage == 0) {
            return newConsumption;
        }
        return (currentAverage + newConsumption) / 2;
    }

    /**
     * Indica se o combustível possui média própria no cadastro do veículo.
     */
    public boolean supportsVehicleAverage(FuelType fuelType) {
        return fuelType == FuelType.GASOLINA || fuelType == FuelType.ETANOL;
    }
}
