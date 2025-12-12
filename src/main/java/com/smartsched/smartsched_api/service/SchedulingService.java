package com.smartsched.smartsched_api.service;

import com.smartsched.smartsched_api.model.*;
import com.smartsched.smartsched_api.repository.*;
import com.smartsched.smartsched_api.solver.domain.Allocation;
import com.smartsched.smartsched_api.solver.domain.ScheduleSolution;
import com.smartsched.smartsched_api.solver.domain.Timeslot;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class SchedulingService {
    private static final Logger logger = LoggerFactory.getLogger(SchedulingService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    private final SolverManager<ScheduleSolution, String> solverManager;
    private final ConcurrentMap<String, SolverStatus> solverStatusMap = new ConcurrentHashMap<>();
    private final AtomicLong nextAllocationId = new AtomicLong(1);
    
    private final TeacherRepository teacherRepository;
    private final ClassroomRepository classroomRepository;
    private final SectionRepository sectionRepository;
    private final ScheduleRepository scheduleRepository;

    @Autowired
    public SchedulingService(SolverManager<ScheduleSolution, String> solverManager,
                             TeacherRepository teacherRepository,
                             ClassroomRepository classroomRepository, SectionRepository sectionRepository,
                             ScheduleRepository scheduleRepository) {
        this.solverManager = solverManager;
        this.teacherRepository = teacherRepository;
        this.classroomRepository = classroomRepository;
        this.sectionRepository = sectionRepository;
        this.scheduleRepository = scheduleRepository;
    }

     // --- NEW METHOD FOR POLLING (Fix for Bug B) ---
     public SolverStatus getSolverStatus(String problemId) {
        return solverStatusMap.getOrDefault(problemId, SolverStatus.NOT_SOLVING);
     }


    public void solveAndSave(String problemId, String sectionId, List<ScheduleInput> scheduleInputs) {
        logger.info("Received scheduling request for problemId: {} and sectionId: {}", problemId, sectionId);
        solverStatusMap.put(problemId, SolverStatus.SOLVING_SCHEDULED);

        // --- Load ALL Data for Solver ---
        Section sectionToSchedule = sectionRepository.findById(sectionId)
                .orElseThrow(() -> {
                     logger.error("Section with ID {} not found!", sectionId);
                     solverStatusMap.put(problemId, SolverStatus.NOT_SOLVING);
                     return new IllegalArgumentException("Section with ID " + sectionId + " not found.");
                });
        
        List<Teacher> allTeachers = teacherRepository.findAll();
        List<Classroom> allClassrooms = classroomRepository.findAll();
        List<Timeslot> allTimeslots = generateTimeslots();
        
        // --- DEBUG: Log available classrooms and their types ---
        logger.info("@@@ AVAILABLE CLASSROOMS:");
        for (Classroom classroom : allClassrooms) {
            logger.info("@@@ Classroom: {} | Type: {} | Capacity: {}", 
                       classroom.getName(), classroom.getType(), classroom.getCapacity());
        }
        
        // --- FIX FOR OVERLAPS (Bug A) ---
        List<Section> allSections = sectionRepository.findAll();
        List<Schedule> allExistingSchedules = scheduleRepository.findAll();
        // --- END FIX ---

        // Create lookup maps for efficiency
        Map<String, Teacher> teacherMap = allTeachers.stream().collect(Collectors.toMap(Teacher::getId, t -> t, (t1, t2) -> t1));
        Map<String, Classroom> classroomMap = allClassrooms.stream().collect(Collectors.toMap(Classroom::getId, c -> c, (c1, c2) -> c1));
        Map<String, Section> sectionMap = allSections.stream().collect(Collectors.toMap(Section::getId, s -> s, (s1, s2) -> s1));
        Map<String, Timeslot> timeslotMap = allTimeslots.stream().collect(Collectors.toMap(ts -> ts.getDayOfWeek().toString() + "_" + ts.getStartTime().toString(), ts -> ts, (ts1, ts2) -> ts1));

        List<Allocation> allocations = new ArrayList<>();
        logger.info("Creating NEW allocations for {} subjects...", scheduleInputs.size());

        for (ScheduleInput input : scheduleInputs) {
            if (!input.getSectionId().equals(sectionId)) continue;
            Teacher teacher = (input.getTeacherId() != null && !input.getTeacherId().isEmpty()) ? teacherMap.get(input.getTeacherId()) : null;
            if (teacher == null && input.getTeacherId() != null && !input.getTeacherId().isEmpty()) {
                 logger.warn("Teacher ID {} not found for subject {}.", input.getTeacherId(), input.getSubjectCode());
            }

            List<Integer> sessionDurationsInMinutes = determineSessionDurations(input.getClassHoursPerWeek());
            // This log now correctly receives isMajor=true thanks to the fix in ScheduleInput.java
            logger.info(">>> Preparing Allocation for Subject: {} | isMajor from Input: {} | ClassHours: {} | Section Program: {}",
                        input.getSubjectCode(), input.isMajor(), input.getClassHoursPerWeek(),
                        sectionToSchedule != null ? sectionToSchedule.getProgram() : "NULL"); 

            for (int duration : sessionDurationsInMinutes) {
                Allocation newAllocation = new Allocation(
                        nextAllocationId.getAndIncrement(),
                        input.getSubjectCode(), input.getSubjectName(),
                        teacher, sectionToSchedule, duration, input.isMajor(),
                        false // This is a NEW allocation, so it is NOT pinned
                );
                logger.info(">>> Created Allocation: {} | isMajor: {} | Duration: {}min | Section Program: {}",
                           newAllocation.getSubjectCode(), newAllocation.isMajor(), duration,
                           sectionToSchedule != null ? sectionToSchedule.getProgram() : "NULL");
                allocations.add(newAllocation);
            }
        }
        
        // --- CONVERT EXISTING SCHEDULES TO PINNED ALLOCATIONS (Fix for Bug A) ---
        logger.info("Converting {} existing schedules to PINNED allocations...", allExistingSchedules.size());
        int pinnedCount = 0;
        for (Schedule existing : allExistingSchedules) {
            if (existing.getSectionId().equals(sectionId)) {
                logger.info("Skipping existing schedule {} for section {} (it will be deleted)", existing.getId(), sectionId);
                continue;
            }

            Teacher teacher = (existing.getTeacherId() != null) ? teacherMap.get(existing.getTeacherId()) : null;
            Classroom classroom = classroomMap.get(existing.getClassroomId());
            Section section = sectionMap.get(existing.getSectionId());
            LocalTime startTime;
            LocalTime endTime;
            try {
                startTime = LocalTime.parse(existing.getStartTime(), TIME_FORMATTER);
                endTime = LocalTime.parse(existing.getEndTime(), TIME_FORMATTER);
            } catch (Exception e) {
                 logger.error("!!! Failed to parse time for existing schedule {}: '{}' / '{}'", existing.getId(), existing.getStartTime(), existing.getEndTime(), e);
                 continue;
            }
            
            String timeslotKey = existing.getDayOfWeek().toString() + "_" + startTime.toString();
            Timeslot timeslot = timeslotMap.get(timeslotKey);
            int duration = (int) ChronoUnit.MINUTES.between(startTime, endTime);
            if(duration < 0) duration += 1440; // 24 * 60

            if (classroom != null && section != null && timeslot != null && duration > 0) {
                 // Determine if this is a major subject by checking if it requires a lab
                 // For now, we'll need to check the subject code or use a different method
                 // Since we don't have isMajor in Schedule, we'll set it based on classroom type
                 boolean isMajor = classroom.getType() != null && 
                                  (classroom.getType().toLowerCase().contains("lab") || 
                                   classroom.getType().toLowerCase().contains("laboratory"));
                 
                 Allocation pinnedAllocation = new Allocation(
                    nextAllocationId.getAndIncrement(),
                    existing.getSubjectCode(), existing.getSubjectName(),
                    teacher, section, duration,
                    isMajor, // Set isMajor based on classroom type for pinned allocations
                    true // --- THIS IS PINNED ---
                );
                pinnedAllocation.setTimeslot(timeslot);
                pinnedAllocation.setClassroom(classroom);
                allocations.add(pinnedAllocation);
                pinnedCount++;
                logger.info("@@@ PINNED ALLOCATION CREATED: Subject={}, Section={}, Teacher={}, Classroom={}, Day={}, Time={}-{}, Timeslot={}", 
                           existing.getSubjectCode(), section.getSectionName(), 
                           teacher != null ? teacher.getName() : "null",
                           classroom != null ? classroom.getName() : "null",
                           existing.getDayOfWeek(), startTime, endTime, timeslotKey);
            } else {
                 logger.warn("Could not create pinned allocation for existing schedule ID {}. Missing data (Classroom: {}, Section: {}, Timeslot: {} (key: {}), Duration: {})", 
                    existing.getId(), classroom != null, section != null, timeslot != null, timeslotKey, duration);
            }
        }
        logger.info("Created {} pinned allocations from existing schedules.", pinnedCount);
        // --- END PINNED ALLOCATION LOGIC ---

        logger.info("Created {} total allocations (new and pinned).", allocations.size());
        ScheduleSolution problem = new ScheduleSolution(allTimeslots, allClassrooms, allTeachers, allSections, allocations);

        solverManager.solveAndListen(
                problemId,
                id -> problem,
                finalBestSolution -> saveSolution(problemId, finalBestSolution),
                (String failedProblemId, Throwable throwable) -> {
                     logger.error("!!! SOLVING FAILED for problemId: {} !!!", failedProblemId, throwable);
                     solverStatusMap.put(failedProblemId, SolverStatus.NOT_SOLVING);
                     try {
                         logger.warn("Attempting cleanup for failed problemId: {}", failedProblemId);
                         scheduleRepository.deleteByProblemId(failedProblemId);
                         logger.warn("Cleaned up entries for failed problemId: {}", failedProblemId);
                     } catch (Exception e) { logger.error("!!! Cleanup failed for problemId: {} !!!", failedProblemId, e); }
                 }
        );
    }

    @Transactional
    protected void saveSolution(String problemId, ScheduleSolution finalBestSolution) {
         try {
            HardSoftScore finalScore = finalBestSolution.getScore();
            logger.info("!!! Solver finished for problemId: {}. FINAL SCORE received: {} !!!", problemId, finalScore);
            logger.warn("@@@ FINAL SCORE ANALYSIS: Hard score={}, Soft score={}, Is feasible={}", 
                       finalScore.hardScore(), finalScore.softScore(), finalScore.isFeasible());
            
            // Additional validation: manually check for overlaps
            logger.info("@@@ saveSolution: Starting validation...");
            boolean hasOverlaps = validateSolutionForOverlaps(finalBestSolution);
            logger.info("@@@ saveSolution: Validation result: hasOverlaps={}", hasOverlaps);
            if (hasOverlaps) {
                logger.error("!!! ERROR: Manual validation detected OVERLAPS in solution! !!!");
                logger.error("!!! REJECTING SOLUTION - Will not save schedules with overlaps !!!");
                solverStatusMap.put(problemId, SolverStatus.NOT_SOLVING);
                return;
            }
            
            if (!finalScore.isFeasible()) {
                logger.error("!!! ERROR: Solution has HARD CONSTRAINT VIOLATIONS! Hard score: {} !!!", finalScore.hardScore());
                logger.error("!!! REJECTING SOLUTION - Will not save schedules with constraint violations !!!");
                solverStatusMap.put(problemId, SolverStatus.NOT_SOLVING);
                return; // Don't save solutions with hard constraint violations
            }
            solverStatusMap.put(problemId, SolverStatus.NOT_SOLVING);

            // --- THIS IS THE FIX: Declare solvedSectionId BEFORE using it ---
            String solvedSectionId = null;
            for(Allocation alloc : finalBestSolution.getAllocations()) {
                if(!alloc.isPinned()) { // Find the first unpinned allocation
                    if (alloc.getSection() != null) {
                        solvedSectionId = alloc.getSection().getId();
                        break;
                    }
                }
            }
            // --- END FIX ---
            
            if (solvedSectionId == null) {
                 logger.warn("No unpinned allocations found in solution for {}. No schedules updated.", problemId);
                 return;
            }
            
            logger.info("Clearing old schedules for *solved* section: {}", solvedSectionId);
            List<Schedule> oldSchedulesForSection = scheduleRepository.findAllBySectionId(solvedSectionId);
            List<String> oldScheduleIds = oldSchedulesForSection.stream().map(Schedule::getId).collect(Collectors.toList());
            if (!oldSchedulesForSection.isEmpty()) {
                 scheduleRepository.deleteAll(oldSchedulesForSection);
            }

            Map<String, Teacher> teachersToUpdate = new HashMap<>();
            Map<String, Classroom> classroomsToUpdate = new HashMap<>();
            
            for(Schedule oldSchedule : oldSchedulesForSection) {
                if(oldSchedule.getTeacherId() != null) teacherRepository.findById(oldSchedule.getTeacherId()).ifPresent(t -> teachersToUpdate.putIfAbsent(t.getId(), t));
                if(oldSchedule.getClassroomId() != null) classroomRepository.findById(oldSchedule.getClassroomId()).ifPresent(c -> classroomsToUpdate.putIfAbsent(c.getId(), c));
            }
            
            teachersToUpdate.values().forEach(t -> { if(t.getScheduleIds() != null) t.getScheduleIds().removeAll(oldScheduleIds); });
            classroomsToUpdate.values().forEach(c -> { if(c.getScheduleIds() != null) c.getScheduleIds().removeAll(oldScheduleIds); });

            final String finalSolvedSectionId = solvedSectionId; // For use in lambda
            Section sectionToUpdate = sectionRepository.findById(finalSolvedSectionId)
                 .orElseThrow(() -> new IllegalStateException("Section disappeared: " + finalSolvedSectionId));
            sectionToUpdate.setScheduleIds(new ArrayList<>());


            int savedCount = 0;
            for (Allocation allocation : finalBestSolution.getAllocations()) {
                if (allocation.isPinned() || !allocation.getSection().getId().equals(finalSolvedSectionId)) continue;
                if (allocation.getTimeslot() == null || allocation.getClassroom() == null) {
                    logger.warn("Unpinned allocation {} for subject {} could not be scheduled.", allocation.getId(), allocation.getSubjectCode());
                    continue;
                }

                 Timeslot ts = allocation.getTimeslot();
                 LocalTime st = ts.getStartTime();
                 LocalTime et = st.plusMinutes(allocation.getDurationInMinutes());
                 String teacherId = (allocation.getTeacher() != null) ? allocation.getTeacher().getId() : null;

                 Schedule schedule = new Schedule(problemId, allocation.getSubjectCode(), allocation.getSubjectName(), teacherId, allocation.getSection().getId(), allocation.getClassroom().getId(), ts.getDayOfWeek(), st.format(TIME_FORMATTER), et.format(TIME_FORMATTER));
                 
                 Schedule saved = scheduleRepository.save(schedule);
                 String newId = saved.getId();
                 savedCount++;

                 if (teacherId != null) {
                     Teacher t = teachersToUpdate.computeIfAbsent(teacherId, tid -> teacherRepository.findById(tid).get());
                     if (t.getScheduleIds() == null) t.setScheduleIds(new ArrayList<>());
                     t.getScheduleIds().add(newId);
                 }
                 Classroom c = classroomsToUpdate.computeIfAbsent(allocation.getClassroom().getId(), cid -> classroomRepository.findById(cid).get());
                 if (c.getScheduleIds() == null) c.setScheduleIds(new ArrayList<>());
                 c.getScheduleIds().add(newId);
                 sectionToUpdate.getScheduleIds().add(newId);
            }
            logger.info("Saved {} new schedule entries.", savedCount);
            
            if (!teachersToUpdate.isEmpty()) teacherRepository.saveAll(teachersToUpdate.values());
            if (!classroomsToUpdate.isEmpty()) classroomRepository.saveAll(classroomsToUpdate.values());
            sectionRepository.save(sectionToUpdate);
            logger.info("New schedule saved successfully for problemId: {}", problemId);
        } catch (Exception e) {
             logger.error("!!! CRITICAL ERROR SAVING SOLUTION for problemId: {} !!!", problemId, e);
             solverStatusMap.put(problemId, SolverStatus.NOT_SOLVING);
             throw new RuntimeException("Failed to save solution", e);
        }
    }

    // --- OPTIMIZED DURATION LOGIC FOR 2-5 HOUR SUBJECTS ---
    private List<Integer> determineSessionDurations(int totalHoursPerWeek) {
         List<Integer> durations = new ArrayList<>();
         int totalMinutes = totalHoursPerWeek * 60;
         
         if (totalHoursPerWeek <= 0) { 
             logger.warn("Invalid classHoursPerWeek: {}.", totalHoursPerWeek); 
             return durations;
         }
         
         // For 1-hour subjects, single session
         if (totalHoursPerWeek == 1) { 
             durations.add(60); 
             logger.info("Single 1-hour session for {} hours", totalHoursPerWeek);
         }
         // For 2-5 hour subjects, split into 2 sessions of 1.5 hours each
         else if (totalHoursPerWeek >= 2 && totalHoursPerWeek <= 5) {
             // Each session is 1.5 hours (90 minutes)
             int sessionsNeeded = (int) Math.ceil((double) totalHoursPerWeek / 1.5);
             
             for (int i = 0; i < sessionsNeeded; i++) {
                 if (totalMinutes > 90) {
                     durations.add(90); // 1.5 hours
                     totalMinutes -= 90;
                 } else if (totalMinutes > 0) {
                     durations.add(totalMinutes); // Remaining time
                     totalMinutes = 0;
                 }
             }
             
             logger.info("Split {} hours into {} sessions: {}", 
                 totalHoursPerWeek, sessionsNeeded, durations);
         } 
         // For more than 5 hours, split into multiple 1.5-hour sessions
         else {
             int sessionsNeeded = (int) Math.ceil((double) totalHoursPerWeek / 1.5);
             
             for (int i = 0; i < sessionsNeeded; i++) {
                 if (totalMinutes > 90) {
                     durations.add(90); // 1.5 hours
                     totalMinutes -= 90;
                 } else if (totalMinutes > 0) {
                     durations.add(totalMinutes); // Remaining time
                     totalMinutes = 0;
                 }
             }
             
             logger.info("Split {} hours into {} sessions: {}", 
                 totalHoursPerWeek, sessionsNeeded, durations);
         }
         
         return durations;
    }
    
    // --- OPTIMIZED TIMESLOTS (Mon-Sat) with 1.5-hour slots for better scheduling ---
    private List<Timeslot> generateTimeslots() {
        List<Timeslot> timeslots = new ArrayList<>();
        long idCounter = 1;
        
        // Generate timeslots for Monday-Saturday
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)) {
            
            // Morning session: 8:00 AM - 12:30 PM (1.5-hour slots)
            LocalTime morningStart = LocalTime.of(8, 0);
            while (morningStart.isBefore(LocalTime.of(12, 30))) {
                LocalTime morningEnd = morningStart.plusMinutes(90); // 1.5 hours
                if (morningEnd.isAfter(LocalTime.of(12, 30))) {
                    morningEnd = LocalTime.of(12, 30); // Adjust last slot
                }
                timeslots.add(new Timeslot(idCounter++, day, morningStart, morningEnd));
                morningStart = morningStart.plusMinutes(90);
            }
            
            // Afternoon session: 1:00 PM - 8:30 PM (1.5-hour slots)
            LocalTime afternoonStart = LocalTime.of(13, 0);
            while (afternoonStart.isBefore(LocalTime.of(20, 30))) {
                LocalTime afternoonEnd = afternoonStart.plusMinutes(90); // 1.5 hours
                if (afternoonEnd.isAfter(LocalTime.of(20, 30))) {
                    afternoonEnd = LocalTime.of(20, 30); // Adjust last slot
                }
                timeslots.add(new Timeslot(idCounter++, day, afternoonStart, afternoonEnd));
                afternoonStart = afternoonStart.plusMinutes(90);
            }
        }
        
        logger.info("Generated {} optimized timeslots (1.5-hour slots) for Mon-Sat, 8:00-12:30, 13:00-20:30", timeslots.size());
        return timeslots;
    }

    /**
     * Manual validation to check for overlaps and rule violations in the solution
     * This is a safety check in addition to constraint-based validation
     * 
     * Rules checked:
     * 1. Teacher conflicts (same teacher at same time)
     * 2. Classroom conflicts (same classroom at same time)
     * 3. Same major subject + same teacher + different sections must be on different days
     */
    private boolean validateSolutionForOverlaps(ScheduleSolution solution) {
        if (solution.getAllocations() == null) return false;
        
        // Group by teacher and check for overlaps
        Map<String, List<Allocation>> byTeacher = 
            solution.getAllocations().stream()
                .filter(a -> a.getTeacher() != null && a.getTimeslot() != null)
                .collect(Collectors.groupingBy(a -> a.getTeacher().getId()));
        
        for (Map.Entry<String, List<Allocation>> entry : byTeacher.entrySet()) {
            List<Allocation> allocs = entry.getValue();
            if (hasOverlapInList(allocs)) {
                logger.error("!!! VALIDATION: Teacher {} has overlapping allocations!", entry.getKey());
                return true;
            }
        }
        
        // Group by classroom and check for overlaps
        Map<String, List<Allocation>> byClassroom = 
            solution.getAllocations().stream()
                .filter(a -> a.getClassroom() != null && a.getTimeslot() != null)
                .collect(Collectors.groupingBy(a -> a.getClassroom().getId()));
        
        for (Map.Entry<String, List<Allocation>> entry : byClassroom.entrySet()) {
            List<Allocation> allocs = entry.getValue();
            if (hasOverlapInList(allocs)) {
                logger.error("!!! VALIDATION: Classroom {} has overlapping allocations!", entry.getKey());
                return true;
            }
        }
        
        // Check rule: Same major subject + same teacher + different sections must be on different days
        // If violation found, attempt automatic reassignment
        logger.info("@@@ VALIDATION: Checking for same major subject same teacher different sections violations...");
        boolean hasViolation = hasSameMajorSubjectSameTeacherDifferentSectionsSameDay(solution.getAllocations());
        logger.info("@@@ VALIDATION RESULT: hasViolation={}", hasViolation);
        
        if (hasViolation) {
            logger.warn("!!! VALIDATION: Same major subject with same teacher but different sections scheduled on same day!");
            logger.info("!!! Attempting automatic reassignment to fix violation...");
            boolean fixed = fixSameMajorSubjectSameTeacherDifferentSectionsViolations(solution);
            logger.info("@@@ REASSIGNMENT RESULT: fixed={}", fixed);
            if (!fixed) {
                logger.error("!!! ERROR: Could not automatically fix violations!");
                return true; // Still has violations after fix attempt
            } else {
                logger.info("!!! SUCCESS: Violations automatically fixed by reassignment!");
                // Re-validate to ensure no new conflicts were created
                logger.info("@@@ RE-VALIDATING after reassignment...");
                boolean stillHasViolations = validateSolutionForOverlaps(solution);
                logger.info("@@@ RE-VALIDATION RESULT: stillHasViolations={}", stillHasViolations);
                return stillHasViolations;
            }
        }
        
        return false;
    }
    
    /**
     * Automatically fix violations where same major subject + same teacher + different sections are on same day
     * by reassigning one section to a different day with the same time slots
     */
    private boolean fixSameMajorSubjectSameTeacherDifferentSectionsViolations(ScheduleSolution solution) {
        logger.info("@@@ fixSameMajorSubjectSameTeacherDifferentSectionsViolations: ENTRY");
        
        if (solution.getAllocations() == null) {
            logger.error("@@@ fixSameMajorSubjectSameTeacherDifferentSectionsViolations: No allocations!");
            return false;
        }
        
        // Get all timeslots from the solution
        List<Timeslot> allTimeslots = solution.getTimeslots();
        if (allTimeslots == null || allTimeslots.isEmpty()) {
            logger.error("!!! No timeslots available for reassignment!");
            return false;
        }
        
        logger.info("@@@ fixSameMajorSubjectSameTeacherDifferentSectionsViolations: timeslotsCount={}", allTimeslots.size());
        
        // Group by teacher and subject code - include ALL allocations (pinned and unpinned) for detection
        // But we'll only reassign unpinned ones
        Map<String, Map<String, List<Allocation>>> byTeacherAndSubject = 
            solution.getAllocations().stream()
                .filter(a -> a.isMajor() && a.getTeacher() != null && a.getSubjectCode() != null && 
                           a.getSection() != null && a.getTimeslot() != null)
                .collect(Collectors.groupingBy(
                    a -> a.getTeacher().getId(),
                    Collectors.groupingBy(Allocation::getSubjectCode)
                ));
        
        logger.info("@@@ fixSameMajorSubjectSameTeacherDifferentSectionsViolations: teacherSubjectGroups={}", byTeacherAndSubject.size());
        
        boolean anyFixed = false;
        
        for (Map.Entry<String, Map<String, List<Allocation>>> teacherEntry : byTeacherAndSubject.entrySet()) {
            for (Map.Entry<String, List<Allocation>> subjectEntry : teacherEntry.getValue().entrySet()) {
                List<Allocation> allocs = subjectEntry.getValue();
                if (allocs.size() < 2) continue;
                
                // Group by section
                Map<String, List<Allocation>> bySection = allocs.stream()
                    .collect(Collectors.groupingBy(a -> a.getSection().getId()));
                
                if (bySection.size() < 2) continue; // Only one section
                
                // Group by day to find conflicts
                Map<DayOfWeek, List<Allocation>> byDay = allocs.stream()
                    .collect(Collectors.groupingBy(a -> a.getTimeslot().getDayOfWeek()));
                
                logger.info("@@@ fixSameMajorSubjectSameTeacherDifferentSectionsViolations: Grouped by day for subject {} teacher {}", 
                           subjectEntry.getKey(), teacherEntry.getKey());
                
                // Find days with multiple sections
                for (Map.Entry<DayOfWeek, List<Allocation>> dayEntry : byDay.entrySet()) {
                    List<Allocation> dayAllocs = dayEntry.getValue();
                    Set<String> sectionsOnThisDay = dayAllocs.stream()
                        .map(a -> a.getSection().getId())
                        .collect(Collectors.toSet());
                    
                    logger.info("@@@ fixSameMajorSubjectSameTeacherDifferentSectionsViolations: Day {} has sections {}", 
                               dayEntry.getKey(), sectionsOnThisDay);
                    
                    if (sectionsOnThisDay.size() > 1) {
                        // Conflict found - need to reassign one section
                        logger.warn("!!! FIXING: Found conflict for subject {} teacher {} - sections {} on day {}", 
                                   subjectEntry.getKey(), teacherEntry.getKey(), sectionsOnThisDay, dayEntry.getKey());
                        
                        // Get the start time from one of the allocations (they should have same time)
                        LocalTime targetStartTime = dayAllocs.get(0).getTimeslot().getStartTime();
                        logger.info("@@@ Target start time for reassignment: {}", targetStartTime);
                        
                        // Find a different day with the same start time available
                        DayOfWeek newDay = findAvailableDayForReassignment(
                            allTimeslots, dayEntry.getKey(), targetStartTime, 
                            solution.getAllocations(), dayAllocs.get(0).getTeacher().getId());
                        
                        logger.info("@@@ Found new day for reassignment: {}", newDay);
                        
                        if (newDay == null) {
                            logger.error("!!! Could not find available day for reassignment!");
                            return false;
                        }
                        
                        // Reassign all allocations from one section to the new day
                        // Priority: Reassign unpinned allocations (the section being solved)
                        // If all are pinned, try to reassign the second section
                        List<String> sectionList = new ArrayList<>(sectionsOnThisDay);
                        List<Allocation> toReassign = null;
                        String foundSectionId = null;
                        
                        // First, try to find a section with unpinned allocations
                        for (String sectionId : sectionList) {
                            final String currentSectionId = sectionId;
                            List<Allocation> unpinnedForSection = dayAllocs.stream()
                                .filter(a -> a.getSection().getId().equals(currentSectionId) && !a.isPinned())
                                .collect(Collectors.toList());
                            
                            if (!unpinnedForSection.isEmpty()) {
                                foundSectionId = currentSectionId;
                                toReassign = unpinnedForSection;
                                logger.info("@@@ Found unpinned section to reassign: {}", foundSectionId);
                                break;
                            }
                        }
                        
                        // If no unpinned allocations found, try the second section (might be partially unpinned)
                        if (foundSectionId == null && sectionList.size() > 1) {
                            final String secondSectionId = sectionList.get(1);
                            foundSectionId = secondSectionId;
                            toReassign = dayAllocs.stream()
                                .filter(a -> a.getSection().getId().equals(secondSectionId) && !a.isPinned())
                                .collect(Collectors.toList());
                            logger.info("@@@ Trying second section: {}", foundSectionId);
                        }
                        
                        if (foundSectionId == null || toReassign == null || toReassign.isEmpty()) {
                            logger.warn("@@@ No unpinned allocations to reassign. All sections may be pinned.");
                            logger.warn("@@@ Sections on this day: {}", sectionList);
                            logger.warn("@@@ Pinned status: {}", dayAllocs.stream()
                                .collect(Collectors.toMap(
                                    a -> a.getSection().getId(),
                                    Allocation::isPinned,
                                    (v1, v2) -> v1
                                )));
                            continue; // Skip if all are pinned
                        }
                        
                        logger.info("@@@ Reassigning section: {} ({} allocations)", foundSectionId, toReassign.size());
                        
                        // Reassign each allocation
                        for (Allocation alloc : toReassign) {
                            Timeslot newTimeslot = findTimeslotForReassignment(
                                allTimeslots, newDay, targetStartTime, alloc.getDurationInMinutes());
                            
                            if (newTimeslot != null) {
                                logger.info("!!! REASSIGNING: {} for section {} from {} {} to {} {}", 
                                          alloc.getSubjectCode(), alloc.getSection().getSectionName(),
                                          dayEntry.getKey(), targetStartTime, newDay, targetStartTime);
                                alloc.setTimeslot(newTimeslot);
                                anyFixed = true;
                            } else {
                                logger.error("!!! Could not find timeslot for reassignment! Day={}, Time={}", newDay, targetStartTime);
                                return false;
                            }
                        }
                    }
                }
            }
        }
        
        logger.info("@@@ fixSameMajorSubjectSameTeacherDifferentSectionsViolations: EXIT, anyFixed={}", anyFixed);
        return anyFixed;
    }
    
    /**
     * Find an available day for reassignment that has the same start time available
     */
    private DayOfWeek findAvailableDayForReassignment(List<Timeslot> allTimeslots, DayOfWeek currentDay, 
                                                       LocalTime startTime, List<Allocation> allAllocations, 
                                                       String teacherId) {
        // Get all days except the current day
        List<DayOfWeek> availableDays = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, 
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        ).stream()
            .filter(day -> !day.equals(currentDay))
            .collect(Collectors.toList());
        
        // Check each day to see if the start time is available for this teacher
        for (DayOfWeek day : availableDays) {
            // Check if there's a timeslot with this start time on this day
            boolean hasTimeslot = allTimeslots.stream()
                .anyMatch(ts -> ts.getDayOfWeek().equals(day) && ts.getStartTime().equals(startTime));
            
            if (!hasTimeslot) continue;
            
            // Check if the teacher is already scheduled at this time on this day
            boolean teacherAvailable = allAllocations.stream()
                .filter(a -> a.getTeacher() != null && a.getTeacher().getId().equals(teacherId))
                .filter(a -> a.getTimeslot() != null && a.getTimeslot().getDayOfWeek().equals(day))
                .noneMatch(a -> {
                    LocalTime allocStart = a.getTimeslot().getStartTime();
                    LocalTime allocEnd = allocStart.plusMinutes(a.getDurationInMinutes());
                    // Check if there's an overlap
                    return (startTime.isBefore(allocEnd) || startTime.equals(allocStart)) && 
                           (allocStart.isBefore(startTime.plusMinutes(90)) || allocStart.equals(startTime));
                });
            
            if (teacherAvailable) {
                logger.info("!!! Found available day: {} for reassignment", day);
                return day;
            }
        }
        
        return null;
    }
    
    /**
     * Find a timeslot for reassignment with the specified day and start time
     */
    private Timeslot findTimeslotForReassignment(List<Timeslot> allTimeslots, DayOfWeek day, 
                                                  LocalTime startTime, int duration) {
        return allTimeslots.stream()
            .filter(ts -> ts.getDayOfWeek().equals(day) && ts.getStartTime().equals(startTime))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Check if same major subject with same teacher but different sections violate scheduling rules
     * Rules:
     * 1. Different sections must be on different days
     * 2. Different sections should be at the same time slots (e.g., both at 08:00-11:00 but on different days)
     * This ensures proper scheduling distribution and consistency
     */
    private boolean hasSameMajorSubjectSameTeacherDifferentSectionsSameDay(List<Allocation> allocations) {
        if (allocations == null || allocations.size() < 2) return false;
        
        // Group by teacher and subject code
        Map<String, Map<String, List<Allocation>>> byTeacherAndSubject = 
            allocations.stream()
                .filter(a -> a.isMajor() && a.getTeacher() != null && a.getSubjectCode() != null && 
                           a.getSection() != null && a.getTimeslot() != null)
                .collect(Collectors.groupingBy(
                    a -> a.getTeacher().getId(),
                    Collectors.groupingBy(Allocation::getSubjectCode)
                ));
        
        for (Map.Entry<String, Map<String, List<Allocation>>> teacherEntry : byTeacherAndSubject.entrySet()) {
            for (Map.Entry<String, List<Allocation>> subjectEntry : teacherEntry.getValue().entrySet()) {
                List<Allocation> allocs = subjectEntry.getValue();
                if (allocs.size() < 2) continue;
                
                // Check if there are different sections
                Set<String> sectionIds = allocs.stream()
                    .map(a -> a.getSection().getId())
                    .collect(Collectors.toSet());
                
                if (sectionIds.size() < 2) continue; // Only one section, skip
                
                // Group by day
                Map<DayOfWeek, List<Allocation>> byDay = allocs.stream()
                    .collect(Collectors.groupingBy(a -> a.getTimeslot().getDayOfWeek()));
                
                // Check if different sections are on the same day (violation)
                for (Map.Entry<DayOfWeek, List<Allocation>> dayEntry : byDay.entrySet()) {
                    List<Allocation> dayAllocs = dayEntry.getValue();
                    Set<String> sectionsOnThisDay = dayAllocs.stream()
                        .map(a -> a.getSection().getId())
                        .collect(Collectors.toSet());
                    
                    if (sectionsOnThisDay.size() > 1) {
                        logger.error("!!! VALIDATION: Subject {} for teacher {} has different sections ({}) scheduled on same day {}", 
                                   subjectEntry.getKey(), teacherEntry.getKey(), sectionsOnThisDay, dayEntry.getKey());
                        return true;
                    }
                }
                
                // Check if different sections have different start times (violation - should be same time on different days)
                Map<String, LocalTime> sectionStartTimes = new HashMap<>();
                for (Allocation alloc : allocs) {
                    String sectionId = alloc.getSection().getId();
                    LocalTime startTime = alloc.getTimeslot().getStartTime();
                    
                    if (sectionStartTimes.containsKey(sectionId)) {
                        // Multiple sessions for same section - check if they're sequential
                        LocalTime existingTime = sectionStartTimes.get(sectionId);
                        if (!existingTime.equals(startTime)) {
                            // Different times for same section - this is OK if sequential, but we check consistency
                            // For now, we'll allow multiple times per section (sequential sessions)
                            continue;
                        }
                    } else {
                        sectionStartTimes.put(sectionId, startTime);
                    }
                }
                
                // Check if different sections have different start times
                Set<LocalTime> uniqueStartTimes = new java.util.HashSet<>(sectionStartTimes.values());
                if (uniqueStartTimes.size() > 1 && sectionStartTimes.size() > 1) {
                    logger.error("!!! VALIDATION: Subject {} for teacher {} has different sections with different start times: {}", 
                               subjectEntry.getKey(), teacherEntry.getKey(), sectionStartTimes);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean hasOverlapInList(List<Allocation> allocations) {
        if (allocations.size() <= 1) return false;
        
        // Group by day
        Map<DayOfWeek, List<Allocation>> byDay = 
            allocations.stream()
                .filter(a -> a.getTimeslot() != null && a.getTimeslot().getDayOfWeek() != null)
                .collect(Collectors.groupingBy(a -> a.getTimeslot().getDayOfWeek()));
        
        for (Map.Entry<DayOfWeek, List<Allocation>> dayEntry : byDay.entrySet()) {
            List<Allocation> dayAllocs = dayEntry.getValue();
            if (dayAllocs.size() <= 1) continue;
            
            // Sort by start time
            dayAllocs.sort((a, b) -> a.getTimeslot().getStartTime().compareTo(b.getTimeslot().getStartTime()));
            
            // Check adjacent pairs
            for (int i = 0; i < dayAllocs.size() - 1; i++) {
                Allocation current = dayAllocs.get(i);
                Allocation next = dayAllocs.get(i + 1);
                
                LocalTime currentStart = current.getTimeslot().getStartTime();
                LocalTime currentEnd = currentStart.plusMinutes(current.getDurationInMinutes());
                LocalTime nextStart = next.getTimeslot().getStartTime();
                
                // Check for overlap
                if (nextStart.isBefore(currentEnd) || nextStart.equals(currentStart)) {
                    logger.error("!!! VALIDATION OVERLAP: {} ({} {}-{}) overlaps with {} ({} {}-{})", 
                               current.getSubjectCode(), dayEntry.getKey(), currentStart, currentEnd,
                               next.getSubjectCode(), dayEntry.getKey(), nextStart, 
                               nextStart.plusMinutes(next.getDurationInMinutes()));
                    return true;
                }
            }
        }
        
        return false;
    }
}