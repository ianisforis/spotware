package org.example.spotware.helper;

import lombok.NoArgsConstructor;
import org.example.spotware.model.Quote;
import org.example.spotware.model.TrendBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.time.temporal.ChronoUnit.YEARS;
import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class TrendBarCalculationHelper {

    public static TrendBar recalculate(Quote quote, TrendBar trendBar) {
        BigDecimal price = quote.price();
        BigDecimal high = trendBar.high().max(price);
        BigDecimal low = trendBar.low().min(price);
        return new TrendBar(trendBar.open(), price, high, low, trendBar.trendBarPeriod(), trendBar.timestamp());
    }

    public static boolean isSamePeriod(Instant baseTimestamp, Instant otherTimestamp, ChronoUnit... units) {
        for (ChronoUnit unit : units) {
            if (unit == YEARS) {
                int year1 = baseTimestamp.atZone(UTC).getYear();
                int year2 = otherTimestamp.atZone(UTC).getYear();
                if (year1 != year2) {
                    return false;
                }
            } else if (unit == MONTHS) {
                ZonedDateTime zdt1 = baseTimestamp.atZone(UTC);
                ZonedDateTime zdt2 = otherTimestamp.atZone(UTC);
                if (zdt1.getYear() != zdt2.getYear() || zdt1.getMonthValue() != zdt2.getMonthValue()) {
                    return false;
                }
            } else if (unit == DAYS) {
                if (!baseTimestamp.truncatedTo(DAYS).equals(otherTimestamp.truncatedTo(DAYS))) {
                    return false;
                }
            } else if (unit == HOURS || unit == MINUTES || unit == SECONDS) {
                if (!baseTimestamp.truncatedTo(unit).equals(otherTimestamp.truncatedTo(unit))) {
                    return false;
                }
            }
        }
        return true;
    }
}
