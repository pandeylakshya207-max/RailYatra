package railyatra.model;

/**
 * One line of a booking: a coach class, how many berths, and (after confirmation) which berths.
 * Supports partial cancellation: some berths of this line can be cancelled while the rest stay booked.
 */
public class BookingLine {

    private final CoachClass coachClass;
    private final int quantity;               // berths originally booked on this line
    private int cancelledQty = 0;             // berths of this line cancelled later
    private int[] berthNos = new int[0];      // berths still held (filled in when the booking is confirmed)

    public BookingLine(CoachClass coachClass, int quantity) {
        this.coachClass = coachClass;
        this.quantity = quantity;
    }

    /** Berths still booked on this line. */
    public int getActiveQty() {
        return quantity - cancelledQty;
    }

    /** Only berths still booked are charged. */
    public double getLineTotal() {
        return coachClass.getFare() * getActiveQty();
    }

    public void setBerthNos(int[] berthNos) {
        this.berthNos = berthNos;
    }

    /**
     * Cancels n berths of this line: the LAST n berths held are given back.
     * Returns those berth numbers so the coach class can mark them free.
     */
    public int[] cancelBerths(int n) {
        if (n < 1 || n > getActiveQty()) {
            throw new IllegalArgumentException("Can cancel 1 to " + getActiveQty() + " berth(s) on this line.");
        }
        int keep = berthNos.length - n;
        int[] released = new int[n];
        int[] kept = new int[keep];
        for (int i = 0; i < keep; i++) {
            kept[i] = berthNos[i];
        }
        for (int i = 0; i < n; i++) {
            released[i] = berthNos[keep + i];
        }
        berthNos = kept;
        cancelledQty += n;
        return released;
    }

    public int[] getBerthNos() { return berthNos; }
    public CoachClass getCoachClass() { return coachClass; }
    public int getQuantity() { return quantity; }
    public int getCancelledQty() { return cancelledQty; }

    /** "S-1 LB, S-4 LB" */
    public String getBerthText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < berthNos.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(coachClass.berthLabel(berthNos[i]));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        String row = String.format("%-16s %3d x %,9.2f = %,11.2f",
                coachClass.getName(), getActiveQty(), coachClass.getFare(), getLineTotal());
        if (berthNos.length > 0) {
            row += "\n      Berths: " + getBerthText();
        }
        if (cancelledQty > 0) {
            row += "\n      Cancelled: " + cancelledQty + " of " + quantity + " berth(s)";
        }
        return row;
    }
}
