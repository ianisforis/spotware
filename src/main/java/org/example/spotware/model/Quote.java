package org.example.spotware.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Quote(BigDecimal price, String symbol, Instant timestamp) {
}
