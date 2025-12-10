package com.smartsched.smartsched_api.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.smartsched.smartsched_api.model.Schedule;
import com.smartsched.smartsched_api.model.Teacher;
import com.smartsched.smartsched_api.repository.ScheduleRepository;
import com.smartsched.smartsched_api.repository.TeacherRepository;

@Service
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final ScheduleRepository scheduleRepository;

    // Inject ScheduleRepository to update schedules on delete
    public TeacherService(TeacherRepository teacherRepository, ScheduleRepository scheduleRepository) {
        this.teacherRepository = teacherRepository;
        this.scheduleRepository = scheduleRepository;
    }

    public List<Teacher> getAllTeachers() {
        return teacherRepository.findAll();
    }

    public Optional<Teacher> getTeacherById(String id) {
        return teacherRepository.findById(id);
    }

    public Teacher createTeacher(Teacher teacher) {
        // scheduleIds will be null by default, which is correct.
        return teacherRepository.save(teacher);
    }

    // --- FIX: "Unsafe Update" Bug ---
    // This logic is now safe and will not overwrite scheduleIds.
    public Optional<Teacher> updateTeacher(String id, Teacher teacherDetails) {
        return teacherRepository.findById(id).map(existingTeacher -> {
            // Only update fields that can be edited by the user
            existingTeacher.setName(teacherDetails.getName());
            existingTeacher.setDepartment(teacherDetails.getDepartment());
            // We intentionally DO NOT touch existingTeacher.scheduleIds
            return teacherRepository.save(existingTeacher);
        });
    }

    // --- FIX: "Orphaned Schedule" Bug ---
    // This logic now updates schedules before deleting the teacher.
    public boolean deleteTeacher(String id) {
        if (!teacherRepository.existsById(id)) {
            return false;
        }
        
        // Step 1: Find all schedules linked to this teacher
        // This method is defined in the new ScheduleRepository.java
        // The error will disappear once that file is updated.
        List<Schedule> schedules = scheduleRepository.findAllByTeacherId(id);
        for (Schedule schedule : schedules) {
            // This method is defined in Schedule.java (your Canvas file)
            schedule.setTeacherId(null); // Set the link to null
        }
        scheduleRepository.saveAll(schedules); // Save the changes

        // Step 2: Now it's safe to delete the teacher
        teacherRepository.deleteById(id);
        return true;
    }
}
