package edsa.core;

/** An examination room, described as a grid of seats. */
public final class Room {

    private final String id;
    private final String name;
    private final int rowCount;
    private final int columnCount;
    private final String building;

    public Room(String id, String name, int rowCount, int columnCount, String building) {
        this.id = id;
        this.name = name;
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.building = building;
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
        return rowCount * columnCount;
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
