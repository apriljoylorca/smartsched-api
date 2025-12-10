package com.smartsched.smartsched_api.model;

import java.util.List;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("teachers")
public class Teacher {
    @Id
    private String id;
    private String name;
    private String department;
    private List<String> scheduleIds;

    // Constructors
    public Teacher() {}

    public Teacher(String name, String department) {
        this.name = name;
        this.department = department;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public List<String> getScheduleIds() { return scheduleIds; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDepartment(String department) { this.department = department; }
    public void setScheduleIds(List<String> scheduleIds) { this.scheduleIds = scheduleIds; }

    // --- STABLE hashCode and equals (Fix for groupBy crash) ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Teacher teacher = (Teacher) o;
        // If id is null for either, they can't be equal unless both are the same instance (checked above)
        if (id == null || teacher.id == null) {
             return false;
        }
        return Objects.equals(id, teacher.id);
    }

    @Override
    public int hashCode() {
        // Use only the id for hashcode calculation
        return Objects.hash(id);
    }
    // --- End of corrected methods ---
}

