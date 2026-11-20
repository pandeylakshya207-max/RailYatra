package railyatra.model;

/**
 * NOVELTY - AUTOMATIC BERTH-SWAP MATCHING.
 * A passenger asks to change one berth for a berth of a WANTED TYPE (LB, MB, UB, SL or SU).
 * RailYatra serves it automatically from a free berth or from a passenger who agreed to swap.
 * A request that cannot be served yet waits here (SW1, SW2 ...) and is retried automatically
 * whenever a willing passenger books, someone agrees to swap, or berths are cancelled.
 */
public class SwapRequest {

    private static int counter = 0;           // static: SW numbers are shared by all requests

    private final String id;
    private final Booking booking;
    private final int lineIndex;
    private final int berthNo;
    private final String wantType;
    private boolean done = false;

    public SwapRequest(Booking booking, int lineIndex, int berthNo, String wantType) {
        counter++;
        this.id = "SW" + counter;
        this.booking = booking;
        this.lineIndex = lineIndex;
        this.berthNo = berthNo;
        this.wantType = wantType;
    }

    /** Still waiting AND the passenger still holds that berth (not cancelled or already moved). */
    public boolean isOpen() {
        return !done && booking.getLine(lineIndex).holds(berthNo);
    }

    public void markDone() { done = true; }
    public String getId() { return id; }
    public Booking getBooking() { return booking; }
    public int getLineIndex() { return lineIndex; }
    public BookingLine getLine() { return booking.getLine(lineIndex); }
    public int getBerthNo() { return berthNo; }
    public String getWantType() { return wantType; }

    @Override
    public String toString() {
        CoachClass c = getLine().getCoachClass();
        return String.format("%-4s %-5s %-20s %-16s %-10s wants a %s berth", id, booking.getPnr(),
                booking.getPassenger().getName(), c.getName(), c.berthLabel(berthNo), wantType);
    }
}
