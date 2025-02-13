package org.example.spotware.service;

import org.example.spotware.model.Quote;
import org.example.spotware.model.TrendBar;
import org.example.spotware.model.TrendBarPeriod;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

interface TrendBarService {

    CompletableFuture<Boolean> getQuote(Quote quote);

    Set<TrendBar> getTrendBars(String symbol, TrendBarPeriod period, Instant from, Instant to);

}
