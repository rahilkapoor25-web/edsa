package edsa.core;

import java.util.List;

/** What one faculty member was asked to do. */
public record Workload(Faculty member, List<ExamSlot> slots) {

    public int duties() {
        return slots.size();
    }
}
