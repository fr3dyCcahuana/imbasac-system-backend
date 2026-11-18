package com.paulfernandosr.possystembackend.timeactivity.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TimeActivity {
    private TimeActivity() {}

    public record Actor(long userId, String username, String role, Instant enrolledSince) {}
    public record Context(boolean enrolled, String contextId, Instant serverTime, int idleMinutes, int policyVersion) {}
    public record Observation(UUID eventId, String module, Long start, Long end, Long lastInteraction) {}
    public record Batch(String contextId, Integer policyVersion, List<Observation> observations) {}
    public record BatchReceipt(int accepted, int repeated) {}

    public record Segment(UUID eventId, long userId, String username, String role, String module,
                          long start, long end, Long lastInteraction, long idleMillis) {}
    public enum State { RECENT, QUIET, UNCLASSIFIED, GAP }
    public record Interval(String module, long start, long end, State state) {}
    public record Slice(Segment source, Interval interval) {}
    public record Row(long userId, String username, String role, String module,
                      long recentMs, long quietMs, long unclassifiedMs) {}
    public record BusinessResult(String kind, String reference, String actor, long time) {}
    public record Operations(List<BusinessResult> results, List<String> coveredKinds, boolean truncated) {}
    public record Summary(List<Row> rows, Long confirmedResults) {}
}
