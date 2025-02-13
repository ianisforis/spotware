package org.example.spotware.model;

import java.math.BigDecimal;
import java.time.Instant;

public record TrendBar(BigDecimal open, BigDecimal close, BigDecimal high, BigDecimal low,
                       TrendBarPeriod trendBarPeriod, Instant timestamp) {
}
