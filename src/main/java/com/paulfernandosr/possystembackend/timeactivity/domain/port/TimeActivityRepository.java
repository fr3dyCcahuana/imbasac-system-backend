package com.paulfernandosr.possystembackend.timeactivity.domain.port;

import com.paulfernandosr.possystembackend.timeactivity.domain.TimeActivity.*;

import java.util.List;
import java.util.Optional;

public interface TimeActivityRepository {
    int MAX_SEGMENTS = 80_000;

    Optional<Actor> actor(String username);
    List<Long> team(long viewer);
    boolean save(Actor actor, Observation observation, int idleMinutes, int policy);
    List<Segment> observations(List<Long> users, long from, long until);
    boolean hasSaleJournal();
    boolean hasProformas();
    List<BusinessResult> results(List<Long> users, long from, long until, boolean sales, boolean proformas);
    Long resultCount(List<Long> users, long from, long until, boolean sales, boolean proformas);
}
