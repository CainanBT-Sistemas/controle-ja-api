package com.cainanbt.softwares.controleja.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public interface DateUtils {
    DateTimeFormatter DATE_FORMATTER  = DateTimeFormatter.ofPattern("d/MM/yyyy");
    DateTimeFormatter DATETIME_FORMATTER  = DateTimeFormatter.ofPattern("d/MM/yyyy HH:mm");

    ZoneId zoneId = ZoneId.of("America/Sao_Paulo");

    static LocalDate stringToLocalDate(String s){
        return LocalDate.parse(s,DATE_FORMATTER);
    }

    static long getEpochNow(){
        return localDateTimeToEpoch(LocalDateTime.now());
    }

    static String localDateToString(LocalDate date){
        return date.format(DATE_FORMATTER);
    }

    static  String localDateTimeToString(LocalDateTime localDateTime){
        return localDateTime.format(DATETIME_FORMATTER);
    }
    static LocalDateTime stringToLocalDateTime(String s){
        return LocalDateTime.parse(s,DATETIME_FORMATTER);
    }

    static long localDateTimeToEpoch(LocalDateTime date){
        ZonedDateTime zdt = date.atZone(zoneId);
        return zdt.toInstant().toEpochMilli();
    }

    static long localDateToEpoch(LocalDate date){
        ZonedDateTime zdt = date.atStartOfDay(zoneId);
        return zdt.toInstant().toEpochMilli();
    }

    static LocalDate epochToLocalDate(long epoch){
        Instant instant =  Instant.ofEpochMilli(epoch);
        return instant.atZone(zoneId).toLocalDate();
    }

    static LocalDateTime epochToLocalDateTime(long epoch){
        Instant instant =  Instant.ofEpochMilli(epoch);
        return instant.atZone(zoneId).toLocalDateTime();
    }
}
