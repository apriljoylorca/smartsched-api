package com.smartsched.smartsched_api.solver;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.smartsched.smartsched_api.model.Classroom;
import com.smartsched.smartsched_api.solver.domain.Allocation;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

public class ScheduleConstraintProvider implements ConstraintProvider {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleConstraintProvider.class);
    private static final LocalTime MAX_END_TIME = LocalTime.of(20, 30);
    private static final LocalTime MIN_TIME = LocalTime.MIN;
    private static final String DEBUG_LOG_PATH = "c:\\Users\\April Joy Lorca\\Documents\\SmartScheduler\\smartsched_app\\.cursor\\debug.log";
    
    // #region agent log
    private void logDebug(String hypothesisId, String location, String message, Map<String, Object> data) {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"").append(hypothesisId).append("\"");
            json.append(",\"location\":\"").append(location.replace("\"", "\\\"")).append("\"");
            json.append(",\"message\":\"").append(message.replace("\"", "\\\"")).append("\"");
            json.append(",\"timestamp\":").append(System.currentTimeMillis());
            if (data != null && !data.isEmpty()) {
                json.append(",\"data\":{");
                boolean first = true;
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (!first) json.append(",");
                    json.append("\"").append(entry.getKey()).append("\":");
                    Object value = entry.getValue();
                    if (value == null) {
                        json.append("null");
                    } else if (value instanceof String) {
                        json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                    } else if (value instanceof Boolean || value instanceof Number) {
                        json.append(value);
                    } else {
                        json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                    }
                    first = false;
                }
                json.append("}");
            } else {
                json.append(",\"data\":{}");
            }
            json.append("}\n");
            try (FileWriter fw = new FileWriter(DEBUG_LOG_PATH, true)) {
                fw.write(json.toString());
            }
        } catch (IOException e) {
            // Ignore logging errors
        }
    }
    // #endregion

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                teacherConflict(constraintFactory),
                classroomConflict(constraintFactory),
                sectionConflict(constraintFactory),
                exactTimeConflict(constraintFactory),
                sameSubjectSameTimeConflict(constraintFactory),
                bsitLabConstraint(constraintFactory),
                generalLabConstraint(constraintFactory),
                nonMajorLectureRoomConstraint(constraintFactory),
                scheduleEndTimeLimit(constraintFactory),
                // --- OPTIMIZED SCHEDULING CONSTRAINTS ---
                nonMajorSubjectsSameTimeDifferentDays(constraintFactory),
                forceNonMajorSameTimeDifferentDays(constraintFactory),
                rewardNonMajorSameTimeDifferentDays(constraintFactory),
                forceMajorSameDay(constraintFactory),
                forceMajorSequential(constraintFactory),
                rewardMajorSequentialSameDay(constraintFactory),
                preferConsistentTimeForNonMajor(constraintFactory),
                preferConsistentTimeForMajor(constraintFactory),
                avoidLateEveningClasses(constraintFactory),
                optimizeClassroomUtilization(constraintFactory),
                // --- NEW CONSTRAINTS FOR SCHEDULING RULES ---
                maxTwoMajorSubjectsPerDayPerSection(constraintFactory),
                majorSubjectsSameTeacherSectionSequential(constraintFactory),
                nonMajorSubjectsSameTeacherSectionSameTimeDifferentDays(constraintFactory),
                // --- NEW CONSTRAINTS FOR TEACHER MAJOR SUBJECTS ---
                majorSubjectsSameTeacherSequential(constraintFactory),
                maxThreeMajorSubjectsPerDayPerTeacher(constraintFactory),
                sameMajorSubjectSameTeacherSameClassroom(constraintFactory),
                sameMajorSubjectSameTeacherDifferentSectionsDifferentDays(constraintFactory)
        };
    }
    
    // --- Helper methods for groupBy constraints ---
    
    private LocalTime calculateEndTime(Allocation allocation) {
        if (allocation == null || allocation.getTimeslot() == null || allocation.getTimeslot().getStartTime() == null) return MIN_TIME;
        try {
            int duration = allocation.getDurationInMinutes();
            if (duration <= 0) return allocation.getTimeslot().getStartTime();
            return allocation.getTimeslot().getStartTime().plusMinutes(duration);
        } catch (Exception e) { return MIN_TIME; }
    }

    private boolean hasOverlap(List<Allocation> allocations) {
        // #region agent log
        logDebug("A", "ScheduleConstraintProvider.hasOverlap:ENTRY", "Checking overlaps", Map.of("allocationCount", allocations.size()));
        // #endregion
        
        if (allocations.size() <= 1) return false;
        
        // Filter out allocations without timeslots
        List<Allocation> validAllocations = allocations.stream()
            .filter(alloc -> alloc.getTimeslot() != null && alloc.getTimeslot().getStartTime() != null && alloc.getTimeslot().getDayOfWeek() != null)
            .collect(java.util.stream.Collectors.toList());
            
        if (validAllocations.size() <= 1) return false;
        
        // Group by day of week first, then check overlaps within each day
        Map<DayOfWeek, List<Allocation>> allocationsByDay = validAllocations.stream()
            .collect(java.util.stream.Collectors.groupingBy(alloc -> alloc.getTimeslot().getDayOfWeek()));
        
        // Check overlaps for each day separately
        for (Map.Entry<DayOfWeek, List<Allocation>> dayEntry : allocationsByDay.entrySet()) {
            List<Allocation> dayAllocations = dayEntry.getValue();
            if (dayAllocations.size() <= 1) continue;
            
            // Sort by start time for this day
            dayAllocations.sort((a, b) -> a.getTimeslot().getStartTime().compareTo(b.getTimeslot().getStartTime()));
            
            // #region agent log
            logDebug("A", "ScheduleConstraintProvider.hasOverlap:DAY_CHECK", "Checking day for overlaps", Map.of(
                "day", dayEntry.getKey().toString(),
                "allocationCount", dayAllocations.size(),
                "allocations", dayAllocations.stream().map(a -> a.getSubjectCode() + "(" + (a.isPinned() ? "PINNED" : "NEW") + ")").collect(java.util.stream.Collectors.joining(","))
            ));
            // #endregion
            
            // Check for overlaps within this day
            for (int i = 0; i < dayAllocations.size() - 1; i++) {
                Allocation current = dayAllocations.get(i);
                Allocation next = dayAllocations.get(i + 1);
                
                try {
                    LocalTime currentStart = current.getTimeslot().getStartTime();
                    LocalTime currentEnd = currentStart.plusMinutes(current.getDurationInMinutes());
                    LocalTime nextStart = next.getTimeslot().getStartTime();
                    
                    // #region agent log
                    boolean isExactOverlap = nextStart.equals(currentStart);
                    boolean isBeforeOverlap = nextStart.isBefore(currentEnd);
                    logDebug("A", "ScheduleConstraintProvider.hasOverlap:CHECK", "Comparing allocations", Map.of(
                        "day", dayEntry.getKey().toString(),
                        "currentSubject", current.getSubjectCode(),
                        "currentStart", currentStart.toString(),
                        "currentEnd", currentEnd.toString(),
                        "nextSubject", next.getSubjectCode(),
                        "nextStart", nextStart.toString(),
                        "isExactOverlap", isExactOverlap,
                        "isBeforeOverlap", isBeforeOverlap,
                        "currentSection", current.getSection() != null ? current.getSection().getId() : "null",
                        "nextSection", next.getSection() != null ? next.getSection().getId() : "null"
                    ));
                    // #endregion
                    
                    // Check if there's an overlap: next starts before current ends OR they start at the same time (on the same day)
                    // FIX: Changed from isBefore() to include exact overlaps (same start time)
                    if (nextStart.isBefore(currentEnd) || nextStart.equals(currentStart)) {
                        logger.warn("!!! OVERLAP DETECTED: {} ({} {} - {}) overlaps with {} ({} {} - {})", 
                                   current.getSubjectCode(), current.getTimeslot().getDayOfWeek(), currentStart, currentEnd,
                                   next.getSubjectCode(), next.getTimeslot().getDayOfWeek(), nextStart, nextStart.plusMinutes(next.getDurationInMinutes()));
                        // #region agent log
                        logDebug("A", "ScheduleConstraintProvider.hasOverlap:OVERLAP_FOUND", "Overlap detected", Map.of(
                            "currentSubject", current.getSubjectCode(),
                            "nextSubject", next.getSubjectCode(),
                            "day", dayEntry.getKey().toString(),
                            "isExactOverlap", isExactOverlap
                        ));
                        // #endregion
                        return true;
                    }
                } catch (Exception e) {
                    logger.error("Error calculating overlap for allocations: {}", e.getMessage());
                }
            }
        }
        // #region agent log
        logDebug("A", "ScheduleConstraintProvider.hasOverlap:EXIT", "No overlaps found", Map.of());
        // #endregion
        return false; // No overlaps found
    }

    private int calculateOverlapPenalty(List<Allocation> allocations) {
        if (allocations.size() <= 1) return 0;
        
        // Filter out allocations without timeslots
        List<Allocation> validAllocations = allocations.stream()
            .filter(alloc -> alloc.getTimeslot() != null && alloc.getTimeslot().getStartTime() != null && alloc.getTimeslot().getDayOfWeek() != null)
            .collect(java.util.stream.Collectors.toList());
            
        if (validAllocations.size() <= 1) return 0;
        
        int overlapCount = 0;
        
        // Group by day of week first, then check overlaps within each day
        Map<DayOfWeek, List<Allocation>> allocationsByDay = validAllocations.stream()
            .collect(java.util.stream.Collectors.groupingBy(alloc -> alloc.getTimeslot().getDayOfWeek()));
        
        // Check overlaps for each day separately
        for (Map.Entry<DayOfWeek, List<Allocation>> dayEntry : allocationsByDay.entrySet()) {
            List<Allocation> dayAllocations = dayEntry.getValue();
            if (dayAllocations.size() <= 1) continue;
            
            // Sort by start time for this day
            dayAllocations.sort((a, b) -> a.getTimeslot().getStartTime().compareTo(b.getTimeslot().getStartTime()));
            
            // Check each allocation against all subsequent ones on the same day
            for (int i = 0; i < dayAllocations.size(); i++) {
                Allocation current = dayAllocations.get(i);
                
                try {
                    LocalTime currentStart = current.getTimeslot().getStartTime();
                    LocalTime currentEnd = currentStart.plusMinutes(current.getDurationInMinutes());
                    
                    for (int j = i + 1; j < dayAllocations.size(); j++) {
                        Allocation next = dayAllocations.get(j);
                        LocalTime nextStart = next.getTimeslot().getStartTime();
                        
                        // #region agent log
                        boolean isExactOverlap = nextStart.equals(currentStart);
                        boolean isBeforeOverlap = nextStart.isBefore(currentEnd);
                        logDebug("C", "ScheduleConstraintProvider.calculateOverlapPenalty:CHECK", "Checking penalty calculation", Map.of(
                            "currentSubject", current.getSubjectCode(),
                            "nextSubject", next.getSubjectCode(),
                            "currentStart", currentStart.toString(),
                            "currentEnd", currentEnd.toString(),
                            "nextStart", nextStart.toString(),
                            "isExactOverlap", isExactOverlap,
                            "isBeforeOverlap", isBeforeOverlap
                        ));
                        // #endregion
                        
                        // If next starts before current ends OR they start at the same time (on the same day), there's an overlap
                        // FIX: Changed from isBefore() to include exact overlaps (same start time)
                        if (nextStart.isBefore(currentEnd) || nextStart.equals(currentStart)) {
                            overlapCount++;
                            logger.warn("!!! PENALIZING Overlap: {} ({} {} - {}) overlaps with {} ({} {} - {})", 
                                       current.getSubjectCode(), current.getTimeslot().getDayOfWeek(), currentStart, currentEnd,
                                       next.getSubjectCode(), next.getTimeslot().getDayOfWeek(), nextStart, nextStart.plusMinutes(next.getDurationInMinutes()));
                            // #region agent log
                            logDebug("C", "ScheduleConstraintProvider.calculateOverlapPenalty:OVERLAP_COUNTED", "Overlap counted", Map.of(
                                "overlapCount", overlapCount,
                                "isExactOverlap", isExactOverlap
                            ));
                            // #endregion
                        } else {
                            // Since allocations are sorted by start time, we can break here
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error calculating overlap penalty for allocation {}: {}", current.getId(), e.getMessage());
                }
            }
        }
        return overlapCount;
    }

    // --- HARD CONSTRAINTS (Using stable groupBy) ---

    private Constraint teacherConflict(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> alloc.getTeacher() != null && alloc.getTimeslot() != null)
                .groupBy(Allocation::getTeacher, ConstraintCollectors.toList())
                .filter((teacher, allocs) -> {
                    // #region agent log
                    long pinnedCount = allocs.stream().filter(Allocation::isPinned).count();
                    long unpinnedCount = allocs.size() - pinnedCount;
                    logDebug("D", "ScheduleConstraintProvider.teacherConflict:FILTER", "Checking teacher for conflicts", Map.of(
                        "teacherId", teacher.getId(),
                        "teacherName", teacher.getName(),
                        "totalAllocations", allocs.size(),
                        "pinnedCount", pinnedCount,
                        "unpinnedCount", unpinnedCount
                    ));
                    // #endregion
                    boolean hasOverlapResult = hasOverlap(allocs);
                    // #region agent log
                    logDebug("D", "ScheduleConstraintProvider.teacherConflict:FILTER_RESULT", "hasOverlap result for teacher", Map.of(
                        "teacherId", teacher.getId(),
                        "hasOverlap", hasOverlapResult
                    ));
                    // #endregion
                    return hasOverlapResult;
                })
                .penalize(HardSoftScore.ONE_HARD, (teacher, allocs) -> {
                    // #region agent log
                    int penalty = calculateOverlapPenalty(allocs);
                    logDebug("D", "ScheduleConstraintProvider.teacherConflict:PENALIZE", "Applying penalty for teacher", Map.of(
                        "teacherId", teacher.getId(),
                        "penalty", penalty
                    ));
                    // #endregion
                    return penalty;
                })
                .asConstraint("Teacher conflict");
    }

    private Constraint classroomConflict(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> alloc.getClassroom() != null && alloc.getTimeslot() != null)
                .groupBy(Allocation::getClassroom, ConstraintCollectors.toList())
                .filter((classroom, allocs) -> {
                    // #region agent log
                    long pinnedCount = allocs.stream().filter(Allocation::isPinned).count();
                    long unpinnedCount = allocs.size() - pinnedCount;
                    logDebug("D", "ScheduleConstraintProvider.classroomConflict:FILTER", "Checking classroom for conflicts", Map.of(
                        "classroomId", classroom.getId(),
                        "classroomName", classroom.getName(),
                        "totalAllocations", allocs.size(),
                        "pinnedCount", pinnedCount,
                        "unpinnedCount", unpinnedCount
                    ));
                    // #endregion
                    boolean hasOverlapResult = hasOverlap(allocs);
                    // #region agent log
                    logDebug("D", "ScheduleConstraintProvider.classroomConflict:FILTER_RESULT", "hasOverlap result for classroom", Map.of(
                        "classroomId", classroom.getId(),
                        "hasOverlap", hasOverlapResult
                    ));
                    // #endregion
                    return hasOverlapResult;
                })
                .penalize(HardSoftScore.ONE_HARD, (classroom, allocs) -> {
                    // #region agent log
                    int penalty = calculateOverlapPenalty(allocs);
                    logDebug("D", "ScheduleConstraintProvider.classroomConflict:PENALIZE", "Applying penalty for classroom", Map.of(
                        "classroomId", classroom.getId(),
                        "penalty", penalty
                    ));
                    // #endregion
                    return penalty;
                })
                .asConstraint("Classroom conflict");
    }

    private Constraint sectionConflict(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> alloc.getSection() != null && alloc.getTimeslot() != null)
                .groupBy(Allocation::getSection, ConstraintCollectors.toList())
                .filter((section, allocs) -> {
                    // #region agent log
                    logDebug("A", "ScheduleConstraintProvider.sectionConflict:FILTER", "Checking section for conflicts", Map.of(
                        "sectionId", section.getId(),
                        "sectionName", section.getSectionName(),
                        "allocationCount", allocs.size()
                    ));
                    // #endregion
                    boolean hasOverlapResult = hasOverlap(allocs);
                    // #region agent log
                    logDebug("A", "ScheduleConstraintProvider.sectionConflict:FILTER_RESULT", "hasOverlap result", Map.of(
                        "sectionId", section.getId(),
                        "hasOverlap", hasOverlapResult
                    ));
                    // #endregion
                    return hasOverlapResult;
                })
                .penalize(HardSoftScore.ONE_HARD, (section, allocs) -> {
                    // #region agent log
                    int penalty = calculateOverlapPenalty(allocs);
                    logDebug("A", "ScheduleConstraintProvider.sectionConflict:PENALIZE", "Applying penalty", Map.of(
                        "sectionId", section.getId(),
                        "penalty", penalty
                    ));
                    // #endregion
                    return penalty;
                })
                .asConstraint("Section conflict");
    }

    /**
     * Additional constraint to catch exact time conflicts (same day, same time)
     * This is a more direct approach to prevent the specific double booking issues
     */
    private Constraint exactTimeConflict(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> alloc.getTimeslot() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getDayOfWeek()),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getStartTime()))
                .filter((alloc1, alloc2) -> {
                    // #region agent log
                    logDebug("B", "ScheduleConstraintProvider.exactTimeConflict:JOIN", "Checking exact time conflict", Map.of(
                        "alloc1Id", alloc1.getId(),
                        "alloc1Subject", alloc1.getSubjectCode(),
                        "alloc2Id", alloc2.getId(),
                        "alloc2Subject", alloc2.getSubjectCode(),
                        "day", alloc1.getTimeslot().getDayOfWeek().toString(),
                        "time", alloc1.getTimeslot().getStartTime().toString()
                    ));
                    // #endregion
                    
                    // Only penalize if they are different allocations
                    if (alloc1.getId().equals(alloc2.getId())) {
                        // #region agent log
                        logDebug("B", "ScheduleConstraintProvider.exactTimeConflict:SAME_ALLOC", "Same allocation, skipping", Map.of());
                        // #endregion
                        return false;
                    }
                    
                    // Check if they have the same teacher, classroom, or section
                    boolean sameTeacher = alloc1.getTeacher() != null && alloc2.getTeacher() != null && 
                                        alloc1.getTeacher().getId().equals(alloc2.getTeacher().getId());
                    boolean sameClassroom = alloc1.getClassroom() != null && alloc2.getClassroom() != null && 
                                          alloc1.getClassroom().getId().equals(alloc2.getClassroom().getId());
                    boolean sameSection = alloc1.getSection() != null && alloc2.getSection() != null && 
                                        alloc1.getSection().getId().equals(alloc2.getSection().getId());
                    
                    // #region agent log
                    logDebug("B", "ScheduleConstraintProvider.exactTimeConflict:CHECK", "Checking conflict conditions", Map.of(
                        "sameTeacher", sameTeacher,
                        "sameClassroom", sameClassroom,
                        "sameSection", sameSection,
                        "alloc1SectionId", alloc1.getSection() != null ? alloc1.getSection().getId() : "null",
                        "alloc2SectionId", alloc2.getSection() != null ? alloc2.getSection().getId() : "null"
                    ));
                    // #endregion
                    
                    if (sameTeacher || sameClassroom || sameSection) {
                        logger.warn("!!! EXACT TIME CONFLICT: {} and {} at same time {} on {}", 
                                   alloc1.getSubjectCode(), alloc2.getSubjectCode(), 
                                   alloc1.getTimeslot().getStartTime(), alloc1.getTimeslot().getDayOfWeek());
                        // #region agent log
                        logDebug("B", "ScheduleConstraintProvider.exactTimeConflict:CONFLICT_FOUND", "Exact time conflict detected", Map.of(
                            "alloc1Subject", alloc1.getSubjectCode(),
                            "alloc2Subject", alloc2.getSubjectCode(),
                            "conflictType", sameSection ? "sameSection" : (sameTeacher ? "sameTeacher" : "sameClassroom")
                        ));
                        // #endregion
                        return true;
                    }
                    // #region agent log
                    logDebug("B", "ScheduleConstraintProvider.exactTimeConflict:NO_CONFLICT", "No conflict conditions met", Map.of());
                    // #endregion
                    return false;
                })
                .penalize(HardSoftScore.ONE_HARD, (alloc1, alloc2) -> {
                    // #region agent log
                    logDebug("B", "ScheduleConstraintProvider.exactTimeConflict:PENALIZE", "Applying penalty", Map.of(
                        "alloc1Subject", alloc1.getSubjectCode(),
                        "alloc2Subject", alloc2.getSubjectCode()
                    ));
                    // #endregion
                    return 1;
                })
                .asConstraint("Exact time conflict");
    }

    /**
     * Prevent the same subject for the same section from being scheduled at the exact same time
     * This addresses the specific issue where CAPSTONE 1 for BSIT 3-D appears twice at 08:00 AM
     */
    private Constraint sameSubjectSameTimeConflict(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> alloc.getTimeslot() != null && alloc.getSection() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getSection().getId()),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getDayOfWeek()),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getStartTime()))
                .filter((alloc1, alloc2) -> {
                    // Only penalize if they are different allocations
                    if (alloc1.getId().equals(alloc2.getId())) return false;
                    
                    logger.warn("!!! SAME SUBJECT SAME TIME CONFLICT: {} for section {} at {} on {}", 
                               alloc1.getSubjectCode(), alloc1.getSection().getSectionName(),
                               alloc1.getTimeslot().getStartTime(), alloc1.getTimeslot().getDayOfWeek());
                    return true;
                })
                .penalize(HardSoftScore.ONE_HARD, (alloc1, alloc2) -> 1)
                .asConstraint("Same subject same time conflict");
    }

     private Constraint bsitLabConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getSection() != null && "BSIT".equalsIgnoreCase(alloc.getSection().getProgram()))
                .penalize(HardSoftScore.of(100, 0), alloc -> {
                    Classroom classroom = alloc.getClassroom();
                    String classroomType = (classroom != null && classroom.getType() != null) ? classroom.getType().trim() : "";
                    logger.warn("@@@ BSIT LAB CHECK: Subject: {} | Classroom: {} | Type: {} | isMajor: {} | Section Program: {}", 
                               alloc.getSubjectCode(), 
                               classroom != null ? classroom.getName() : "NULL",
                               classroomType, 
                               alloc.isMajor(),
                               alloc.getSection() != null ? alloc.getSection().getProgram() : "NULL");
                    
                    // Check for various computer lab types - be more flexible
                    boolean isComputerLab = "Computer Laboratory".equalsIgnoreCase(classroomType) ||
                                          "Computer Lab".equalsIgnoreCase(classroomType) ||
                                          "Laboratory".equalsIgnoreCase(classroomType) ||
                                          "Lab".equalsIgnoreCase(classroomType) ||
                                          classroomType.toLowerCase().contains("computer") ||
                                          classroomType.toLowerCase().contains("lab") ||
                                          classroomType.toLowerCase().contains("laboratory");
                    
                    if (!isComputerLab) {
                        logger.warn("@@@ PENALIZING BSIT LAB RULE for Subject: {} - Expected Computer Laboratory, got: {}", 
                                   alloc.getSubjectCode(), classroomType);
                        return 10; // Much higher penalty
                    }
                    logger.info("@@@ BSIT LAB RULE PASSED for Subject: {} - Correctly assigned to Computer Laboratory", 
                               alloc.getSubjectCode());
                    return 0;
                }).asConstraint("BSIT major subject must be in a computer laboratory");
     }

    private Constraint generalLabConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getSection() != null && !"BSIT".equalsIgnoreCase(alloc.getSection().getProgram()))
                .penalize(HardSoftScore.of(50, 0), alloc -> {
                    Classroom classroom = alloc.getClassroom();
                    String classroomType = (classroom != null && classroom.getType() != null) ? classroom.getType().trim() : "";
                    logger.warn("@@@ GENERAL LAB CHECK: Subject: {} | Classroom: {} | Type: {} | isMajor: {}", 
                               alloc.getSubjectCode(), 
                               classroom != null ? classroom.getName() : "NULL",
                               classroomType, 
                               alloc.isMajor());
                    
                    // Check for various lab types
                    boolean isLab = classroomType.toLowerCase().contains("laboratory") ||
                                  classroomType.toLowerCase().contains("lab");
                    
                    if (!isLab) {
                        logger.warn("@@@ PENALIZING GENERAL LAB RULE for Subject: {} - Expected Laboratory, got: {}", 
                                   alloc.getSubjectCode(), classroomType);
                        return 1;
                    }
                    logger.info("@@@ GENERAL LAB RULE PASSED for Subject: {} - Correctly assigned to Laboratory", 
                               alloc.getSubjectCode());
                    return 0;
                }).asConstraint("Major subject must be in a laboratory");
    }

    /**
     * Non-major subjects should be assigned to lecture rooms
     */
    private Constraint nonMajorLectureRoomConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && !alloc.isMajor())
                .penalize(HardSoftScore.of(50, 0), alloc -> {
                    Classroom classroom = alloc.getClassroom();
                    String classroomType = (classroom != null && classroom.getType() != null) ? classroom.getType().trim() : "";
                    logger.warn("@@@ NON-MAJOR LECTURE CHECK: Subject: {} | Classroom: {} | Type: {} | isMajor: {}", 
                               alloc.getSubjectCode(), 
                               classroom != null ? classroom.getName() : "NULL",
                               classroomType, 
                               alloc.isMajor());
                    
                    // Check for various lecture room types
                    boolean isLectureRoom = "Lecture Room".equalsIgnoreCase(classroomType) ||
                                          "Lecture".equalsIgnoreCase(classroomType) ||
                                          classroomType.toLowerCase().contains("lecture") ||
                                          classroomType.toLowerCase().contains("classroom");
                    
                    if (!isLectureRoom) {
                        logger.warn("@@@ PENALIZING NON-MAJOR LECTURE ROOM RULE for Subject: {} - Expected Lecture Room, got: {}", 
                                   alloc.getSubjectCode(), classroomType);
                        return 1;
                    }
                    logger.info("@@@ NON-MAJOR LECTURE RULE PASSED for Subject: {} - Correctly assigned to Lecture Room", 
                               alloc.getSubjectCode());
                    return 0;
                }).asConstraint("Non-major subject should be in a lecture room");
    }

    private Constraint scheduleEndTimeLimit(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                // --- ADDED !alloc.isPinned() ---
                .filter(alloc -> !alloc.isPinned() && alloc.getTimeslot() != null)
                .penalize(HardSoftScore.ONE_HARD, alloc -> {
                     try {
                        LocalTime endTime = calculateEndTime(alloc);
                        if (endTime.isAfter(MAX_END_TIME)) { return 1; } return 0;
                    } catch (Exception e) { return 0; }
                })
                .asConstraint("Class ends after 8:30 PM");
    }

    // --- OPTIMIZED SCHEDULING CONSTRAINTS ---
    
    /**
     * Non-major subjects should be scheduled at the same time but on different days
     */
    private Constraint nonMajorSubjectsSameTimeDifferentDays(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && !alloc.isMajor() && alloc.getTimeslot() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getDayOfWeek()))
                .filter((alloc1, alloc2) -> {
                    // Only penalize if they are different allocations of the same subject on the same day
                    return !alloc1.getId().equals(alloc2.getId());
                })
                .penalize(HardSoftScore.of(200, 0), (alloc1, alloc2) -> {
                    logger.warn("@@@ NON-MAJOR SAME DAY PENALTY: {} scheduled on same day {}", 
                               alloc1.getSubjectCode(), alloc1.getTimeslot().getDayOfWeek());
                    return 1;
                })
                .asConstraint("Non-major subjects should be on different days");
    }

    /**
     * Force non-major subjects to be at the same time on different days
     */
    private Constraint forceNonMajorSameTimeDifferentDays(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && !alloc.isMajor() && alloc.getTimeslot() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode))
                .filter((alloc1, alloc2) -> {
                    if (alloc1.getId().equals(alloc2.getId())) return false;
                    // Penalize if same subject is at different times or on same day
                    boolean sameTime = alloc1.getTimeslot().getStartTime().equals(alloc2.getTimeslot().getStartTime());
                    boolean sameDay = alloc1.getTimeslot().getDayOfWeek().equals(alloc2.getTimeslot().getDayOfWeek());
                    return !sameTime || sameDay; // Penalize if not same time OR if same day
                })
                .penalize(HardSoftScore.of(300, 0), (alloc1, alloc2) -> {
                    logger.warn("@@@ NON-MAJOR WRONG PATTERN: {} at {} on {} vs {} on {}", 
                               alloc1.getSubjectCode(), 
                               alloc1.getTimeslot().getStartTime(), alloc1.getTimeslot().getDayOfWeek(),
                               alloc2.getTimeslot().getStartTime(), alloc2.getTimeslot().getDayOfWeek());
                    return 1;
                })
                .asConstraint("Force non-major subjects same time different days");
    }

    /**
     * Reward non-major subjects for being scheduled at the same time on different days
     */
    private Constraint rewardNonMajorSameTimeDifferentDays(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && !alloc.isMajor() && alloc.getTimeslot() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getStartTime()))
                .filter((alloc1, alloc2) -> {
                    // Reward if they are different subjects at the same time on different days
                    return !alloc1.getSubjectCode().equals(alloc2.getSubjectCode()) && 
                           !alloc1.getId().equals(alloc2.getId()) &&
                           !alloc1.getTimeslot().getDayOfWeek().equals(alloc2.getTimeslot().getDayOfWeek());
                })
                .reward(HardSoftScore.of(0, 10), (alloc1, alloc2) -> {
                    logger.info("@@@ NON-MAJOR REWARD: {} and {} at same time {} on different days", 
                               alloc1.getSubjectCode(), alloc2.getSubjectCode(), 
                               alloc1.getTimeslot().getStartTime());
                    return 1;
                })
                .asConstraint("Reward non-major subjects for same time different days");
    }

    /**
     * Force major subjects to be on the same day (first priority)
     */
    private Constraint forceMajorSameDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getTimeslot() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode))
                .filter((alloc1, alloc2) -> {
                    if (alloc1.getId().equals(alloc2.getId())) return false;
                    if (alloc1.getTimeslot() == null || alloc2.getTimeslot() == null) return false;
                    
                    boolean sameDay = alloc1.getTimeslot().getDayOfWeek().equals(alloc2.getTimeslot().getDayOfWeek());
                    return !sameDay; // Penalize if not on same day
                })
                .penalize(HardSoftScore.of(500, 0), (alloc1, alloc2) -> {
                    logger.warn("@@@ MAJOR DIFFERENT DAY PENALTY: {} at {} on {} vs {} on {}", 
                               alloc1.getSubjectCode(), 
                               alloc1.getTimeslot().getStartTime(), alloc1.getTimeslot().getDayOfWeek(),
                               alloc2.getTimeslot().getStartTime(), alloc2.getTimeslot().getDayOfWeek());
                    return 1;
                })
                .asConstraint("Force major subjects same day");
    }

    /**
     * Force major subjects to be sequential when on same day
     * Updated to also check for same section to avoid false positives
     */
    private Constraint forceMajorSequential(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getTimeslot() != null && alloc.getSection() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSection),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getDayOfWeek()))
                .filter((alloc1, alloc2) -> {
                    if (alloc1.getId().equals(alloc2.getId())) return false;
                    if (alloc1.getTimeslot() == null || alloc2.getTimeslot() == null) return false;
                    
                    LocalTime endTime1 = alloc1.getTimeslot().getStartTime().plusMinutes(alloc1.getDurationInMinutes());
                    LocalTime endTime2 = alloc2.getTimeslot().getStartTime().plusMinutes(alloc2.getDurationInMinutes());
                    LocalTime start1 = alloc1.getTimeslot().getStartTime();
                    LocalTime start2 = alloc2.getTimeslot().getStartTime();
                    
                    // Sequential means: end1 == start2 OR end2 == start1
                    boolean sequential = endTime1.equals(start2) || endTime2.equals(start1);
                    return !sequential; // Penalize if not sequential
                })
                .penalize(HardSoftScore.of(300, 0), (alloc1, alloc2) -> {
                    logger.warn("@@@ MAJOR NON-SEQUENTIAL PENALTY: {} for section {} at {} vs {} on {}", 
                               alloc1.getSubjectCode(), alloc1.getSection().getSectionName(),
                               alloc1.getTimeslot().getStartTime(),
                               alloc2.getTimeslot().getStartTime(), alloc1.getTimeslot().getDayOfWeek());
                    return 1;
                })
                .asConstraint("Force major subjects sequential");
    }

    /**
     * Reward major subjects for being sequential on the same day
     */
    private Constraint rewardMajorSequentialSameDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getTimeslot() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getDayOfWeek()))
                .filter((alloc1, alloc2) -> {
                    if (alloc1.getTimeslot() == null || alloc2.getTimeslot() == null) return false;
                    if (alloc1.getId().equals(alloc2.getId())) return false; // Same allocation
                    LocalTime endTime1 = alloc1.getTimeslot().getStartTime().plusMinutes(alloc1.getDurationInMinutes());
                    return endTime1.equals(alloc2.getTimeslot().getStartTime());
                })
                .reward(HardSoftScore.of(0, 10), (alloc1, alloc2) -> {
                    logger.info("@@@ MAJOR REWARD: {} sessions sequential on {}", 
                               alloc1.getSubjectCode(), alloc1.getTimeslot().getDayOfWeek());
                    return 1;
                })
                .asConstraint("Reward major subjects for being sequential on same day");
    }

    /**
     * Prefer consistent time slots for non-major subjects (soft constraint)
     */
    private Constraint preferConsistentTimeForNonMajor(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && !alloc.isMajor() && alloc.getTimeslot() != null)
                .penalize(HardSoftScore.of(0, 1), alloc -> {
                    // This is a soft constraint - we prefer consistent times but don't force them
                    return 0; // No penalty for now, just placeholder
                })
                .asConstraint("Prefer consistent time slots for non-major subjects");
    }

    /**
     * Prefer consistent time slots for major subjects (soft constraint)
     */
    private Constraint preferConsistentTimeForMajor(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getTimeslot() != null)
                .penalize(HardSoftScore.of(0, 1), alloc -> {
                    // This is a soft constraint - we prefer consistent times but don't force them
                    return 0; // No penalty for now, just placeholder
                })
                .asConstraint("Prefer consistent time slots for major subjects");
    }

    /**
     * Avoid late evening classes (after 6:00 PM)
     */
    private Constraint avoidLateEveningClasses(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.getTimeslot() != null)
                .penalize(HardSoftScore.of(0, 2), alloc -> {
                    LocalTime startTime = alloc.getTimeslot().getStartTime();
                    if (startTime.isAfter(LocalTime.of(18, 0))) return 1; // Late evening penalty
                    return 0;
                })
                .asConstraint("Avoid late evening classes");
    }

    /**
     * Optimize classroom utilization (prefer 50-90% capacity)
     */
    private Constraint optimizeClassroomUtilization(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.getClassroom() != null && alloc.getSection() != null)
                .penalize(HardSoftScore.of(0, 1), alloc -> {
                    int classroomCapacity = alloc.getClassroom().getCapacity();
                    int sectionSize = alloc.getSection().getNumberOfStudents();
                    if (classroomCapacity == 0) return 3; // No capacity info
                    
                    double utilization = (double) sectionSize / classroomCapacity;
                    if (utilization >= 0.5 && utilization <= 0.9) return 0; // Optimal
                    if (utilization >= 0.3 && utilization <= 1.0) return 1; // Acceptable
                    return 2; // Poor utilization
                })
                .asConstraint("Optimize classroom utilization");
    }

    /**
     * Maximum 2 major subjects per day per section
     * Rule: Allow only 2 major subjects to be held on the same day per section
     */
    private Constraint maxTwoMajorSubjectsPerDayPerSection(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getTimeslot() != null && alloc.getSection() != null)
                .groupBy(
                    alloc -> alloc.getSection(),
                    alloc -> alloc.getTimeslot().getDayOfWeek(),
                    ConstraintCollectors.count()
                )
                .filter((section, day, count) -> count > 2)
                .penalize(HardSoftScore.ONE_HARD, (section, day, count) -> {
                    logger.warn("!!! MAX MAJOR SUBJECTS VIOLATION: Section {} has {} major subjects on {}", 
                               section.getSectionName(), count, day);
                    return (int)(count - 2); // Penalty increases with each subject over 2
                })
                .asConstraint("Maximum 2 major subjects per day per section");
    }

    /**
     * Major subjects from same teacher and section must be sequential
     * Rule: Major subjects from the same teacher and section must be held at sequential time
     */
    private Constraint majorSubjectsSameTeacherSectionSequential(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getTimeslot() != null && 
                               alloc.getTeacher() != null && alloc.getSection() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getTeacher),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSection),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getDayOfWeek()))
                .filter((alloc1, alloc2) -> {
                    if (alloc1.getId().equals(alloc2.getId())) return false;
                    if (alloc1.getTimeslot() == null || alloc2.getTimeslot() == null) return false;
                    
                    // Check if they are sequential: end time of one equals start time of the other
                    LocalTime endTime1 = alloc1.getTimeslot().getStartTime().plusMinutes(alloc1.getDurationInMinutes());
                    LocalTime endTime2 = alloc2.getTimeslot().getStartTime().plusMinutes(alloc2.getDurationInMinutes());
                    LocalTime start1 = alloc1.getTimeslot().getStartTime();
                    LocalTime start2 = alloc2.getTimeslot().getStartTime();
                    
                    // Sequential means: end1 == start2 OR end2 == start1
                    boolean sequential = endTime1.equals(start2) || endTime2.equals(start1);
                    return !sequential; // Penalize if not sequential
                })
                .penalize(HardSoftScore.ONE_HARD, (alloc1, alloc2) -> {
                    logger.warn("!!! MAJOR NON-SEQUENTIAL VIOLATION: {} and {} for teacher {} section {} on {} - not sequential", 
                               alloc1.getSubjectCode(), alloc2.getSubjectCode(),
                               alloc1.getTeacher().getName(), alloc1.getSection().getSectionName(),
                               alloc1.getTimeslot().getDayOfWeek());
                    return 1;
                })
                .asConstraint("Major subjects same teacher section sequential");
    }

    /**
     * Non-major subjects from same teacher and section must be same time different days
     * Rule: Non major subjects from same teacher and section must be held on the same timeslot but different days
     */
    private Constraint nonMajorSubjectsSameTeacherSectionSameTimeDifferentDays(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && !alloc.isMajor() && alloc.getTimeslot() != null && 
                               alloc.getTeacher() != null && alloc.getSection() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getTeacher),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSection),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode))
                .filter((alloc1, alloc2) -> {
                    if (alloc1.getId().equals(alloc2.getId())) return false;
                    if (alloc1.getTimeslot() == null || alloc2.getTimeslot() == null) return false;
                    
                    // Check if same time and same day (violation) OR different time (violation)
                    boolean sameTime = alloc1.getTimeslot().getStartTime().equals(alloc2.getTimeslot().getStartTime());
                    boolean sameDay = alloc1.getTimeslot().getDayOfWeek().equals(alloc2.getTimeslot().getDayOfWeek());
                    
                    // Penalize if: not same time OR same day (should be same time AND different days)
                    return !sameTime || sameDay;
                })
                .penalize(HardSoftScore.ONE_HARD, (alloc1, alloc2) -> {
                    logger.warn("!!! NON-MAJOR RULE VIOLATION: {} for teacher {} section {} - should be same time different days, but got {} on {} vs {} on {}", 
                               alloc1.getSubjectCode(), alloc1.getTeacher().getName(), alloc1.getSection().getSectionName(),
                               alloc1.getTimeslot().getStartTime(), alloc1.getTimeslot().getDayOfWeek(),
                               alloc2.getTimeslot().getStartTime(), alloc2.getTimeslot().getDayOfWeek());
                    return 1;
                })
                .asConstraint("Non-major subjects same teacher section same time different days");
    }

    /**
     * Major subjects from same teacher (across all sections) must be sequential
     * Rule: Same teacher's major subjects must be at different sequential timeslots (not overlapping)
     */
    private Constraint majorSubjectsSameTeacherSequential(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> alloc.isMajor() && alloc.getTimeslot() != null && 
                               alloc.getTeacher() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getTeacher),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getDayOfWeek()))
                .filter((alloc1, alloc2) -> {
                    // #region agent log
                    logDebug("E", "ScheduleConstraintProvider.majorSubjectsSameTeacherSequential:JOIN", "Checking major subjects for same teacher", Map.of(
                        "alloc1Id", alloc1.getId(),
                        "alloc1Subject", alloc1.getSubjectCode(),
                        "alloc1Section", alloc1.getSection() != null ? alloc1.getSection().getSectionName() : "null",
                        "alloc1Pinned", alloc1.isPinned(),
                        "alloc2Id", alloc2.getId(),
                        "alloc2Subject", alloc2.getSubjectCode(),
                        "alloc2Section", alloc2.getSection() != null ? alloc2.getSection().getSectionName() : "null",
                        "alloc2Pinned", alloc2.isPinned(),
                        "teacher", alloc1.getTeacher().getName(),
                        "day", alloc1.getTimeslot().getDayOfWeek().toString()
                    ));
                    // #endregion
                    
                    if (alloc1.getId().equals(alloc2.getId())) {
                        // #region agent log
                        logDebug("E", "ScheduleConstraintProvider.majorSubjectsSameTeacherSequential:SAME_ALLOC", "Same allocation, skipping", Map.of());
                        // #endregion
                        return false;
                    }
                    if (alloc1.getTimeslot() == null || alloc2.getTimeslot() == null) return false;
                    
                    LocalTime start1 = alloc1.getTimeslot().getStartTime();
                    LocalTime start2 = alloc2.getTimeslot().getStartTime();
                    LocalTime end1 = start1.plusMinutes(alloc1.getDurationInMinutes());
                    LocalTime end2 = start2.plusMinutes(alloc2.getDurationInMinutes());
                    
                    // Check if they are sequential: end1 == start2 OR end2 == start1
                    boolean sequential = end1.equals(start2) || end2.equals(start1);
                    
                    // Check for overlap: Two time ranges overlap if they share any time
                    // Standard overlap check: (start1 < end2) AND (start2 < end1)
                    // But isBefore() returns false for equals, so we need to handle equals separately
                    // Also need to catch exact same start time
                    boolean overlapCheck1 = start1.isBefore(end2);
                    boolean overlapCheck2 = start2.isBefore(end1);
                    boolean overlapCheck3 = start1.equals(start2); // Same start time = overlap
                    boolean hasOverlap = (overlapCheck1 && overlapCheck2) || overlapCheck3;
                    
                    // Violation: has overlap AND not sequential
                    boolean violation = hasOverlap && !sequential;
                    
                    // #region agent log
                    logDebug("E", "ScheduleConstraintProvider.majorSubjectsSameTeacherSequential:CHECK", "Checking overlap", Map.of(
                        "start1", start1.toString(),
                        "end1", end1.toString(),
                        "start2", start2.toString(),
                        "end2", end2.toString(),
                        "sequential", sequential,
                        "overlapCheck1", overlapCheck1,
                        "overlapCheck2", overlapCheck2,
                        "overlapCheck3", overlapCheck3,
                        "hasOverlap", hasOverlap,
                        "violation", violation
                    ));
                    // #endregion
                    
                    if (violation) {
                        logger.warn("!!! MAJOR TEACHER OVERLAP VIOLATION: {} and {} for teacher {} on {} - overlapping times {} vs {}", 
                                   alloc1.getSubjectCode(), alloc2.getSubjectCode(),
                                   alloc1.getTeacher().getName(), alloc1.getTimeslot().getDayOfWeek(),
                                   start1 + "-" + end1, start2 + "-" + end2);
                        // #region agent log
                        logDebug("E", "ScheduleConstraintProvider.majorSubjectsSameTeacherSequential:VIOLATION", "Violation detected", Map.of(
                            "alloc1Subject", alloc1.getSubjectCode(),
                            "alloc2Subject", alloc2.getSubjectCode()
                        ));
                        // #endregion
                    }
                    return violation;
                })
                .penalize(HardSoftScore.ONE_HARD, (alloc1, alloc2) -> {
                    logger.warn("!!! PENALIZING MAJOR TEACHER OVERLAP: {} and {} for teacher {} on {}", 
                               alloc1.getSubjectCode(), alloc2.getSubjectCode(),
                               alloc1.getTeacher().getName(), alloc1.getTimeslot().getDayOfWeek());
                    return 1;
                })
                .asConstraint("Major subjects same teacher sequential");
    }

    /**
     * Maximum 6 major subjects per day per teacher
     * Rule: Teachers should only have 6 major subjects scheduled per day
     */
    private Constraint maxThreeMajorSubjectsPerDayPerTeacher(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getTimeslot() != null && 
                               alloc.getTeacher() != null)
                .groupBy(
                    alloc -> alloc.getTeacher(),
                    alloc -> alloc.getTimeslot().getDayOfWeek(),
                    ConstraintCollectors.count()
                )
                .filter((teacher, day, count) -> count > 6)
                .penalize(HardSoftScore.ONE_HARD, (teacher, day, count) -> {
                    logger.warn("!!! MAX MAJOR SUBJECTS PER TEACHER VIOLATION: Teacher {} has {} major subjects on {}", 
                               teacher.getName(), count, day);
                    return (int)(count - 6); // Penalty increases with each subject over 6
                })
                .asConstraint("Maximum 6 major subjects per day per teacher");
    }

    /**
     * Same major subject with same teacher must be at the same classroom/laboratory
     * Rule: If a teacher teaches the same major subject (across different sections), it must be in the same classroom
     */
    private Constraint sameMajorSubjectSameTeacherSameClassroom(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getTimeslot() != null && 
                               alloc.getTeacher() != null && alloc.getClassroom() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getTeacher),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode))
                .filter((alloc1, alloc2) -> {
                    if (alloc1.getId().equals(alloc2.getId())) return false;
                    if (alloc1.getClassroom() == null || alloc2.getClassroom() == null) return false;
                    
                    // Check if they are in different classrooms - this is a violation
                    boolean differentClassroom = !alloc1.getClassroom().getId().equals(alloc2.getClassroom().getId());
                    
                    if (differentClassroom) {
                        logger.warn("!!! SAME MAJOR SUBJECT DIFFERENT CLASSROOM VIOLATION: {} for teacher {} - {} vs {}", 
                                   alloc1.getSubjectCode(), alloc1.getTeacher().getName(),
                                   alloc1.getClassroom().getName(), alloc2.getClassroom().getName());
                    }
                    return differentClassroom;
                })
                .penalize(HardSoftScore.ONE_HARD, (alloc1, alloc2) -> {
                    logger.warn("!!! PENALIZING SAME MAJOR SUBJECT DIFFERENT CLASSROOM: {} for teacher {}", 
                               alloc1.getSubjectCode(), alloc1.getTeacher().getName());
                    return 1;
                })
                .asConstraint("Same major subject same teacher same classroom");
    }

    /**
     * Same major subject with same teacher but different sections must be on different days and same time slots
     * Rule: If a teacher teaches the same major subject to different sections:
     *   - They must be scheduled on different days (prevents overlaps)
     *   - They should be at the same time slots (e.g., both at 08:00-11:00 but on different days)
     *   - Sessions within each section should be sequential
     * This ensures proper scheduling distribution and consistency
     */
    private Constraint sameMajorSubjectSameTeacherDifferentSectionsDifferentDays(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> alloc.isMajor() && alloc.getTimeslot() != null && 
                               alloc.getTeacher() != null && alloc.getSection() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getTeacher),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode))
                .filter((alloc1, alloc2) -> {
                    // #region agent log
                    Map<String, Object> logData = new HashMap<>();
                    logData.put("alloc1Id", alloc1.getId());
                    logData.put("alloc1Subject", alloc1.getSubjectCode());
                    logData.put("alloc1Section", alloc1.getSection().getSectionName());
                    logData.put("alloc1Pinned", alloc1.isPinned());
                    logData.put("alloc1Day", alloc1.getTimeslot().getDayOfWeek().toString());
                    logData.put("alloc1Time", alloc1.getTimeslot().getStartTime().toString());
                    logData.put("alloc2Id", alloc2.getId());
                    logData.put("alloc2Subject", alloc2.getSubjectCode());
                    logData.put("alloc2Section", alloc2.getSection().getSectionName());
                    logData.put("alloc2Pinned", alloc2.isPinned());
                    logData.put("alloc2Day", alloc2.getTimeslot().getDayOfWeek().toString());
                    logData.put("alloc2Time", alloc2.getTimeslot().getStartTime().toString());
                    logData.put("teacher", alloc1.getTeacher().getName());
                    logDebug("F", "ScheduleConstraintProvider.sameMajorSubjectSameTeacherDifferentSectionsDifferentDays:JOIN", 
                            "Checking same major subject same teacher different sections", logData);
                    // #endregion
                    
                    if (alloc1.getId().equals(alloc2.getId())) {
                        // #region agent log
                        logDebug("F", "ScheduleConstraintProvider.sameMajorSubjectSameTeacherDifferentSectionsDifferentDays:SAME_ALLOC", 
                                "Same allocation, skipping", Map.of());
                        // #endregion
                        return false;
                    }
                    
                    if (alloc1.getSection() == null || alloc2.getSection() == null) return false;
                    if (alloc1.getTimeslot() == null || alloc2.getTimeslot() == null) return false;
                    
                    // Check if they are different sections
                    boolean differentSections = !alloc1.getSection().getId().equals(alloc2.getSection().getId());
                    
                    if (!differentSections) {
                        // #region agent log
                        logDebug("F", "ScheduleConstraintProvider.sameMajorSubjectSameTeacherDifferentSectionsDifferentDays:SAME_SECTION", 
                                "Same section, skipping", Map.of());
                        // #endregion
                        return false; // Only apply to different sections
                    }
                    
                    // Check if they are on the same day - this is a violation
                    boolean sameDay = alloc1.getTimeslot().getDayOfWeek().equals(alloc2.getTimeslot().getDayOfWeek());
                    
                    // Only penalize if same day (different sections on same day is the main violation)
                    // We'll handle different times separately if needed, but for now focus on same day violation
                    boolean violation = sameDay;
                    
                    // #region agent log
                    Map<String, Object> checkLogData = new HashMap<>();
                    checkLogData.put("differentSections", differentSections);
                    checkLogData.put("sameDay", sameDay);
                    checkLogData.put("violation", violation);
                    logDebug("F", "ScheduleConstraintProvider.sameMajorSubjectSameTeacherDifferentSectionsDifferentDays:CHECK", 
                            "Checking violations", checkLogData);
                    // #endregion
                    
                    if (sameDay) {
                        logger.warn("!!! SAME MAJOR SUBJECT SAME TEACHER DIFFERENT SECTIONS SAME DAY VIOLATION: {} for teacher {} - {} and {} both on {}", 
                                   alloc1.getSubjectCode(), alloc1.getTeacher().getName(),
                                   alloc1.getSection().getSectionName(), alloc2.getSection().getSectionName(),
                                   alloc1.getTimeslot().getDayOfWeek());
                        // #region agent log
                        logDebug("F", "ScheduleConstraintProvider.sameMajorSubjectSameTeacherDifferentSectionsDifferentDays:VIOLATION_SAME_DAY", 
                                "Same day violation detected", Map.of(
                            "alloc1Section", alloc1.getSection().getSectionName(),
                            "alloc2Section", alloc2.getSection().getSectionName(),
                            "day", alloc1.getTimeslot().getDayOfWeek().toString()
                        ));
                        // #endregion
                    }
                    
                    return violation;
                })
                .penalize(HardSoftScore.ONE_HARD, (alloc1, alloc2) -> {
                    logger.warn("!!! PENALIZING SAME MAJOR SUBJECT SAME TEACHER DIFFERENT SECTIONS SAME DAY: {} for teacher {} - {} and {}", 
                               alloc1.getSubjectCode(), alloc1.getTeacher().getName(),
                               alloc1.getSection().getSectionName(), alloc2.getSection().getSectionName());
                    // #region agent log
                    Map<String, Object> penaltyData = new HashMap<>();
                    penaltyData.put("alloc1Id", alloc1.getId());
                    penaltyData.put("alloc1Section", alloc1.getSection() != null ? alloc1.getSection().getSectionName() : "null");
                    penaltyData.put("alloc1Day", alloc1.getTimeslot() != null ? alloc1.getTimeslot().getDayOfWeek().toString() : "null");
                    penaltyData.put("alloc2Id", alloc2.getId());
                    penaltyData.put("alloc2Section", alloc2.getSection() != null ? alloc2.getSection().getSectionName() : "null");
                    penaltyData.put("alloc2Day", alloc2.getTimeslot() != null ? alloc2.getTimeslot().getDayOfWeek().toString() : "null");
                    penaltyData.put("penalty", 1);
                    logDebug("A", "ScheduleConstraintProvider.sameMajorSubjectSameTeacherDifferentSectionsDifferentDays:PENALTY", 
                            "Applying penalty", penaltyData);
                    // #endregion
                    return 1;
                })
                .asConstraint("Same major subject same teacher different sections different days same time");
    }
}