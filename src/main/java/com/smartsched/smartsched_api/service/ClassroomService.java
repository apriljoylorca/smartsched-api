package com.smartsched.smartsched_api.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.smartsched.smartsched_api.model.Classroom;
import com.smartsched.smartsched_api.model.Schedule;
import com.smartsched.smartsched_api.repository.ClassroomRepository;
import com.smartsched.smartsched_api.repository.ScheduleRepository;

@Service
public class ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final ScheduleRepository scheduleRepository;

    public ClassroomService(ClassroomRepository classroomRepository, ScheduleRepository scheduleRepository) {
        this.classroomRepository = classroomRepository;
        this.scheduleRepository = scheduleRepository;
    }

    public List<Classroom> getAllClassrooms() {
        return classroomRepository.findAll();
    }

    public Optional<Classroom> getClassroomById(String id) {
        return classroomRepository.findById(id);
    }

    public Classroom createClassroom(Classroom classroom) {
        return classroomRepository.save(classroom);
    }

    // --- FIX: "Unsafe Update" Bug ---
    public Optional<Classroom> updateClassroom(String id, Classroom classroomDetails) {
        return classroomRepository.findById(id).map(existingClassroom -> {
            existingClassroom.setName(classroomDetails.getName());
            existingClassroom.setCapacity(classroomDetails.getCapacity());
            existingClassroom.setType(classroomDetails.getType());
            // We DO NOT touch existingClassroom.scheduleIds
            return classroomRepository.save(existingClassroom);
        });
    }

    // --- FIX: "Orphaned Schedule" Bug ---
    public boolean deleteClassroom(String id) {
        if (!classroomRepository.existsById(id)) {
            return false;
        }

        // Step 1: Update linked schedules
        // The error on the line below will be fixed when you update ScheduleRepository.java
        List<Schedule> schedules = scheduleRepository.findAllByClassroomId(id);
        for (Schedule schedule : schedules) {
            schedule.setClassroomId(null); // Set to null
        }
        scheduleRepository.saveAll(schedules);

        // Step 2: Delete classroom
        classroomRepository.deleteById(id);
        return true;
    }
}
