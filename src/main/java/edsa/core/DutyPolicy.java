package edsa.core;

/**
 * The rules an examination office sets for how invigilation is shared out.
 *
 * <p>Every one of these is configuration rather than a rule this application believes in.
 * Published university practice varies widely — some institutions set a minimum number of
 * duties by grade, some excuse senior staff entirely, some ask the course teacher to invigilate
 * their own paper and others forbid exactly that — so each is a value an office sets, and the
 * defaults below are this application's own starting point, not anybody's policy.
 *
 * @param weightBySeniority whether the duty shares on the ladder are applied at all. Off, every
 *                          eligible member is treated as carrying a full share, which is what
 *                          the allocator did before the ladder existed.
 * @param maxDutiesPerMember the most duties one member may be given in a session, or 0 for no
 *                          cap. A cap is the simplest protection against the allocator leaning
 *                          on whoever happens to sort first.
 * @param minDutiesPerMember the fewest duties a member carrying a full share is expected to
 *                          take. Nothing in the allocator enforces a minimum — it is the floor
 *                          that reward credits may not redeem somebody below, which is the only
 *                          place a minimum can actually protect staffing.
 * @param courseTeacherMayInvigilate whether a member may invigilate a paper they teach.
 * @param preferOwnDepartment whether a member from the paper's own department is preferred when
 *                          loads are otherwise equal.
 */
public record DutyPolicy(boolean weightBySeniority,
                         int maxDutiesPerMember,
                         int minDutiesPerMember,
                         boolean courseTeacherMayInvigilate,
                         boolean preferOwnDepartment) {

    public static DutyPolicy defaults() {
        return new DutyPolicy(true, 0, 1, false, false);
    }

    /** The policy that behaves as the allocator did before any of this existed. */
    public static DutyPolicy unweighted() {
        return new DutyPolicy(false, 0, 0, true, false);
    }

    public boolean hasCap() {
        return maxDutiesPerMember > 0;
    }

    public boolean hasFloor() {
        return minDutiesPerMember > 0;
    }
}
