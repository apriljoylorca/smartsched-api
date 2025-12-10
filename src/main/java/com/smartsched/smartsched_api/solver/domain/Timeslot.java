package com.smartsched.smartsched_api.solver.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects; // Import Objects

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

public class Timeslot {
    @PlanningId
    private Long id;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;

    // No-arg constructor required
    public Timeslot() {}

    // All-args constructor
    public Timeslot(Long id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters
    public Long getId() { return id; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    @Override
    public String toString() {
        return dayOfWeek + " " + startTime;
    }

    // --- STABLE hashCode/equals (Fix for groupBy crash) ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Timeslot timeslot = (Timeslot) o;
        // If id is null for either, they can't be equal unless both are the same instance
        if (id == null || timeslot.id == null) {
            return false;
        }
        return Objects.equals(id, timeslot.id);
    }

    @Override
    public int hashCode() {
        // Use only the id for hashcode calculation
        return Objects.hash(id);
    }
    // --- End hashCode/equals ---
}

