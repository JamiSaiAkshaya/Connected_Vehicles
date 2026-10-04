package com.project.digitaltwin.freshness;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Computes an Age-of-Information-based freshness score.
 *
 * DESIGN DECISION: F = exp(-lambda * AoI_seconds).
 * This is the candidate formulation from the project brief, chosen because it is
 * monotonically decreasing, bounded in (0, 1], and has a single interpretable
 * parameter (lambda = decay rate). It is NOT assumed to be optimal — Phase 2+
 * experiments (docs/research) should evaluate alternative decay shapes
 * (e.g. linear cutoff, sigmoid) against this baseline.
 *
 * LIMITATION: exponential decay never reaches exactly zero, so a separate
 * hard staleness threshold (see application.yml) is still needed for binary
 * "is this stale" decisions.
 */
@Component
public class FreshnessCalculator {

    private final double lambda;

    public FreshnessCalculator(@Value("${digitaltwin.freshness.lambda}") double lambda) {
        this.lambda = lambda;
    }

    public double score(Instant messageTimestamp, Instant now) {
        long ageSeconds = Math.max(0, Duration.between(messageTimestamp, now).toSeconds());
        return Math.exp(-lambda * ageSeconds);
    }

    public double lambda() {
        return lambda;
    }
}
