package com.smartsched.smartsched_api.model;

import java.time.DayOfWeek;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("schedules")
public class Schedule {
    @Id
    private String id;
    private String problemId;
    private String subjectCode;
    private String subjectName;
    private String teacherId; 
    private String sectionId; 
    private String classroomId; 
    private DayOfWeek dayOfWeek;
    private String startTime; 
    private String endTime; 

    // No-arg constructor
    public Schedule() {}

    // All-args constructor (must match SchedulingService)
    public Schedule(String problemId, String subjectCode, String subjectName, String teacherId, String sectionId, String classroomId, DayOfWeek dayOfWeek, String startTime, String endTime) {
        this.problemId = problemId;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.teacherId = teacherId;
        this.sectionId = sectionId;
        this.classroomId = classroomId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters
    public String getId() { return id; }
    public String getProblemId() { return problemId; }
    public String getSubjectCode() { return subjectCode; }
    public String getSubjectName() { return subjectName; }
    public String getTeacherId() { return teacherId; }
    public String getSectionId() { return sectionId; }
    public String getClassroomId() { return classroomId; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }

    // --- SETTERS REQUIRED FOR THE DELETE LOGIC ---
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }
    public void setClassroomId(String classroomId) { this.classroomId = classroomId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
}
