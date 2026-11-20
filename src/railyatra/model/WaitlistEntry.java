package railyatra.model;

import railyatra.people.Passenger;

/**
 * One waiting-list request (WL1, WL2, ...).
 *
 * NOVELTY - PRIORITY WAITLIST:
 *   priority 2 = pregnant or disability / medical need
 *   priority 1 = senior citizen (automatic)
 *   priority 0 = everyone else
 * Higher priority is confirmed first; inside the same priority it is first come, first served.
 */
public class WaitlistEntry {

    public static final int PRIORITY_MEDICAL = 2;
    public static final int PRIORITY_SENIOR = 1;
    public static final int PRIORITY_NORMAL = 0;

    private static int counter = 0;           // static: WL numbers are shared by all entries

    private final String wlNo;
    private final Passenger passenger;
    private final CoachClass coachClass;
    private final int quantity;
    private final int priority;
    private final String reason;

    /**
     * @param category 'P' = pregnant, 'D' = disability / medical, 'N' = none.
     *                 Senior citizens get priority 1 automatically.
     */
    public WaitlistEntry(Passenger passenger, CoachClass coachClass, int quantity, char category) {
        counter++;
        this.wlNo = "WL" + counter;
        this.passenger = passenger;
        this.coachClass = coachClass;
        this.quantity = quantity;
        switch (category) {
            case 'P':
                priority = PRIORITY_MEDICAL;
                reason = "pregnant";
                break;
            case 'D':
                priority = PRIORITY_MEDICAL;
                reason = "disability / medical";
                break;
            default:
                priority = passenger.prefersLowerBerth() ? PRIORITY_SENIOR : PRIORITY_NORMAL;
                reason = passenger.prefersLowerBerth() ? "senior citizen" : "";
        }
    }

    public String getWlNo() { return wlNo; }
    public Passenger getPassenger() { return passenger; }
    public CoachClass getCoachClass() { return coachClass; }
    public int getQuantity() { return quantity; }
    public int getPriority() { return priority; }

    public String getPriorityLabel() {
        return priority == PRIORITY_MEDICAL ? "HIGH (" + reason + ")"
                : priority == PRIORITY_SENIOR ? "MEDIUM (" + reason + ")" : "normal";
    }

    @Override
    public String toString() {
        return String.format("%-5s %-28s %3d x %-15s priority: %s", wlNo,
                passenger.getName() + " (" + passenger.getType() + ")", quantity, coachClass.getName(),
                getPriorityLabel());
    }
}
