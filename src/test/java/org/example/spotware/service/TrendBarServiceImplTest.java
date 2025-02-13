package org.example.spotware.service;

import org.example.spotware.model.Quote;
import org.example.spotware.model.TrendBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.math.BigDecimal.valueOf;
import static java.time.Instant.parse;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.example.spotware.model.TrendBarPeriod.M1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendBarServiceImplTest {

    private TrendBarServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TrendBarServiceImpl();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private void sendQuote(BigDecimal price, String symbol, Instant timestamp) throws ExecutionException, InterruptedException {
        Quote quote = new Quote(price, symbol, timestamp);
        CompletableFuture<Boolean> future = service.getQuote(quote);
        future.get();
    }

    @Test
    void getTrendBarsReturnsEmptyWhenFromInFuture() {
        Instant now = Instant.now();
        Instant futureFrom = now.plus(1, MINUTES);

        assertTrue(service.getTrendBars("EURUSD", M1, futureFrom, now).isEmpty());
        assertTrue(service.getTrendBars("EURUSD", M1, now, now).isEmpty());
    }

    @Test
    void getTrendBarsReturnsCompletedTrendBarForM1() throws ExecutionException, InterruptedException {
        sendQuote(valueOf(100), "EURUSD", parse("2023-11-10T10:00:00Z"));
        Set<TrendBar> trendBars = service.getTrendBars("EURUSD", M1, null, parse("2023-11-10T10:01:30Z"));

        assertEquals(1, trendBars.size());
        TrendBar bar = trendBars.iterator().next();
        assertEquals(valueOf(100), bar.open());
        assertEquals(valueOf(100), bar.close());
    }

    @Test
    void getTrendBarsDoesNotReturnTrendBarForM1() throws ExecutionException, InterruptedException {
        sendQuote(valueOf(100), "EURUSD", parse("2023-11-10T10:00:00Z"));
        Set<TrendBar> trendBars = service.getTrendBars("EURUSD", M1, null, parse("2023-11-10T10:00:59Z"));

        assertTrue(trendBars.isEmpty());
    }

    @Test
    void recalculationOfTrendBars() throws ExecutionException, InterruptedException {
        Instant baseTimestamp = parse("2023-11-10T10:00:00Z");
        sendQuote(valueOf(100), "EURUSD", baseTimestamp);

        Instant to = baseTimestamp.plus(1, MINUTES).plus(30, SECONDS);
        Set<TrendBar> trendBarsBefore = service.getTrendBars("EURUSD", M1, null, to);
        assertEquals(1, trendBarsBefore.size());

        TrendBar barBefore = trendBarsBefore.iterator().next();
        assertEquals(valueOf(100), barBefore.open());
        assertEquals(valueOf(100), barBefore.close());
        assertEquals(valueOf(100), barBefore.high());
        assertEquals(valueOf(100), barBefore.low());

        sendQuote(valueOf(120), "EURUSD", baseTimestamp.plus(30, SECONDS));
        Set<TrendBar> trendBarsAfter = service.getTrendBars("EURUSD", M1, null, to);

        assertEquals(1, trendBarsAfter.size());
        TrendBar barAfter = trendBarsAfter.iterator().next();
        assertEquals(valueOf(100), barAfter.open());
        assertEquals(valueOf(120), barAfter.close());
        assertEquals(valueOf(120), barAfter.high());
        assertEquals(valueOf(100), barAfter.low());
    }

    @Test
    void aggregationOfMultipleQuotesInSamePeriod() throws ExecutionException, InterruptedException {
        Instant timestamp = parse("2023-11-10T10:00:00Z");
        sendQuote(valueOf(100), "EURUSD", timestamp);
        sendQuote(valueOf(120), "EURUSD", timestamp.plus(20, SECONDS));
        sendQuote(valueOf(90), "EURUSD", timestamp.plus(30, SECONDS));
        Instant to = parse("2023-11-10T10:01:30Z");
        Set<TrendBar> trendBars = service.getTrendBars("EURUSD", M1, null, to);

        assertEquals(1, trendBars.size());
        TrendBar bar = trendBars.iterator().next();

        assertEquals(valueOf(100), bar.open());
        assertEquals(valueOf(90), bar.close());
        assertEquals(valueOf(120), bar.high());
        assertEquals(valueOf(90), bar.low());
    }

    @Test
    void getTrendBarsWithFromFilter() throws ExecutionException, InterruptedException {
        sendQuote(valueOf(100), "EURUSD", parse("2023-11-10T10:00:00Z"));

        Instant from = parse("2023-11-10T10:00:30Z");
        Instant to = parse("2023-11-10T10:01:30Z");
        Set<TrendBar> trendBars = service.getTrendBars("EURUSD", M1, from, to);
        assertEquals(1, trendBars.size());

        from = parse("2023-11-10T10:01:01Z");
        trendBars = service.getTrendBars("EURUSD", M1, from, to);
        assertTrue(trendBars.isEmpty());
    }

    @Test
    void getTrendBarsForAllSymbolsWhenSymbolNull() throws ExecutionException, InterruptedException {
        Instant timestamp = parse("2023-11-10T10:00:00Z");
        sendQuote(valueOf(100), "EURUSD", timestamp);
        sendQuote(valueOf(200), "GBPUSD", timestamp);

        Set<TrendBar> allTrendBarsForM1 = service.getTrendBars(null, M1, null, parse("2023-11-10T10:01:30Z"));
        assertEquals(2, allTrendBarsForM1.size());
    }

    @Test
    void getTrendBarsWhenPeriodNull() throws ExecutionException, InterruptedException {
        Instant timestamp = parse("2023-11-10T10:00:00Z");
        sendQuote(valueOf(100), "EURUSD", timestamp);
        sendQuote(valueOf(150), "EURUSD", timestamp);

        Instant to = parse("2023-11-10T10:01:30Z");
        Set<TrendBar> trendBars = service.getTrendBars("EURUSD", null, null, to);

        assertEquals(1, trendBars.size());
        TrendBar bar = trendBars.iterator().next();
        assertEquals(M1, bar.trendBarPeriod());
    }
}
