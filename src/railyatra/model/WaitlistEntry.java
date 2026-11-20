package railyatra.model;

import railyatra.people.Passenger;

/**
 * NOVELTY 2 - one waiting-list request (WL1, WL2, ...), like a real railway waitlisted ticket.
 */
public class WaitlistEntry {

    private static int counter = 0;           // static: WL numbers are shared by all entries

    private final String wlNo;
    private final Passenger passenger;
    private final CoachClass coachClass;
    private final int quantity;

    public WaitlistEntry(Passenger passenger, CoachClass coachClass, int quantity) {
        counter++;
        this.wlNo = "WL" + counter;
        this.passenger = passenger;
        this.coachClass = coachClass;
        this.quantity = quantity;
    }

    public String getWlNo() { return wlNo; }
    public Passenger getPassenger() { return passenger; }
    public CoachClass getCoachClass() { return coachClass; }
    public int getQuantity() { return quantity; }

    @Override
    public String toString() {
        return String.format("%-5s %-28s %3d x %s", wlNo, passenger.getName() + " (" + passenger.getType() + ")",
                quantity, coachClass.getName());
    }
}
