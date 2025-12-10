package com.smartsched.smartsched_api.solver.domain;

import com.smartsched.smartsched_api.model.Classroom;
import com.smartsched.smartsched_api.model.Section;
import com.smartsched.smartsched_api.model.Teacher;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class Allocation {

    @PlanningId
    private Long id;

    // Problem Facts
    private String subjectCode;
    private String subjectName;
    private Teacher teacher;
    private Section section;
    private int durationInMinutes;
    private boolean isMajor;
    private boolean isPinned; // --- ADDED isPinned FIELD ---

    // Planning Variables
    @PlanningVariable(valueRangeProviderRefs = "timeslots")
    private Timeslot timeslot;

    @PlanningVariable(valueRangeProviderRefs = "classrooms")
    private Classroom classroom;

    public Allocation() {}

    // Constructor updated to include isPinned
    public Allocation(long id, String subjectCode, String subjectName, Teacher teacher, Section section, int durationInMinutes, boolean isMajor, boolean isPinned) {
        this.id = id;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.teacher = teacher;
        this.section = section;
        this.durationInMinutes = durationInMinutes;
        this.isMajor = isMajor;
        this.isPinned = isPinned; // --- SET isPinned ---
    }

    // Getters & Setters
    public Long getId() { return id; }
    public String getSubjectCode() { return subjectCode; }
    public String getSubjectName() { return subjectName; }
    public Teacher getTeacher() { return teacher; }
    public Section getSection() { return section; }
    public int getDurationInMinutes() { return durationInMinutes; }
    public boolean isMajor() { return isMajor; }
    public boolean isPinned() { return isPinned; } // --- ADDED GETTER ---
    public Timeslot getTimeslot() { return timeslot; }
    public void setTimeslot(Timeslot timeslot) { this.timeslot = timeslot; }
    public Classroom getClassroom() { return classroom; }
    public void setClassroom(Classroom classroom) { this.classroom = classroom; }

    @Override
    public String toString() { return subjectCode + " (" + id + ")"; }
}
