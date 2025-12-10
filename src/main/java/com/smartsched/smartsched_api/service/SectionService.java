package com.smartsched.smartsched_api.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.smartsched.smartsched_api.model.Schedule;
import com.smartsched.smartsched_api.model.Section;
import com.smartsched.smartsched_api.repository.ScheduleRepository;
import com.smartsched.smartsched_api.repository.SectionRepository;

@Service
public class SectionService {

    private final SectionRepository sectionRepository;
    private final ScheduleRepository scheduleRepository;

    public SectionService(SectionRepository sectionRepository, ScheduleRepository scheduleRepository) {
        this.sectionRepository = sectionRepository;
        this.scheduleRepository = scheduleRepository;
    }

    public List<Section> getAllSections() {
        return sectionRepository.findAll();
    }

    public Optional<Section> getSectionById(String id) {
        return sectionRepository.findById(id);
    }

    public Section createSection(Section section) {
        return sectionRepository.save(section);
    }

    // --- FIX: "Unsafe Update" Bug ---
    public Optional<Section> updateSection(String id, Section sectionDetails) {
        return sectionRepository.findById(id).map(existingSection -> {
            existingSection.setProgram(sectionDetails.getProgram());
            existingSection.setYearLevel(sectionDetails.getYearLevel());
            existingSection.setSectionName(sectionDetails.getSectionName());
            existingSection.setNumberOfStudents(sectionDetails.getNumberOfStudents());
            // We DO NOT touch existingSection.scheduleIds
            return sectionRepository.save(existingSection);
        });
    }

    // --- FIX: "Orphaned Schedule" Bug ---
    public boolean deleteSection(String id) {
        if (!sectionRepository.existsById(id)) {
            return false;
        }

        // Step 1: Update linked schedules
        // The error on the line below will be fixed when you update ScheduleRepository.java
        List<Schedule> schedules = scheduleRepository.findAllBySectionId(id);
        for (Schedule schedule : schedules) {
            schedule.setSectionId(null); // Set to null
        }
        scheduleRepository.saveAll(schedules);

        // Step 2: Delete section
        sectionRepository.deleteById(id);
        return true;
    }
}
