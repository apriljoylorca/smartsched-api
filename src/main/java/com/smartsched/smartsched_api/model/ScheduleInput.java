package com.smartsched.smartsched_api.model;

// No MongoDB annotations needed as this is a transient DTO
public class ScheduleInput {

    private String subjectCode;
    private String subjectName;
    private String teacherId;
    private String sectionId;
    private int classHoursPerWeek;
    private boolean isMajor; // Field name matches JSON key "isMajor"

    // Constructors
    public ScheduleInput() {
    }

    public ScheduleInput(String subjectCode, String subjectName, String teacherId, String sectionId, int classHoursPerWeek, boolean isMajor) {
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.teacherId = teacherId;
        this.sectionId = sectionId;
        this.classHoursPerWeek = classHoursPerWeek;
        this.isMajor = isMajor;
    }

    // Getters (Standard names)
    public String getSubjectCode() { return subjectCode; }
    public String getSubjectName() { return subjectName; }
    public String getTeacherId() { return teacherId; }
    public String getSectionId() { return sectionId; }
    public int getClassHoursPerWeek() { return classHoursPerWeek; }
    public boolean isMajor() { return isMajor; } // Standard boolean getter

    // Setters (Standard names)
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }
    public void setClassHoursPerWeek(int classHoursPerWeek) { this.classHoursPerWeek = classHoursPerWeek; }

    // Jackson expects setIsMajor for field "isMajor"
    public void setIsMajor(boolean isMajor) { 
        this.isMajor = isMajor; 
    }


    @Override
    public String toString() {
        return "ScheduleInput{" +
               "subjectCode='" + subjectCode + '\'' +
               ", subjectName='" + subjectName + '\'' +
               ", teacherId='" + teacherId + '\'' +
               ", sectionId='" + sectionId + '\'' +
               ", classHoursPerWeek=" + classHoursPerWeek +
               ", isMajor=" + isMajor +
               '}';
    }
}

