package com.smartsched.smartsched_api.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.smartsched.smartsched_api.model.Classroom;
import com.smartsched.smartsched_api.model.Schedule;
import com.smartsched.smartsched_api.model.Section;
import com.smartsched.smartsched_api.model.Teacher;
import com.smartsched.smartsched_api.repository.ClassroomRepository;
import com.smartsched.smartsched_api.repository.ScheduleRepository;
import com.smartsched.smartsched_api.repository.SectionRepository;
import com.smartsched.smartsched_api.repository.TeacherRepository;

@Service
public class ExcelExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelExportService.class);

    private final ScheduleRepository scheduleRepository;
    private final TeacherRepository teacherRepository;
    private final ClassroomRepository classroomRepository;
    private final SectionRepository sectionRepository; // Added for filename generation

    // Define the desired order of days
    private static final List<DayOfWeek> DAYS_ORDER = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    );

    public ExcelExportService(ScheduleRepository scheduleRepository, TeacherRepository teacherRepository,
                              ClassroomRepository classroomRepository, SectionRepository sectionRepository) {
        this.scheduleRepository = scheduleRepository;
        this.teacherRepository = teacherRepository;
        this.classroomRepository = classroomRepository;
        this.sectionRepository = sectionRepository; // Inject SectionRepository
    }

    public String getExcelFilename(String sectionId) {
        // Try to find the section to make the filename more descriptive
        Optional<Section> sectionOpt = sectionRepository.findById(sectionId);
        if (sectionOpt.isPresent()) {
            Section section = sectionOpt.get();
            // Sanitize program, year, section name for filename
            String program = section.getProgram().replaceAll("[^a-zA-Z0-9\\-_]", ""); // Allow underscore and hyphen
            String year = String.valueOf(section.getYearLevel());
            String name = section.getSectionName().replaceAll("[^a-zA-Z0-9\\-_]", "");
            return String.format("Schedule_%s_%s-%s.xls", program, year, name); // Use hyphen
        } else {
            // Fallback filename if section not found
            logger.warn("Section with ID {} not found when generating filename. Using default.", sectionId);
            // Sanitize sectionId as well
            String safeSectionId = sectionId.replaceAll("[^a-zA-Z0-9\\-_]", "");
            return String.format("Schedule_Section_%s.xls", safeSectionId);
        }
    }


    public ByteArrayInputStream generateScheduleExcel(String sectionId) throws IOException {
        logger.info("Generating Excel schedule for section ID: {}", sectionId);

        List<Schedule> schedules = scheduleRepository.findAllBySectionId(sectionId);
        if (schedules.isEmpty()) {
            logger.warn("No schedules found for section ID: {}", sectionId);
            // Consider throwing an exception or returning an empty Excel file
             throw new IllegalArgumentException("No schedules found for section ID: " + sectionId);
           // return createEmptyExcel("No Schedule Found"); // Alternative: return empty file
        }

        // Fetch related data efficiently
        Set<String> teacherIds = schedules.stream().map(Schedule::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> classroomIds = schedules.stream().map(Schedule::getClassroomId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<String, String> teacherMap = teacherRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Teacher::getName));
        Map<String, String> classroomMap = classroomRepository.findAllById(classroomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, Classroom::getName));

        // Get Section Info for Header
        Optional<Section> sectionOpt = sectionRepository.findById(sectionId);
        String sectionHeader = sectionOpt
                .map(s -> String.format("%s %d-%s Schedule", s.getProgram(), s.getYearLevel(), s.getSectionName()))
                .orElse("Schedule for Section " + sectionId);


        try (Workbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Schedule");
            sheet.setDefaultColumnWidth(20); // Set a default column width

            // --- Create Styles ---
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle centeredStyle = createCenteredStyle(workbook);
            CellStyle wrappedStyle = createWrappedStyle(workbook); // For subject names
            CellStyle timeStyle = createTimeStyle(workbook); // Centered time

             // --- Add Section Header Row ---
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(sectionHeader);
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleCell.setCellStyle(titleStyle);
            // Merge cells for the title (adjust column count if needed)
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));


            // --- Create Header Row ---
            Row headerRow = sheet.createRow(2); // Start header at row 2
            String[] columns = {"Day", "Time", "Subject Code", "Subject Name", "Teacher", "Classroom"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // --- Populate Data ---
            int rowIdx = 3; // Start data at row 3

            // Sort schedules by day according to DAYS_ORDER, then by start time
            schedules.sort(Comparator
                .<Schedule, Integer>comparing(s -> DAYS_ORDER.indexOf(s.getDayOfWeek()))
                .thenComparing(s -> {
                    try {
                        // Attempt to parse time for proper sorting
                        return Date.parse("01/01/1970 " + s.getStartTime());
                    } catch (Exception e) {
                        logger.warn("Could not parse start time for sorting: {}", s.getStartTime());
                        return 0L; // Fallback
                    }
                })
            );

            for (Schedule schedule : schedules) {
                Row row = sheet.createRow(rowIdx++);

                // Day (Bold)
                Cell dayCell = row.createCell(0);
                dayCell.setCellValue(schedule.getDayOfWeek().toString());
                dayCell.setCellStyle(boldStyle);

                // Time (Centered)
                Cell timeCell = row.createCell(1);
                timeCell.setCellValue(String.format("%s - %s", schedule.getStartTime(), schedule.getEndTime()));
                timeCell.setCellStyle(timeStyle); // Use centered time style

                // Subject Code
                 row.createCell(2).setCellValue(schedule.getSubjectCode());

                 // Subject Name (Wrapped)
                 Cell subjectNameCell = row.createCell(3);
                 subjectNameCell.setCellValue(schedule.getSubjectName());
                 subjectNameCell.setCellStyle(wrappedStyle);

                // Teacher
                row.createCell(4).setCellValue(teacherMap.getOrDefault(schedule.getTeacherId(), "N/A"));

                // Classroom
                row.createCell(5).setCellValue(classroomMap.getOrDefault(schedule.getClassroomId(), "N/A"));
            }

            // Auto-size columns (optional, can sometimes be imperfect with wrapped text)
            // for (int i = 0; i < columns.length; i++) {
            //     sheet.autoSizeColumn(i);
            // }

            workbook.write(out);
            logger.info("Excel file generated successfully for section ID: {}", sectionId);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            logger.error("Error generating Excel for section ID {}: {}", sectionId, e.getMessage());
            throw e; // Re-throw exception to be handled by controller
        }
    }

     // Helper method to create header cell style
     private CellStyle createHeaderStyle(Workbook workbook) {
         CellStyle style = workbook.createCellStyle();
         Font font = workbook.createFont();
         font.setBold(true);
         font.setColor(IndexedColors.WHITE.getIndex());
         style.setFont(font);
         style.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex()); // Or your preferred color
         style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
         style.setAlignment(HorizontalAlignment.CENTER);
         style.setVerticalAlignment(VerticalAlignment.CENTER);
         setBorder(style, BorderStyle.THIN);
         return style;
     }

     // Helper method to create bold cell style
     private CellStyle createBoldStyle(Workbook workbook) {
         CellStyle style = workbook.createCellStyle();
         Font font = workbook.createFont();
         font.setBold(true);
         style.setFont(font);
         setBorder(style, BorderStyle.THIN);
         return style;
     }

      // Helper method to create centered cell style
     private CellStyle createCenteredStyle(Workbook workbook) {
         CellStyle style = workbook.createCellStyle();
         style.setAlignment(HorizontalAlignment.CENTER);
         style.setVerticalAlignment(VerticalAlignment.CENTER);
          setBorder(style, BorderStyle.THIN);
         return style;
     }

      // Helper method to create wrapped text style
     private CellStyle createWrappedStyle(Workbook workbook) {
         CellStyle style = workbook.createCellStyle();
         style.setWrapText(true);
         style.setVerticalAlignment(VerticalAlignment.TOP); // Align wrapped text to top
          setBorder(style, BorderStyle.THIN);
         return style;
     }

      // Helper method to create centered time style
     private CellStyle createTimeStyle(Workbook workbook) {
         CellStyle style = workbook.createCellStyle();
         style.setAlignment(HorizontalAlignment.CENTER);
         style.setVerticalAlignment(VerticalAlignment.CENTER);
          setBorder(style, BorderStyle.THIN);
         return style;
     }

      // Helper to set borders on all sides
     private void setBorder(CellStyle style, BorderStyle borderStyle) {
         style.setBorderBottom(borderStyle);
         style.setBorderTop(borderStyle);
         style.setBorderLeft(borderStyle);
         style.setBorderRight(borderStyle);
     }


    // Optional: Helper to create an empty Excel file if no schedule exists
    private ByteArrayInputStream createEmptyExcel(String message) throws IOException {
         try (Workbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
             Sheet sheet = workbook.createSheet("Schedule");
             Row row = sheet.createRow(0);
             row.createCell(0).setCellValue(message);
             workbook.write(out);
             return new ByteArrayInputStream(out.toByteArray());
         }
    }
}

