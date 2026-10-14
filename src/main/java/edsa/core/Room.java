package edsa.core;

/**
 * An examination room. Capacity is authoritative; the grid is how seats are addressed when
 * the room data describes rows and columns.
 */
public final class Room {

    private static final int DEFAULT_COLUMNS = 6;

    private final String id;
    private final String name;
    private final int rowCount;
    private final int columnCount;
    private final String building;
    private final int capacity;

    public Room(String id, String name, int rowCount, int columnCount, String building) {
        this(id, name, rowCount, columnCount, building, rowCount * columnCount);
    }

    private Room(String id, String name, int rowCount, int columnCount, String building, int capacity) {
        this.id = id;
        this.name = name;
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.building = building;
        this.capacity = capacity;
    }

    /** A room described by how many it seats rather than by a grid. */
    public static Room ofCapacity(String id, String name, int capacity, String building) {
        int columns = Math.min(DEFAULT_COLUMNS, Math.max(capacity, 1));
        int rows = (int) Math.ceil((double) Math.max(capacity, 1) / columns);
        return new Room(id, name, rows, columns, building, capacity);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public String getBuilding() {
        return building;
    }

    public int capacity() {
        return capacity;
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
