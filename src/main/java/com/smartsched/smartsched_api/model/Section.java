package com.smartsched.smartsched_api.model;

import java.util.List;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("sections")
public class Section {
    @Id
    private String id;
    private String program;
    private int yearLevel;
    private String sectionName;
    private int numberOfStudents;
    private List<String> scheduleIds;

    // Constructors
    public Section() {}

    public Section(String program, int yearLevel, String sectionName, int numberOfStudents) {
        this.program = program;
        this.yearLevel = yearLevel;
        this.sectionName = sectionName;
        this.numberOfStudents = numberOfStudents;
    }

    // Getters
    public String getId() { return id; }
    public String getProgram() { return program; }
    public int getYearLevel() { return yearLevel; }
    public String getSectionName() { return sectionName; }
    public int getNumberOfStudents() { return numberOfStudents; }
    public List<String> getScheduleIds() { return scheduleIds; }

    // Setters
     public void setId(String id) { this.id = id; }
    public void setProgram(String program) { this.program = program; }
    public void setYearLevel(int yearLevel) { this.yearLevel = yearLevel; }
    public void setSectionName(String sectionName) { this.sectionName = sectionName; }
    public void setNumberOfStudents(int numberOfStudents) { this.numberOfStudents = numberOfStudents; }
    public void setScheduleIds(List<String> scheduleIds) { this.scheduleIds = scheduleIds; }

    // --- STABLE hashCode and equals (Fix for groupBy crash) ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Section section = (Section) o;
         if (id == null || section.id == null) {
             return false;
         }
        return Objects.equals(id, section.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    // --- End ---
}

