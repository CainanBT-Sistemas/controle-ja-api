package com.cainanbt.softwares.controleja.utils;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateUtilsTest {

    @Test
    void localDateToEpochKeepsSaoPauloCivilDate() {
        long epoch = DateUtils.localDateToEpoch(LocalDate.of(2026, 7, 9));

        assertEquals("2026-07-09T03:00:00Z", Instant.ofEpochMilli(epoch).toString());
        assertEquals(LocalDate.of(2026, 7, 9), DateUtils.epochToLocalDate(epoch));
    }

    @Test
    void epochAtUtcMidnightBelongsToPreviousSaoPauloCivilDate() {
        long utcMidnight = Instant.parse("2026-07-09T00:00:00Z").toEpochMilli();

        assertEquals(LocalDate.of(2026, 7, 8), DateUtils.epochToLocalDate(utcMidnight));
    }
}
