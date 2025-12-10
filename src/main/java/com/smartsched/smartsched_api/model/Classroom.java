package com.smartsched.smartsched_api.model;

import java.util.List;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("classrooms")
public class Classroom {
    @Id
    private String id;
    private String name;
    private int capacity;
    private String type;
    private List<String> scheduleIds;

    // Constructors
    public Classroom() {}

    public Classroom(String name, int capacity, String type) {
        this.name = name;
        this.capacity = capacity;
        this.type = type;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public String getType() { return type; }
    public List<String> getScheduleIds() { return scheduleIds; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void setType(String type) { this.type = type; }
    public void setScheduleIds(List<String> scheduleIds) { this.scheduleIds = scheduleIds; }

     // --- STABLE hashCode and equals (Fix for groupBy crash) ---
     @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Classroom classroom = (Classroom) o;
         if (id == null || classroom.id == null) {
             return false;
         }
        return Objects.equals(id, classroom.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    // --- End ---
}

