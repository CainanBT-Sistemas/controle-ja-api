package com.cainanbt.softwares.controleja.services.vehicles;

import com.cainanbt.softwares.controleja.dtos.responses.VehicleOdometerContextDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.OdometerValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Centraliza a linha do tempo de odômetro formada por transações veiculares.
 */
@Service
@RequiredArgsConstructor
public class VehicleOdometerTimelineService {
    private static final Pageable NEAREST_CANDIDATES = PageRequest.of(0, 2);
    private static final Pageable LATEST_CANDIDATES = PageRequest.of(0, 100);

    private final TransactionRepository transactionRepository;
    private final VehicleService vehicleService;

    /**
     * Valida uma leitura absoluta entre os eventos cronológicos anterior e posterior.
     */
    public void validateReading(
            Vehicle vehicle,
            Long date,
            BigDecimal odometer,
            UUID excludedTransactionId,
            Long createdAt) {
        validateReading(vehicle, date, odometer, excludedTransactionId, createdAt, false);
    }

    /**
     * Valida uma leitura absoluta entre os eventos cronológicos anterior e posterior.
     */
    public void validateReading(
            Vehicle vehicle,
            Long date,
            BigDecimal odometer,
            UUID excludedTransactionId,
            Long createdAt,
            boolean odometerJumpConfirmed) {
        OdometerValidator.validateValue(odometer);
        if (date == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A data da leitura do odômetro é obrigatória.");
        }

        TimelineContext context = resolveContext(
                vehicle,
                date,
                createdAt != null ? createdAt : Long.MAX_VALUE,
                excludedTransactionId
        );
        BigDecimal previous = context.previous()
                .map(OdometerPoint::odometer)
                .orElse(vehicle.getInitialOdometer());

        if (previous != null && odometer.compareTo(previous) <= 0) {
            throw new BadRequestException(
                    ConstsMessages.ERROR_TITLE,
                    "Odômetro deve ser maior que a leitura anterior de " + previous + "."
            );
        }
        context.next().ifPresent(next -> {
            if (odometer.compareTo(next.odometer()) >= 0) {
                throw new BadRequestException(
                        ConstsMessages.ERROR_TITLE,
                        "Odômetro deve ser menor que a próxima leitura de " + next.odometer() + "."
                );
            }
        });
        OdometerValidator.validateJump(previous, odometer, odometerJumpConfirmed);
    }

    /**
     * Retorna as leituras vizinhas usadas pelo front para lançamentos retroativos.
     */
    public VehicleOdometerContextDTO getContext(Vehicle vehicle, Long date, UUID excludedTransactionId) {
        Long createdAt = excludedTransactionId == null
                ? Long.MAX_VALUE
                : transactionRepository.findById(excludedTransactionId)
                .filter(transaction -> transaction.getVehicle() != null)
                .filter(transaction -> transaction.getVehicle().getId().equals(vehicle.getId()))
                .map(Transactions::getCreatedAt)
                .orElseThrow(() -> new BadRequestException(
                        ConstsMessages.ERROR_TITLE,
                        ConstsMessages.TRANSACTION_NOT_FOUND));
        TimelineContext context = resolveContext(vehicle, date, createdAt, excludedTransactionId);
        Optional<OdometerPoint> latest = findLatest(vehicle.getId(), excludedTransactionId);
        Optional<OdometerPoint> previous = context.previous();
        Optional<OdometerPoint> next = context.next();

        return VehicleOdometerContextDTO.builder()
                .previousOdometer(previous.map(OdometerPoint::odometer).orElse(vehicle.getInitialOdometer()))
                .previousDate(previous.map(OdometerPoint::date).orElse(null))
                .previousSource(previous.map(OdometerPoint::source).orElse("INITIAL"))
                .nextOdometer(next.map(OdometerPoint::odometer).orElse(null))
                .nextDate(next.map(OdometerPoint::date).orElse(null))
                .nextSource(next.map(OdometerPoint::source).orElse(null))
                .currentOdometer(latest.map(OdometerPoint::odometer).orElse(vehicle.getInitialOdometer()))
                .latestReadingDate(latest.map(OdometerPoint::date).orElse(null))
                .retroactive(latest.map(point -> date < point.date()).orElse(false))
                .build();
    }

    /**
     * Recalcula o odômetro atual pela leitura de data mais recente, nunca pelo maior valor numérico.
     */
    public void recalculateCurrentOdometer(Vehicle vehicle) {
        BigDecimal current = findLatest(vehicle.getId(), null)
                .map(OdometerPoint::odometer)
                .orElse(vehicle.getInitialOdometer());
        if (current != null) {
            vehicleService.setCurrentOdometer(vehicle, current);
        }
    }

    /**
     * Localiza os eventos imediatamente anterior e posterior à data informada.
     */
    private TimelineContext resolveContext(
            Vehicle vehicle,
            Long date,
            Long createdAt,
            UUID excludedTransactionId) {
        long dayStart = DateUtils.localDateToEpoch(DateUtils.epochToLocalDate(date));
        long dayEnd = DateUtils.localDateToEpoch(DateUtils.epochToLocalDate(date).plusDays(1)) - 1;
        Optional<OdometerPoint> previous = transactionRepository.findOdometerReadingsAtOrBefore(
                        vehicle.getId(), dayStart, dayEnd, createdAt, NEAREST_CANDIDATES).stream()
                .filter(transaction -> !transaction.getId().equals(excludedTransactionId))
                .map(this::fromTransaction)
                .max(pointComparator());

        Optional<OdometerPoint> next = transactionRepository.findOdometerReadingsAfter(
                        vehicle.getId(), dayStart, dayEnd, createdAt, NEAREST_CANDIDATES).stream()
                .filter(transaction -> !transaction.getId().equals(excludedTransactionId))
                .map(this::fromTransaction)
                .min(pointComparator());

        return new TimelineContext(previous, next);
    }

    /**
     * Localiza a leitura cronologicamente mais recente entre todas as fontes.
     */
    private Optional<OdometerPoint> findLatest(
            UUID vehicleId,
            UUID excludedTransactionId) {
        return transactionRepository.findOdometerReadingsByVehicle(vehicleId, LATEST_CANDIDATES).stream()
                .filter(transaction -> !transaction.getId().equals(excludedTransactionId))
                .map(this::fromTransaction)
                .max(pointComparator());
    }

    private Comparator<OdometerPoint> pointComparator() {
        return Comparator.<OdometerPoint, java.time.LocalDate>comparing(
                        point -> DateUtils.epochToLocalDate(point.date()))
                .thenComparing(OdometerPoint::createdAt)
                .thenComparing(OdometerPoint::source);
    }

    private OdometerPoint fromTransaction(Transactions transaction) {
        return new OdometerPoint(
                transaction.getDate(),
                transaction.getCreatedAt() != null ? transaction.getCreatedAt() : 0L,
                transaction.getCurrentOdometer(),
                "TRANSACTION"
        );
    }

    private record TimelineContext(Optional<OdometerPoint> previous, Optional<OdometerPoint> next) {
    }

    private record OdometerPoint(Long date, Long createdAt, BigDecimal odometer, String source) {
    }
}
