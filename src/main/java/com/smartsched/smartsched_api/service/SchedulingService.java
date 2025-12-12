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
            boolean hasOverlaps = validateSolutionForOverlaps(finalBestSolution);
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
     * Manual validation to check for overlaps in the solution
     * This is a safety check in addition to constraint-based validation
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