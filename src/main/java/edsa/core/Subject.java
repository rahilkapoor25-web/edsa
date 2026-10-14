package edsa.core;

/** A paper on the syllabus. Exams are sat for a subject; students register for one. */
public record Subject(String code, String name, String department, Integer semester) {
}
