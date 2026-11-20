package railyatra.model;

import railyatra.people.Passenger;

/**
 * One booking (one bill).
 * UNIT I   - final double GST, arrays (BookingLine[]).
 * UNIT II  - token & 1 decides the window (bitwise AND: odd numbers end in binary 1).
 * UNIT III - static counter for PNR1, PNR2 ...; recursive subtotal; overloaded constructors.
 * UNIT IV  - implements the Billable interface; final method; toString().
 */
public class Booking implements Billable {

    public static final double GST = 0.05;                    // 5% GST on the discounted amount
    public static final double CANCELLATION_CHARGE = 0.10;    // NOVELTY 2: 10% kept on cancellation

    private static int counter = 0;                // shared by ALL bookings -> PNR1, PNR2, ...

    private final String pnr;
    private final int token;
    private final Passenger passenger;
    private final BookingLine[] lines;
    private final int lineCount;
    private final String note;                     // e.g. "Confirmed from waitlist WL1"
    private boolean cancelled = false;
    private double refund = 0;

    /** Constructor 1: normal counter booking with many lines. */
    public Booking(Passenger passenger, BookingLine[] cartLines, int lineCount) {
        this(passenger, cartLines, lineCount, null);
    }

    /** Constructor 2 (overloaded): a single-line booking, used when a waitlist entry is confirmed. */
    public Booking(Passenger passenger, BookingLine line, String note) {
        this(passenger, new BookingLine[] { line }, 1, note);
    }

    private Booking(Passenger passenger, BookingLine[] cartLines, int lineCount, String note) {
        counter++;
        this.pnr = "PNR" + counter;
        this.token = counter;
        this.passenger = passenger;
        this.lineCount = lineCount;
        this.note = note;
        this.lines = new BookingLine[lineCount];
        for (int i = 0; i < lineCount; i++) {       // copy so the cart can be reused
            this.lines[i] = cartLines[i];
        }
    }

    /** UNIT II - bitwise AND. 5 = 101, 5 & 1 = 1 -> odd.   4 = 100, 4 & 1 = 0 -> even. */
    public String getWindow() {
        return (token & 1) == 1 ? "Window 1" : "Window 2";
    }

    /** UNIT III - recursion: total of line i + total of all lines after it. */
    private double subtotalFrom(int index) {
        if (index == lineCount) {                   // base case: no lines left
            return 0;
        }
        return lines[index].getLineTotal() + subtotalFrom(index + 1);
    }

    @Override
    public double getSubtotal() {
        return subtotalFrom(0);
    }

    @Override
    public double getDiscount() {
        return getSubtotal() * passenger.getDiscountRate();   // runtime polymorphism
    }

    @Override
    public double getGst() {
        return (getSubtotal() - getDiscount()) * GST;
    }

    /** UNIT IV - final method: the billing formula cannot be changed by a subclass. */
    @Override
    public final double getTotal() {
        return getSubtotal() - getDiscount() + getGst();
    }

    /** NOVELTY 2 - cancels this booking and returns the refund amount. */
    public double cancel() {
        cancelled = true;
        refund = getTotal() * (1 - CANCELLATION_CHARGE);
        return refund;
    }

    public static int getCount() { return counter; }
    public String getPnr() { return pnr; }
    public int getToken() { return token; }
    public Passenger getPassenger() { return passenger; }
    public int getLineCount() { return lineCount; }
    public BookingLine getLine(int i) { return lines[i]; }
    public boolean isCancelled() { return cancelled; }
    public double getRefund() { return refund; }

    @Override
    public String toString() {
        String line = "+--------------------------------------------------+\n";
        StringBuilder sb = new StringBuilder();
        sb.append(line);
        sb.append("|           RAILYATRA  -  RESERVATION BILL         |\n");
        sb.append(line);
        sb.append(String.format("  PNR       : %s   [%s]%n", pnr, cancelled ? "CANCELLED" : "CONFIRMED"));
        sb.append(String.format("  Passenger : %s%n", passenger));
        sb.append(String.format("  Token     : %d  ->  collect ticket at %s%n", token, getWindow()));
        if (note != null) {
            sb.append("  Note      : ").append(note).append('\n');
        }
        sb.append("  ------------------------------------------------\n");
        for (int i = 0; i < lineCount; i++) {
            sb.append("  ").append(lines[i]).append('\n');
        }
        if (passenger.prefersLowerBerth()) {
            sb.append("  (Senior citizen: lower berths given first)\n");
        }
        sb.append("  ------------------------------------------------\n");
        sb.append(String.format("  Subtotal                         Rs. %,11.2f%n", getSubtotal()));
        sb.append(String.format("  Concession (%2d%%)               - Rs. %,11.2f%n",
                (int) (passenger.getDiscountRate() * 100), getDiscount()));
        sb.append(String.format("  GST (%d%%)                      + Rs. %,11.2f%n",
                (int) Math.round(GST * 100), getGst()));
        sb.append(String.format("  TOTAL                            Rs. %,11.2f%n", getTotal()));
        if (getDiscount() > 0) {
            sb.append(String.format("  You saved Rs. %,.2f with your concession!%n", getDiscount()));
        }
        if (cancelled) {
            sb.append(String.format("  Refunded (after %d%% charge)      Rs. %,11.2f%n",
                    (int) Math.round(CANCELLATION_CHARGE * 100), refund));
        }
        sb.append(line);
        return sb.toString();
    }
}
