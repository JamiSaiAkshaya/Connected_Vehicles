package com.project.digitaltwin.freshness;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreshnessCalculatorTest {

    @Test
    void freshInformationScoresNearOne() {
        FreshnessCalculator calc = new FreshnessCalculator(0.2);
        Instant now = Instant.now();
        double score = calc.score(now, now);
        assertEquals(1.0, score, 1e-9);
    }

    @Test
    void scoreDecaysAsAgeIncreases() {
        FreshnessCalculator calc = new FreshnessCalculator(0.2);
        Instant now = Instant.now();
        double scoreAt1s = calc.score(now.minusSeconds(1), now);
        double scoreAt10s = calc.score(now.minusSeconds(10), now);
        assertTrue(scoreAt1s > scoreAt10s, "Older information must score lower freshness");
    }

    @Test
    void scoreNeverGoesBelowZero() {
        FreshnessCalculator calc = new FreshnessCalculator(0.2);
        Instant now = Instant.now();
        double score = calc.score(now.minusSeconds(10_000), now);
        assertTrue(score >= 0.0 && score <= 1.0);
    }

    @Test
    void futureTimestampTreatedAsZeroAge() {
        FreshnessCalculator calc = new FreshnessCalculator(0.2);
        Instant now = Instant.now();
        double score = calc.score(now.plusSeconds(5), now);
        assertEquals(1.0, score, 1e-9);
    }
}
