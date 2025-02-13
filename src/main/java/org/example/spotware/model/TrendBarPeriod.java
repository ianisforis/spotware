package org.example.spotware.model;

import java.time.temporal.ChronoUnit;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;

public enum TrendBarPeriod {
    M1(MINUTES),
    H1(HOURS),
    D1(DAYS);

    private final ChronoUnit chronoUnit;

    TrendBarPeriod(ChronoUnit chronoUnit) {
        this.chronoUnit = chronoUnit;
    }

    public ChronoUnit getChronoUnit() {
        return this.chronoUnit;
    }
}
