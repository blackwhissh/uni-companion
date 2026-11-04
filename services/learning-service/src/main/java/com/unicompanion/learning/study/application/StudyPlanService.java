package com.unicompanion.learning.study.application;

import com.unicompanion.learning.study.infrastructure.StudyPlanEntity;
import com.unicompanion.learning.study.infrastructure.StudyPlanKey;
import com.unicompanion.learning.study.infrastructure.StudyPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class StudyPlanService {

    private final ProgressService progress;
    private final StudyPlanRepository plans;
    private final Clock clock;

    public StudyPlanService(ProgressService progress, StudyPlanRepository plans, Clock clock) {
        this.progress = progress;
        this.plans = plans;
        this.clock = clock;
    }

    @Transactional
    public StudyPlan.Result save(UUID courseId, UUID userId, LocalDate examDate) {
        ProgressSummary.Result scores = progress.view(courseId, userId);
        LocalDate today = LocalDate.now(clock);
        StudyPlan.Result plan;
        try {
            plan = StudyPlan.build(today, examDate, scores);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        if (examDate.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an exam date from today onward.");
        }
        StudyPlanKey key = new StudyPlanKey(courseId, userId);
        StudyPlanEntity entity = plans.findById(key)
                .orElseGet(() -> StudyPlanEntity.create(key, examDate, clock.instant()));
        entity.reschedule(examDate, clock.instant());
        plans.save(entity);
        return plan;
    }

    @Transactional(readOnly = true)
    public StudyPlan.Result view(UUID courseId, UUID userId) {
        ProgressSummary.Result scores = progress.view(courseId, userId);
        StudyPlanEntity entity = plans.findById(new StudyPlanKey(courseId, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return StudyPlan.build(LocalDate.now(clock), entity.getExamDate(), scores);
    }
}
