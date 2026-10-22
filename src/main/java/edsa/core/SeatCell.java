package edsa.core;

/**
 * One position on a room's grid: who is sitting there, or why nobody is.
 *
 * <p>A cell is one of three things. Somebody is seated at it; it is a real seat that happens
 * to be free; or it is a position the room does not seat at all, because a room's grid can be
 * wider than its capacity — twenty seats addressed six to a row leave four dead positions in
 * the last row. Telling the last two apart matters to whoever sets the room out.
 */
public record SeatCell(int row, int column, Student student, boolean seatExists) {

    public boolean isTaken() {
        return student != null;
    }

    public boolean isFree() {
        return student == null && seatExists;
    }

    /** How the seat is referred to out loud: "3-4" is row three, seat four. */
    public String position() {
        return row + "-" + column;
    }
}
