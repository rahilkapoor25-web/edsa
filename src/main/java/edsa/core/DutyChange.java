package edsa.core;

/** One duty handed from one invigilator to another, for the diff the coordinator reads. */
public record DutyChange(ExamSlot slot, String roomId, Faculty from, Faculty to) {
}
