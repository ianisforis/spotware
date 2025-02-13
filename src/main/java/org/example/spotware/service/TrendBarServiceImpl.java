package org.example.spotware.service;

import org.example.spotware.model.Quote;
import org.example.spotware.model.TrendBar;
import org.example.spotware.model.TrendBarPeriod;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import static java.time.Instant.now;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.YEARS;
import static java.util.Collections.emptySet;
import static java.util.Comparator.comparing;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.empty;
import static org.example.spotware.helper.TrendBarCalculationHelper.isSamePeriod;
import static org.example.spotware.helper.TrendBarCalculationHelper.recalculate;
import static org.example.spotware.model.TrendBarPeriod.D1;
import static org.example.spotware.model.TrendBarPeriod.H1;
import static org.example.spotware.model.TrendBarPeriod.M1;

@Service
public class TrendBarServiceImpl implements TrendBarService {

    private final Map<String, Map<TrendBarPeriod, Queue<TrendBar>>> trendBarsBySymbolAndPeriod = new ConcurrentHashMap<>();
    private final ExecutorService executor = newSingleThreadExecutor();

    @Override
    public CompletableFuture<Boolean> getQuote(Quote quote) {
        return supplyAsync(() -> processQuote(quote), executor);
    }

    private boolean processQuote(Quote quote) {
        updateTrendBars(quote, M1, YEARS, MONTHS, DAYS, HOURS, MINUTES);
        updateTrendBars(quote, H1, YEARS, MONTHS, DAYS, HOURS);
        updateTrendBars(quote, D1, YEARS, MONTHS, DAYS);
        return true;
    }

    private void updateTrendBars(Quote quote, TrendBarPeriod period, ChronoUnit... units) {
        Map<TrendBarPeriod, Queue<TrendBar>> trendBarsByPeriod = trendBarsBySymbolAndPeriod
                .computeIfAbsent(quote.symbol(), key -> new ConcurrentHashMap<>());
        Queue<TrendBar> trendBars = trendBarsByPeriod
                .computeIfAbsent(period, key -> new ConcurrentLinkedQueue<>());
        BigDecimal price = quote.price();
        Instant quoteTimestamp = quote.timestamp();

        if (trendBars.isEmpty()) {
            TrendBar trendBar = new TrendBar(price, price, price, price, period, quoteTimestamp);
            trendBars.add(trendBar);
        } else {
            Queue<TrendBar> recalculatedTrendBars = new ConcurrentLinkedQueue<>();
            Queue<TrendBar> trendBarsToRemove = new ConcurrentLinkedQueue<>();
            trendBars
                    .stream()
                    .filter(bar -> isSamePeriod(bar.timestamp(), quoteTimestamp, units))
                    .forEach(bar -> {
                        trendBarsToRemove.add(bar);
                        recalculatedTrendBars.add(recalculate(quote, bar));
            });
            trendBars.removeAll(trendBarsToRemove);
            trendBars.addAll(recalculatedTrendBars);
        }
    }

    @Override
    public Set<TrendBar> getTrendBars(String symbol, TrendBarPeriod period, Instant from, Instant to) {
        if (to == null || to.isAfter(now())) {
            to = now();
        }

        if (from != null && from.isAfter(now())) {
            return emptySet();
        }

        Stream<TrendBar> trendBarStream;
        if (symbol != null) {
            Map<TrendBarPeriod, Queue<TrendBar>> trendBarsByPeriod = trendBarsBySymbolAndPeriod.get(symbol);
            if (trendBarsByPeriod == null) {
                return emptySet();
            }
            if (period != null) {
                Queue<TrendBar> bars = trendBarsByPeriod.get(period);
                if (bars != null) {
                    trendBarStream = bars.stream();
                } else {
                    trendBarStream = empty();
                }
            } else {
                trendBarStream = trendBarsByPeriod
                        .values()
                        .stream()
                        .flatMap(Collection::stream);
            }
        } else {
            Stream<Map<TrendBarPeriod, Queue<TrendBar>>> trendBarsByPeriodStream = trendBarsBySymbolAndPeriod
                    .values()
                    .stream();

            if (period != null) {
                trendBarStream = trendBarsByPeriodStream
                        .map(innerMap -> innerMap.get(period))
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream)
                        .sorted(comparing(TrendBar::timestamp));
            } else {
                trendBarStream = trendBarsByPeriodStream
                        .flatMap(map -> map.values().stream())
                        .flatMap(Collection::stream);
            }
        }

        return filterTrendBarsByFromAndTo(period, from, to, trendBarStream)
                .collect(toSet());
    }

    private Stream<TrendBar> filterTrendBarsByFromAndTo(TrendBarPeriod period, Instant from, Instant to,
                                                        Stream<TrendBar> trendBarStream) {
        return trendBarStream.filter(tb -> {
            ChronoUnit unit;
            if (period != null) {
                unit = period.getChronoUnit();
            } else {
                unit = tb.trendBarPeriod().getChronoUnit();
            }

            Instant barStart = tb.timestamp();
            Instant barEnd;
            Instant cutoff;
            if (unit == MONTHS) {
                ZonedDateTime zdtBarStart = barStart.atZone(UTC);
                ZonedDateTime zdtBarEnd = zdtBarStart.withDayOfMonth(1).plusMonths(1);
                barEnd = zdtBarEnd.toInstant();

                ZonedDateTime zdtTo = to.atZone(UTC);
                ZonedDateTime cutoffZdt = zdtTo.withDayOfMonth(1);
                cutoff = cutoffZdt.toInstant();
            } else if (unit == YEARS) {
                ZonedDateTime zdtBarStart = barStart.atZone(UTC);
                ZonedDateTime zdtBarEnd = zdtBarStart.withDayOfYear(1).plusYears(1);
                barEnd = zdtBarEnd.toInstant();

                ZonedDateTime zdtTo = to.atZone(UTC);
                ZonedDateTime cutoffZdt = zdtTo.withDayOfYear(1);
                cutoff = cutoffZdt.toInstant();
            } else {
                barEnd = barStart.plus(1, unit);
                cutoff = to.truncatedTo(unit);
            }

            if (barEnd.isAfter(cutoff)) {
                return false;
            }

            return from == null || !barEnd.isBefore(from);
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
