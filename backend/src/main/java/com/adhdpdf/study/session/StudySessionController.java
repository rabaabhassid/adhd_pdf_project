package com.adhdpdf.study.session;

import com.adhdpdf.study.auth.CurrentUserService;
import com.adhdpdf.study.llm.SummarizationFailureReason;
import com.adhdpdf.study.profile.AppUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/session")
public class StudySessionController {

    private static final String SUMMARY_UNAVAILABLE = "Summary unavailable";

    private final StudySessionStore studySessionStore;
    private final CurrentUserService currentUserService;

    public StudySessionController(StudySessionStore studySessionStore, CurrentUserService currentUserService) {
        this.studySessionStore = studySessionStore;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{id}/current")
    public ResponseEntity<SessionSectionView> current(@PathVariable String id) {
        AppUser user = currentUserService.requireUser();
        Optional<StudySession> session = studySessionStore.findByIdForUser(id, user);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StudySession s = session.get();
        synchronized (s) {
            return ResponseEntity.ok(toView(s));
        }
    }

    @PostMapping("/{id}/next")
    public ResponseEntity<SessionSectionView> next(@PathVariable String id) {
        AppUser user = currentUserService.requireUser();
        Optional<StudySession> session = studySessionStore.findByIdForUser(id, user);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StudySession s = session.get();
        synchronized (s) {
            List<StudySection> sections = s.getSections();
            int n = sections.size();
            if (n > 0) {
                int idx = s.getCurrentIndex();
                if (idx < n - 1) {
                    s.setCurrentIndex(idx + 1);
                    studySessionStore.updateCurrentIndexForUser(id, user, s.getCurrentIndex());
                }
            }
            return ResponseEntity.ok(toView(s));
        }
    }

    @PostMapping("/{id}/previous")
    public ResponseEntity<SessionSectionView> previous(@PathVariable String id) {
        AppUser user = currentUserService.requireUser();
        Optional<StudySession> session = studySessionStore.findByIdForUser(id, user);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StudySession s = session.get();
        synchronized (s) {
            int idx = s.getCurrentIndex();
            if (idx > 0) {
                s.setCurrentIndex(idx - 1);
                studySessionStore.updateCurrentIndexForUser(id, user, s.getCurrentIndex());
            }
            return ResponseEntity.ok(toView(s));
        }
    }

    @PostMapping("/{id}/sections/{sectionIndex}/open")
    public ResponseEntity<SessionSectionView> openSection(
            @PathVariable String id, @PathVariable int sectionIndex) {
        AppUser user = currentUserService.requireUser();
        Optional<StudySession> session = studySessionStore.findByIdForUser(id, user);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StudySession s = session.get();
        synchronized (s) {
            try {
                s.setCurrentIndex(sectionIndex);
            } catch (IndexOutOfBoundsException ex) {
                return ResponseEntity.badRequest().build();
            }
            studySessionStore.updateCurrentIndexForUser(id, user, s.getCurrentIndex());
            return ResponseEntity.ok(toView(s));
        }
    }

    private static SessionSectionView toView(StudySession s) {
        List<StudySection> sections = s.getSections();
        int total = sections.size();
        int index = s.getCurrentIndex();
        if (total == 0) {
            return new SessionSectionView(null, null, SummarizationFailureReason.NONE, index, total, false, List.of());
        }
        StudySection sec = sections.get(index);
        boolean quizAvailable = quizAvailableForSection(sec);
        List<Boolean> perSection = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            perSection.add(quizAvailableForSection(sections.get(i)));
        }
        return new SessionSectionView(
                sec.originalContent(),
                sec.summary(),
                sec.summarizationFailureReason(),
                index,
                total,
                quizAvailable,
                List.copyOf(perSection));
    }

    public static boolean quizAvailableForSection(StudySection sec) {
        if (sec.summarizationFailureReason() != SummarizationFailureReason.NONE) {
            return false;
        }
        String summary = sec.summary();
        if (!StringUtils.hasText(summary) || !StringUtils.hasText(summary.strip())) {
            return false;
        }
        return !SUMMARY_UNAVAILABLE.equals(summary.strip());
    }
}
