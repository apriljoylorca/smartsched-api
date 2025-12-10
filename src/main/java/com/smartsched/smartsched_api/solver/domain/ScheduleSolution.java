package com.smartsched.smartsched_api.solver.domain;

import java.util.List;

import com.smartsched.smartsched_api.model.Classroom;
import com.smartsched.smartsched_api.model.Section;
import com.smartsched.smartsched_api.model.Teacher;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This is the Planning Solution. It holds all the data required by the solver.
 */
@PlanningSolution
@Data
@NoArgsConstructor
public class ScheduleSolution {

    // Problem Facts (the data that doesn't change)
    // We add @ValueRangeProvider to make these lists available to the @PlanningVariable fields.
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "timeslots")
    private List<Timeslot> timeslots;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "classrooms")
    private List<Classroom> classrooms;

    @ProblemFactCollectionProperty
    private List<Teacher> teachers;
    
    @ProblemFactCollectionProperty
    private List<Section> sections;

    // Planning Entities (the list of things the solver needs to schedule)
    @PlanningEntityCollectionProperty
    private List<Allocation> allocations;

    @PlanningScore
    private HardSoftScore score;

    public ScheduleSolution(List<Timeslot> timeslots, List<Classroom> classrooms, List<Teacher> teachers, List<Section> sections, List<Allocation> allocations) {
        this.timeslots = timeslots;
        this.classrooms = classrooms;
        this.teachers = teachers;
        this.sections = sections;
        this.allocations = allocations;
    }
}

