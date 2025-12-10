package com.smartsched.smartsched_api.controller;

import ai.timefold.solver.core.api.solver.SolverStatus;
import com.smartsched.smartsched_api.model.Schedule;
import com.smartsched.smartsched_api.model.ScheduleInput;
import com.smartsched.smartsched_api.repository.ScheduleRepository;
import com.smartsched.smartsched_api.service.ExcelExportService; // Import Excel Service
import com.smartsched.smartsched_api.service.SchedulingService;

import jakarta.servlet.http.HttpServletResponse; // Import HttpServletResponse
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Import for method security
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream; // Import ByteArrayInputStream
import java.io.IOException; // Import IOException
import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleController.class);

    private final SchedulingService schedulingService;
    private final ScheduleRepository scheduleRepository;
    private final ExcelExportService excelExportService; // Inject Excel Service

    public ScheduleController(SchedulingService schedulingService, ScheduleRepository scheduleRepository, ExcelExportService excelExportService) {
        this.schedulingService = schedulingService;
        this.scheduleRepository = scheduleRepository;
        this.excelExportService = excelExportService; // Initialize Excel Service
    }

    @PostMapping("/solve")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')") // Both roles can generate
    public ResponseEntity<?> solveSchedule(@RequestBody List<ScheduleInput> inputs) {
        logger.info(">>> Received /solve request.");
        if (inputs == null || inputs.isEmpty()) {
            logger.warn(">>> Payload is null or empty!");
            return ResponseEntity.badRequest().body(Map.of("message", "Input list cannot be empty."));
        }

        String sectionId = inputs.get(0).getSectionId(); // Assuming all inputs are for the same section
        if (sectionId == null || sectionId.trim().isEmpty()) {
             logger.error(">>> Section ID is missing in the first input object!");
            return ResponseEntity.badRequest().body(Map.of("message", "Section ID is missing in the input."));
        }

        String problemId = UUID.randomUUID().toString();
        logger.info(">>> Submitting job to SchedulingService with problemId: {}, sectionId: {}", problemId, sectionId);

        try {
            schedulingService.solveAndSave(problemId, sectionId, inputs);
            return ResponseEntity.ok(Map.of(
                "message", "Scheduling process started.",
                "problemId", problemId
            ));
        } catch (IllegalArgumentException e) {
             logger.error(">>> Error during submission to SchedulingService: {}", e.getMessage());
             return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
             logger.error(">>> Unexpected error during submission to SchedulingService:", e);
             return ResponseEntity.internalServerError().body(Map.of("message", "An unexpected error occurred."));
        }
    }

    @GetMapping("/status/{problemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')") // Both roles can check status
    public ResponseEntity<?> getSolverStatus(@PathVariable String problemId) {
        logger.debug(">>> Received status check request for problemId: {}", problemId);
        try {
            SolverStatus status = schedulingService.getSolverStatus(problemId);
            return ResponseEntity.ok(Map.of("problemId", problemId, "status", status.name()));
        } catch (Exception e) {
             logger.error(">>> Error checking status for problemId {}:", problemId, e);
             return ResponseEntity.internalServerError().body(Map.of("message", "Error checking solver status."));
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')") // Both roles can view all schedules (filtered on frontend)
    public ResponseEntity<List<Schedule>> getAllSchedules() {
        logger.info(">>> Received /all request.");
        try {
            List<Schedule> schedules = scheduleRepository.findAll();
            return ResponseEntity.ok(schedules);
        } catch (Exception e) {
            logger.error(">>> Error fetching all schedules:", e);
            // Consider returning an error response instead of null body for better frontend handling
            return ResponseEntity.internalServerError().build();
        }
    }

    // --- NEW: Endpoint to export schedule for a specific section ---
    @GetMapping("/export/section/{sectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')") // Both roles can export
    public void exportSchedule(@PathVariable String sectionId, HttpServletResponse response) throws IOException {
        logger.info(">>> Received request to export schedule for section ID: {}", sectionId);
        try {
            ByteArrayInputStream bis = excelExportService.generateScheduleExcel(sectionId);

            // --- Filename Logic ---
            // Try to get section details to create a more descriptive filename
            String filename = excelExportService.getExcelFilename(sectionId);

            response.setContentType("application/vnd.ms-excel");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

            // --- FIX: Use Java's built-in transferTo ---
            // Replace: org.apache.commons.io.IOUtils.copy(bis, response.getOutputStream());
            bis.transferTo(response.getOutputStream());
            // --- End Fix ---

            response.flushBuffer();
            logger.info(">>> Successfully exported schedule for section ID: {}", sectionId);

        } catch (IllegalArgumentException e) {
            logger.error(">>> Error during export (Bad Request): {}", e.getMessage());
            // Set response status to 400 or 404 depending on the specific error
             if (e.getMessage().contains("not found")) {
                 response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
             } else {
                 response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
             }
        } catch (IOException e) {
            logger.error(">>> Error writing excel file to response for section ID {}:", sectionId, e);
             response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating Excel file.");
        } catch (Exception e) {
            logger.error(">>> Unexpected error during schedule export for section ID {}:", sectionId, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An unexpected error occurred during export.");
        }
    }

    // --- NEW: Endpoint for Admin to delete a generated schedule by Problem ID ---
    @DeleteMapping("/problem/{problemId}")
    @PreAuthorize("hasRole('ADMIN')") // ONLY Admin can delete
    public ResponseEntity<?> deleteScheduleByProblemId(@PathVariable String problemId) {
        logger.warn(">>> Received request to DELETE schedule for problemId: {}", problemId);
        try {
            long deletedCount = scheduleRepository.deleteByProblemId(problemId);
            if (deletedCount > 0) {
                logger.warn(">>> Successfully deleted {} schedule entries for problemId: {}", deletedCount, problemId);
                return ResponseEntity.ok(Map.of("message", "Schedule deleted successfully.", "deletedCount", deletedCount));
            } else {
                logger.warn(">>> No schedule entries found for problemId: {}. Nothing deleted.", problemId);
                return ResponseEntity.status(404).body(Map.of("message", "No schedule found for the given problem ID."));
            }
        } catch (Exception e) {
            logger.error(">>> Error deleting schedule for problemId {}:", problemId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "An error occurred while deleting the schedule."));
        }
    }
}

