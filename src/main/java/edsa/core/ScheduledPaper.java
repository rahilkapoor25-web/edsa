package edsa.core;

/** One paper as the timetable shows it: when it runs, how many sit it, and how it is staffed. */
public record ScheduledPaper(String examId, String paperCode, String paperName, String date,
                             String time, String length, int students, int rooms, int invigilators) {
}
