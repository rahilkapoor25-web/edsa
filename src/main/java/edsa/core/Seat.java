package edsa.core;

/** One student placed at one position on a room's grid. Rows and columns count from 1. */
public record Seat(String roomId, int row, int column, String studentId) {
}
