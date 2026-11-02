package edsa.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The timing changes in force, and the one place they are applied to the declared exams.
 *
 * <p>Applying them produces a new {@link ExamData} rather than editing the one handed in. The
 * scheduler, the allocator, the rule checker and every export then work from the same re-timed
 * data without any of them needing to know that an override exists — which is why a change made
 * here reaches the timetable, the room allocation, the seating plan and the downloads together
 * instead of each having to remember to ask.
 */
public final class TimingOverrides {

    private final Map<String, TimingOverride> byExamId = new LinkedHashMap<>();

    public static TimingOverrides none() {
        return new TimingOverrides();
    }

    public static TimingOverrides of(List<TimingOverride> overrides) {
        TimingOverrides result = new TimingOverrides();
        overrides.forEach(result::put);
        return result;
    }

    public TimingOverrides put(TimingOverride override) {
        byExamId.put(override.examId(), override);
        return this;
    }

    public Optional<TimingOverride> find(String examId) {
        return Optional.ofNullable(byExamId.get(examId));
    }

    public boolean isEmpty() {
        return byExamId.isEmpty();
    }

    public int size() {
        return byExamId.size();
    }

    public List<TimingOverride> all() {
        return List.copyOf(byExamId.values());
    }

    /** The same data with every override applied. Returns the original when there are none. */
    public ExamData applyTo(ExamData data) {
        if (byExamId.isEmpty() || data == null) {
            return data;
        }
        List<Exam> retimed = new ArrayList<>();
        for (Exam exam : data.exams()) {
            TimingOverride override = byExamId.get(exam.getId());
            retimed.add(override == null
                    ? exam
                    : exam.retimed(override.date(), override.start(), override.minutes()));
        }
        return new ExamData(data.students(), data.rooms(), data.faculty(), retimed);
    }

    /** The same set with this one override added, leaving this set untouched. */
    public TimingOverrides with(TimingOverride override) {
        TimingOverrides copy = TimingOverrides.of(all());
        return copy.put(override);
    }

    /** The same set with one paper's override removed, leaving this set untouched. */
    public TimingOverrides without(String examId) {
        TimingOverrides copy = TimingOverrides.of(all());
        copy.byExamId.remove(examId);
        return copy;
    }
}
