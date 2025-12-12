package com.smartsched.smartsched_api.solver;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

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
                optimizeClassroomUtilization(constraintFactory)
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
            
            // Check for overlaps within this day
            for (int i = 0; i < dayAllocations.size() - 1; i++) {
                Allocation current = dayAllocations.get(i);
                Allocation next = dayAllocations.get(i + 1);
                
                try {
                    LocalTime currentStart = current.getTimeslot().getStartTime();
                    LocalTime currentEnd = currentStart.plusMinutes(current.getDurationInMinutes());
                    LocalTime nextStart = next.getTimeslot().getStartTime();
                    
                    // Check if there's an overlap: next starts before current ends (on the same day)
                    if (nextStart.isBefore(currentEnd)) {
                        logger.warn("!!! OVERLAP DETECTED: {} ({} {} - {}) overlaps with {} ({} {} - {})", 
                                   current.getSubjectCode(), current.getTimeslot().getDayOfWeek(), currentStart, currentEnd,
                                   next.getSubjectCode(), next.getTimeslot().getDayOfWeek(), nextStart, nextStart.plusMinutes(next.getDurationInMinutes()));
                        return true;
                    }
                } catch (Exception e) {
                    logger.error("Error calculating overlap for allocations: {}", e.getMessage());
                }
            }
        }
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
                        
                        // If next starts before current ends (on the same day), there's an overlap
                        if (nextStart.isBefore(currentEnd)) {
                            overlapCount++;
                            logger.warn("!!! PENALIZING Overlap: {} ({} {} - {}) overlaps with {} ({} {} - {})", 
                                       current.getSubjectCode(), current.getTimeslot().getDayOfWeek(), currentStart, currentEnd,
                                       next.getSubjectCode(), next.getTimeslot().getDayOfWeek(), nextStart, nextStart.plusMinutes(next.getDurationInMinutes()));
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
                .filter((teacher, allocs) -> hasOverlap(allocs))
                .penalize(HardSoftScore.ONE_HARD, (teacher, allocs) -> calculateOverlapPenalty(allocs))
                .asConstraint("Teacher conflict");
    }

    private Constraint classroomConflict(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> alloc.getClassroom() != null && alloc.getTimeslot() != null)
                .groupBy(Allocation::getClassroom, ConstraintCollectors.toList())
                .filter((classroom, allocs) -> hasOverlap(allocs))
                .penalize(HardSoftScore.ONE_HARD, (classroom, allocs) -> calculateOverlapPenalty(allocs))
                .asConstraint("Classroom conflict");
    }

    private Constraint sectionConflict(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> alloc.getSection() != null && alloc.getTimeslot() != null)
                .groupBy(Allocation::getSection, ConstraintCollectors.toList())
                .filter((section, allocs) -> hasOverlap(allocs))
                .penalize(HardSoftScore.ONE_HARD, (section, allocs) -> calculateOverlapPenalty(allocs))
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
                    // Only penalize if they are different allocations
                    if (alloc1.getId().equals(alloc2.getId())) return false;
                    
                    // Check if they have the same teacher, classroom, or section
                    boolean sameTeacher = alloc1.getTeacher() != null && alloc2.getTeacher() != null && 
                                        alloc1.getTeacher().getId().equals(alloc2.getTeacher().getId());
                    boolean sameClassroom = alloc1.getClassroom() != null && alloc2.getClassroom() != null && 
                                          alloc1.getClassroom().getId().equals(alloc2.getClassroom().getId());
                    boolean sameSection = alloc1.getSection() != null && alloc2.getSection() != null && 
                                        alloc1.getSection().getId().equals(alloc2.getSection().getId());
                    
                    if (sameTeacher || sameClassroom || sameSection) {
                        logger.warn("!!! EXACT TIME CONFLICT: {} and {} at same time {} on {}", 
                                   alloc1.getSubjectCode(), alloc2.getSubjectCode(), 
                                   alloc1.getTimeslot().getStartTime(), alloc1.getTimeslot().getDayOfWeek());
                        return true;
                    }
                    return false;
                })
                .penalize(HardSoftScore.ONE_HARD, (alloc1, alloc2) -> 1)
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
     */
    private Constraint forceMajorSequential(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Allocation.class)
                .filter(alloc -> !alloc.isPinned() && alloc.isMajor() && alloc.getTimeslot() != null)
                .join(Allocation.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(Allocation::getSubjectCode),
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(a -> a.getTimeslot().getDayOfWeek()))
                .filter((alloc1, alloc2) -> {
                    if (alloc1.getId().equals(alloc2.getId())) return false;
                    if (alloc1.getTimeslot() == null || alloc2.getTimeslot() == null) return false;
                    
                    LocalTime endTime1 = alloc1.getTimeslot().getStartTime().plusMinutes(alloc1.getDurationInMinutes());
                    boolean sequential = endTime1.equals(alloc2.getTimeslot().getStartTime());
                    return !sequential; // Penalize if not sequential
                })
                .penalize(HardSoftScore.of(300, 0), (alloc1, alloc2) -> {
                    logger.warn("@@@ MAJOR NON-SEQUENTIAL PENALTY: {} at {} vs {} on {}", 
                               alloc1.getSubjectCode(), 
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
}