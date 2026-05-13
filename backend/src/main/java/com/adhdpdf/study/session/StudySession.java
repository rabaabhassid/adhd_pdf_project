package com.adhdpdf.study.session;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One study run: unique {@link #id}, {@link #sections} (original + summary per chunk), and {@link #currentIndex}.
 */
public class StudySession {

    private final String id;
    private final List<StudySection> sections;
    private int currentIndex;

    public StudySession(String id, List<StudySection> sections, int currentIndex) {
        this.id = Objects.requireNonNull(id, "id");
        this.sections = List.copyOf(sections);
        this.currentIndex = validateIndex(currentIndex, this.sections.size());
    }

    /**
     * New session with a random id, starting at the first section.
     */
    public static StudySession fromStudySections(List<StudySection> sections) {
        return new StudySession(UUID.randomUUID().toString(), sections, 0);
    }

    public String getId() {
        return id;
    }

    public List<StudySection> getSections() {
        return sections;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = validateIndex(currentIndex, sections.size());
    }

    private static int validateIndex(int index, int sectionCount) {
        if (sectionCount == 0) {
            if (index != 0) {
                throw new IndexOutOfBoundsException("currentIndex must be 0 when there are no sections");
            }
            return 0;
        }
        if (index < 0 || index >= sectionCount) {
            throw new IndexOutOfBoundsException(
                    "currentIndex " + index + " out of bounds for section count " + sectionCount);
        }
        return index;
    }
}
