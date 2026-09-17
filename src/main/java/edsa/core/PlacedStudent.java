package edsa.core;

/** One student at their place on a room's grid. Rows and seats count from 1. */
public record PlacedStudent(int row, int column, Student student) {
}
