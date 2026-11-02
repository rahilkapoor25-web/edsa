package edsa.core;

import java.time.LocalDateTime;

/**
 * One duty offered up, and whatever has happened to it since.
 *
 * <p>The sitting's own times are copied in rather than looked up. A substitution outlives the
 * plan it was made against — the workbook gets reimported, the mode changes, the timetable is
 * recomputed — and a record of who covered what at nine o'clock on the tenth is worthless if it
 * can only be read while that exact plan is still loaded.
 *
 * @param originalFacultyId whose duty it was
 * @param volunteerFacultyId who offered to take it, or null while nobody has
 * @param offeredBy        the account that recorded the offer
 * @param decidedBy        the administrator who approved or rejected it
 * @param completedBy      the administrator who confirmed the duty was actually done
 */
public record SubstitutionRequest(long id, String slotId, String roomId, String paperCode,
                                  LocalDateTime startsAt, LocalDateTime endsAt,
                                  String originalFacultyId, String reason,
                                  SubstitutionStatus status,
                                  LocalDateTime offeredAt, String offeredBy,
                                  String volunteerFacultyId, LocalDateTime volunteeredAt,
                                  String decidedBy, LocalDateTime decidedAt,
                                  String completedBy, LocalDateTime completedAt,
                                  String outcome) {

    /** A fresh offer, before it has been stored and given an id. */
    public static SubstitutionRequest offer(String slotId, String roomId, String paperCode,
                                            LocalDateTime startsAt, LocalDateTime endsAt,
                                            String originalFacultyId, String reason,
                                            String offeredBy, LocalDateTime now) {
        return new SubstitutionRequest(0, slotId, roomId, paperCode, startsAt, endsAt,
                originalFacultyId, reason, SubstitutionStatus.OFFERED, now, offeredBy,
                null, null, null, null, null, null, null);
    }

    public boolean hasVolunteer() {
        return volunteerFacultyId != null && !volunteerFacultyId.isBlank();
    }

    /**
     * Whether this person may volunteer for it.
     *
     * <p>Covering your own duty is the first thing anybody would try, and it would earn points
     * for turning up to the duty you already had. The database refuses it as well.
     */
    public boolean mayBeCoveredBy(String facultyId) {
        return status.mayVolunteer() && facultyId != null && !facultyId.equals(originalFacultyId);
    }

    /**
     * Whether this account may decide it.
     *
     * <p>Nobody decides their own case: not the person giving the duty up, and not the person
     * who would be paid for taking it. Being an administrator is not enough, because an
     * administrator with a faculty record is also a professor who can be short of points.
     */
    public boolean mayBeDecidedBy(String accountEmail, String accountFacultyId) {
        if (!status.mayDecide()) {
            return false;
        }
        if (accountFacultyId != null
                && (accountFacultyId.equals(originalFacultyId) || accountFacultyId.equals(volunteerFacultyId))) {
            return false;
        }
        return accountEmail != null && !accountEmail.equals(offeredBy);
    }

    /** Whether the sitting has already passed, so an undecided offer is now moot. */
    public boolean isPast(LocalDateTime now) {
        return endsAt != null && now.isAfter(endsAt);
    }

    /** The duty this offer is about, as the roster writes it. */
    public Assignment asAssignment(String facultyId) {
        return new Assignment(slotId, roomId, facultyId, DutyOrigin.SUBSTITUTED);
    }

    public SubstitutionRequest withStatus(SubstitutionStatus newStatus) {
        return new SubstitutionRequest(id, slotId, roomId, paperCode, startsAt, endsAt,
                originalFacultyId, reason, newStatus, offeredAt, offeredBy,
                volunteerFacultyId, volunteeredAt, decidedBy, decidedAt, completedBy, completedAt, outcome);
    }
}
