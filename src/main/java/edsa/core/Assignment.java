package edsa.core;

/** One invigilation duty: a faculty member in a room for a slot. */
public record Assignment(String slotId, String roomId, String facultyId) {
}
