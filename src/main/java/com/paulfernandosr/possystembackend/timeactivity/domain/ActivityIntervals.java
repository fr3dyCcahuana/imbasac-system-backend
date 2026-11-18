package com.paulfernandosr.possystembackend.timeactivity.domain;

import com.paulfernandosr.possystembackend.timeactivity.domain.TimeActivity.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public final class ActivityIntervals {
    private ActivityIntervals() {}

    private record Boundary(long time, Segment source, boolean opening) {}

    // A segment with observed interaction takes priority over one without it.
    private static final Comparator<Segment> PRIORITY = Comparator
            .comparing(Segment::lastInteraction, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparingLong(Segment::start)
            .thenComparing(Segment::eventId);

    /** Calculate disjoint slices for a single user's observations. */
    public static List<Slice> calculate(List<Segment> segments, long from, long until) {
        var boundaries = new ArrayList<Boundary>(segments.size() * 3);
        for (Segment s : segments) {
            long start = Math.max(from, s.start());
            long end = Math.min(until, s.end());
            if (start >= end) continue;
            boundaries.add(new Boundary(start, s, true));
            boundaries.add(new Boundary(end, s, false));
            if (s.lastInteraction() != null) {
                long threshold = s.lastInteraction() + s.idleMillis();
                if (threshold > start && threshold < end) boundaries.add(new Boundary(threshold, null, false));
            }
        }
        boundaries.sort(Comparator.comparingLong(Boundary::time));
        var active = new TreeSet<Segment>(PRIORITY);
        var slices = new ArrayList<Slice>();
        long previous = from;
        for (Boundary boundary : boundaries) {
            if (boundary.time() > previous && !active.isEmpty()) {
                Segment selected = active.last();
                State state = selected.lastInteraction() == null ? State.UNCLASSIFIED
                        : previous >= selected.lastInteraction() + selected.idleMillis() ? State.QUIET : State.RECENT;
                slices.add(new Slice(selected, new Interval(selected.module(), previous, boundary.time(), state)));
            }
            previous = boundary.time();
            if (boundary.source() == null) continue;
            if (boundary.opening()) active.add(boundary.source());
            else active.remove(boundary.source());
        }
        return slices;
    }

    public static List<Interval> timeline(List<Slice> slices, String module) {
        var timeline = new ArrayList<Interval>();
        Long previousEnd = null;
        for (Slice slice : slices) {
            Interval next = slice.interval();
            if (module == null && previousEnd != null && previousEnd < next.start()) {
                timeline.add(new Interval("", previousEnd, next.start(), State.GAP));
            }
            previousEnd = next.end();
            if (module != null && !module.equals(next.module())) continue;
            if (!timeline.isEmpty()) {
                Interval previous = timeline.getLast();
                if (previous.end() == next.start() && previous.state() == next.state()
                        && previous.module().equals(next.module())) {
                    timeline.set(timeline.size() - 1,
                            new Interval(next.module(), previous.start(), next.end(), next.state()));
                    continue;
                }
            }
            timeline.add(next);
        }
        return timeline;
    }
}
