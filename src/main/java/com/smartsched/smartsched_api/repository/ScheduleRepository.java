package com.smartsched.smartsched_api.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.smartsched.smartsched_api.model.Schedule;

public interface ScheduleRepository extends MongoRepository<Schedule, String> {

    // Method to find all schedules for a specific problem ID
    List<Schedule> findAllByProblemId(String problemId);

    // --- THIS METHOD MUST EXIST ---
    // Method to delete all schedules associated with a specific problem ID
    // Returns the count of deleted documents
    long deleteByProblemId(String problemId);

    // --- Methods Required by Services for Deleting Teacher/Classroom/Section ---
    List<Schedule> findAllByTeacherId(String teacherId);
    List<Schedule> findAllByClassroomId(String classroomId);
    List<Schedule> findAllBySectionId(String sectionId);
}
