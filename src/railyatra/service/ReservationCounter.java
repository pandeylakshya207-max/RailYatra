package railyatra.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import railyatra.exceptions.BerthsNotAvailableException;
import railyatra.exceptions.CoachClassNotFoundException;
import railyatra.model.Booking;
import railyatra.model.BookingLine;
import railyatra.model.CoachClass;
import railyatra.model.SwapRequest;
import railyatra.model.WaitlistEntry;
import railyatra.people.Passenger;
import railyatra.util.ItemStack;
import railyatra.util.WaitlistQueue;

/**
 * The "brain" of the counter: holds the class list, all bookings and the waitlist in plain arrays.
 * UNIT I - CoachClass[] and Booking[] arrays (no ArrayList / HashMap).
 * UNIT V - methods declare "throws" and use "throw" for the two checked exceptions.
 */
public class ReservationCounter {

    public static final int MAX_BOOKINGS = 100;
    public static final int MAX_WAITLIST = 50;
    public static final int MAX_SWAPS = 50;

    // Berth patterns of one bay, as in real Indian Railways coaches
    private static final String[] SLEEPER_BAY  = {"LB", "MB", "UB", "LB", "MB", "UB", "SL", "SU"};
    private static final String[] TWO_TIER_BAY = {"LB", "UB", "LB", "UB", "SL", "SU"};
    private static final String[] FIRST_BAY    = {"LB", "UB", "LB", "UB"};
    private static final String[] SEAT_ROW     = {"W", "M", "A", "A", "W"};

    private final CoachClass[] classes;
    private final Booking[] bookings = new Booking[MAX_BOOKINGS];
    private int bookingCount = 0;
    private final WaitlistQueue waitlist = new WaitlistQueue(MAX_WAITLIST);

    public ReservationCounter() {
        classes = new CoachClass[] {
            new CoachClass(101, "Sleeper",        "Non-AC",  450, 60, "S", SLEEPER_BAY),
            new CoachClass(102, "AC 3 Tier",      "AC",     1150, 40, "B", SLEEPER_BAY),
            new CoachClass(103, "AC 2 Tier",      "AC",     1650, 20, "A", TWO_TIER_BAY),
            new CoachClass(104, "AC First Class", "AC",     2800,  8, "H", FIRST_BAY),
            new CoachClass(105, "Second Sitting", "Non-AC",  180, 80, "D", SEAT_ROW),
            new CoachClass(106, "AC Chair Car",   "AC",      775, 30, "C", SEAT_ROW)
        };
    }

    // ------------------------------------------------------------------ lookups

    /** UNIT V - "throws" declares it, "throw" actually raises it. */
    public CoachClass findCoachClass(int id) throws CoachClassNotFoundException {
        for (CoachClass c : classes) {
            if (c.getId() == id) {
                return c;
            }
        }
        throw new CoachClassNotFoundException(id);
    }

    /**
     * Checks one new line against berths left, counting what is ALREADY in this booking's cart.
     * Example: cart already has 30 x AC 3 Tier, clerk adds 15 more -> 45 > 40 -> rejected.
     */
    public void checkBerths(CoachClass c, int qty, ItemStack cart) throws BerthsNotAvailableException {
        int alreadyInCart = cart.quantityOf(c.getId());
        int stillFree = c.getBerths() - alreadyInCart;
        if (qty > stillFree) {
            throw new BerthsNotAvailableException(c.getName(), qty, stillFree);
        }
    }

    /**
     * Confirms a booking. ALL lines are checked first; berths change only if every line passes.
     * So a rejected booking never changes any berths (problem point 3).
     */
    public Booking confirm(Passenger p, ItemStack cart) throws BerthsNotAvailableException {
        if (bookingCount == MAX_BOOKINGS) {
            throw new IllegalStateException("Booking register is full for today.");
        }
        BookingLine[] lines = cart.toArray();
        int n = cart.size();

        // Pass 1: validate everything (no changes yet)
        for (int i = 0; i < n; i++) {
            CoachClass c = lines[i].getCoachClass();
            int totalAsked = cart.quantityOf(c.getId());
            if (totalAsked > c.getBerths()) {
                throw new BerthsNotAvailableException(c.getName(), totalAsked, c.getBerths());
            }
        }
        // Pass 2: everything is valid -> allot real berth numbers (NOVELTY 1)
        for (int i = 0; i < n; i++) {
            CoachClass c = lines[i].getCoachClass();
            lines[i].setBerthNos(c.allocate(lines[i].getQuantity(), p.prefersLowerBerth()));
        }
        Booking b = new Booking(p, lines, n);
        bookings[bookingCount++] = b;
        return b;
    }

    /** Problem point 5: find an old bill. Accepts "PNR3", "pnr3" or just "3". */
    public Booking findBooking(String pnr) {
        String key = pnr.trim().toUpperCase();
        if (!key.startsWith("PNR")) {
            key = "PNR" + key;
        }
        for (int i = 0; i < bookingCount; i++) {
            if (bookings[i].getPnr().equals(key)) {
                return bookings[i];
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ NOVELTY 3: suggestions

    /**
     * When a class cannot take qty berths, suggest other classes that can, cheapest first.
     * Uses bubble sort on a plain array (no Collections.sort).
     */
    public CoachClass[] suggestAlternatives(CoachClass full, int qty) {
        CoachClass[] temp = new CoachClass[classes.length];
        int n = 0;
        for (CoachClass c : classes) {
            if (c != full && c.getBerths() >= qty) {
                temp[n++] = c;
            }
        }
        for (int i = 0; i < n - 1; i++) {                       // bubble sort by fare
            for (int j = 0; j < n - 1 - i; j++) {
                if (temp[j].getFare() > temp[j + 1].getFare()) {
                    CoachClass t = temp[j];
                    temp[j] = temp[j + 1];
                    temp[j + 1] = t;
                }
            }
        }
        CoachClass[] result = new CoachClass[n];                // exact-size array
        for (int i = 0; i < n; i++) {
            result[i] = temp[i];
        }
        return result;
    }

    // ------------------------------------------------------------------ NOVELTY 2: waitlist + cancel

    /** Overload 1: no special category (seniors still get priority automatically). */
    public WaitlistEntry addToWaitlist(Passenger p, CoachClass c, int qty) {
        return addToWaitlist(p, c, qty, 'N');
    }

    /** Overload 2: category 'P' = pregnant, 'D' = disability / medical, 'N' = none. */
    public WaitlistEntry addToWaitlist(Passenger p, CoachClass c, int qty, char category) {
        // The same passenger may not wait twice for the same coach class
        for (int i = 0; i < waitlist.size(); i++) {
            WaitlistEntry w = waitlist.get(i);
            if (w.getCoachClass() == c && w.getPassenger().getName().equalsIgnoreCase(p.getName())) {
                throw new IllegalStateException(p.getName() + " is already on the waitlist for "
                        + c.getName() + " as " + w.getWlNo() + ".");
            }
        }
        WaitlistEntry e = new WaitlistEntry(p, c, qty, category);
        waitlist.enqueue(e);                                     // throws IllegalStateException if full
        return e;
    }

    /**
     * Partial cancellation: cancels n berths of one coach-class line of a booking.
     * Those berths go back to the chart and 90% of their price is refunded.
     */
    public double cancelPart(Booking b, int lineIndex, int n) {
        int[][] released = new int[1][];
        double refund = b.cancelPart(lineIndex, n, released);
        b.getLine(lineIndex).getCoachClass().release(released[0]);
        return refund;
    }

    /** Cancels exactly the chosen berth numbers of one line; they go back to the chart. */
    public double cancelChosen(Booking b, int lineIndex, int[] berthNos) {
        double refund = b.cancelChosen(lineIndex, berthNos);
        b.getLine(lineIndex).getCoachClass().release(berthNos);
        return refund;
    }

    /** Full cancellation = cancel every berth still held on every line. */
    public double cancel(Booking b) {
        double refund = 0;
        for (int i = 0; i < b.getLineCount(); i++) {
            int active = b.getLine(i).getActiveQty();
            if (active > 0) {
                refund += cancelPart(b, i, active);
            }
        }
        return refund;
    }

    /**
     * After berths are freed, confirm waitlisted passengers in FIFO order.
     * Every entry is taken from the front once; if its berths are now free it becomes a booking,
     * otherwise it goes back to the rear (so the original order is kept).
     */
    public Booking[] promoteWaitlist() {
        Booking[] temp = new Booking[waitlist.size()];
        int promoted = 0;
        // NOVELTY - PRIORITY WAITLIST: serve priority 2 first, then 1, then 0.
        // Each pass walks the whole circular queue once, so order inside a priority stays first-come.
        for (int level = WaitlistEntry.PRIORITY_MEDICAL; level >= WaitlistEntry.PRIORITY_NORMAL; level--) {
            int rounds = waitlist.size();
            for (int i = 0; i < rounds; i++) {
                WaitlistEntry e = waitlist.dequeue();
                CoachClass c = e.getCoachClass();
                if (e.getPriority() == level && e.getQuantity() <= c.getBerths() && bookingCount < MAX_BOOKINGS) {
                    BookingLine line = new BookingLine(c, e.getQuantity());
                    line.setBerthNos(c.allocate(e.getQuantity(), e.getPassenger().prefersLowerBerth()));
                    Booking b = new Booking(e.getPassenger(), line, "Confirmed from waitlist " + e.getWlNo()
                            + " (priority: " + e.getPriorityLabel() + ")");
                    bookings[bookingCount++] = b;
                    temp[promoted++] = b;
                } else {
                    waitlist.enqueue(e);                         // still waiting, keeps its place
                }
            }
        }
        Booking[] result = new Booking[promoted];
        for (int i = 0; i < promoted; i++) {
            result[i] = temp[i];
        }
        return result;
    }

    // ------------------------------------------------------------------ NOVELTY: automatic berth swap

    private final SwapRequest[] swaps = new SwapRequest[MAX_SWAPS];
    private int swapCount = 0;
    private Booking lastSwapPartner;

    /**
     * A passenger asks to change one berth for a berth of the wanted type (LB, MB, UB, SL, SU).
     * Served AUTOMATICALLY, nobody else has to come to the counter:
     *   1. a FREE berth of that type (same bay first) -> moved at once;
     *   2. otherwise a passenger who AGREED TO SWAP at booking and holds that type
     *      (same compartment/bay first, then the same coach) -> both tickets swapped at once;
     *   3. otherwise the request waits (SW1, SW2 ...) and is retried automatically by servePendingSwaps().
     * Returns a message describing what happened.
     */
    public String requestSwap(Booking b, int lineIndex, int berthNo, String wantType) {
        BookingLine line = b.getLine(lineIndex);
        CoachClass c = line.getCoachClass();
        if (!c.hasLowerBerths()) {
            throw new IllegalArgumentException("Swaps are only for sleeping berths, not seats in " + c.getName() + ".");
        }
        if (!line.holds(berthNo)) {
            throw new IllegalArgumentException(c.berthLabel(berthNo) + " is not on " + b.getPnr() + ".");
        }
        if (!c.hasType(wantType)) {
            throw new IllegalArgumentException(c.getName() + " has no " + wantType + " berths.");
        }
        if (c.berthType(berthNo).equals(wantType)) {
            throw new IllegalArgumentException(c.berthLabel(berthNo) + " is already a " + wantType + " berth.");
        }
        for (int i = 0; i < swapCount; i++) {
            if (swaps[i].isOpen() && swaps[i].getBooking() == b && swaps[i].getBerthNo() == berthNo) {
                throw new IllegalArgumentException("There is already an open request " + swaps[i].getId() + " for this berth.");
            }
        }
        lastSwapPartner = null;
        String done = tryServe(b, lineIndex, berthNo, wantType);
        if (done != null) {
            return done;
        }
        if (swapCount == MAX_SWAPS) {
            throw new IllegalStateException("Swap register is full.");
        }
        SwapRequest r = new SwapRequest(b, lineIndex, berthNo, wantType);
        swaps[swapCount++] = r;
        return "No free " + wantType + " berth and no passenger who agreed to swap has one yet. Saved as " + r.getId()
                + "; it will be matched automatically when a willing passenger books or a berth is freed.";
    }

    /**
     * Tries to serve one swap. Returns a message if it was done, or null if nothing fits yet.
     * Order (families stay together):
     *   1. free berth in the SAME bay   2. willing passenger in the SAME bay
     *   3. free berth anywhere          4. willing passenger anywhere in the coach
     * Fairness: a senior citizen is never moved from a lower berth to a non-lower one.
     */
    private String tryServe(Booking b, int lineIndex, int berthNo, String wantType) {
        for (int pass = 1; pass <= 2; pass++) {
            boolean sameBay = pass == 1;
            String done = tryFree(b, lineIndex, berthNo, wantType, sameBay);
            if (done == null) {
                done = tryPartner(b, lineIndex, berthNo, wantType, sameBay);
            }
            if (done != null) {
                return done;
            }
        }
        return null;
    }

    private String tryFree(Booking b, int lineIndex, int berthNo, String wantType, boolean sameBay) {
        BookingLine line = b.getLine(lineIndex);
        CoachClass c = line.getCoachClass();
        int free = c.findFreeOfType(wantType, berthNo, sameBay);
        if (free == -1) {
            return null;
        }
        String from = c.berthLabel(berthNo);
        c.moveBerth(berthNo, free);
        line.replaceBerth(berthNo, free);
        b.addNote("Moved " + from + " -> " + c.berthLabel(free) + " (free " + wantType + " berth)");
        return b.getPnr() + ": a free " + wantType + " berth was found" + (sameBay ? " in the same compartment" : "")
                + ". Moved from " + from + " to " + c.berthLabel(free) + ".";
    }

    private String tryPartner(Booking b, int lineIndex, int berthNo, String wantType, boolean sameBay) {
        BookingLine line = b.getLine(lineIndex);
        CoachClass c = line.getCoachClass();
        boolean giveUpLower = !CoachClass.isLower(c.berthType(berthNo));
        for (int k = 0; k < bookingCount; k++) {                 // whoever booked first is asked first
            Booking other = bookings[k];
            if (other == b || other.isCancelled() || !other.isSwapWilling()) {
                continue;
            }
            if (other.getPassenger().prefersLowerBerth() && CoachClass.isLower(wantType) && giveUpLower) {
                continue;                                        // never move a senior off a lower berth
            }
            for (int li = 0; li < other.getLineCount(); li++) {
                BookingLine ol = other.getLine(li);
                if (ol.getCoachClass() != c) {
                    continue;
                }
                for (int x : ol.getBerthNos()) {
                    if (c.berthType(x).equals(wantType) && (!sameBay || c.bayOf(x) == c.bayOf(berthNo))) {
                        String from = c.berthLabel(berthNo);
                        line.replaceBerth(berthNo, x);           // both berths stay occupied,
                        ol.replaceBerth(x, berthNo);             // only the owners change
                        b.addNote("Swapped " + from + " for " + c.berthLabel(x) + " with " + other.getPnr());
                        other.addNote("Swapped " + c.berthLabel(x) + " for " + from + " with " + b.getPnr()
                                + " (agreed to swap)");
                        lastSwapPartner = other;
                        return b.getPnr() + " (" + b.getPassenger().getName() + ") swapped " + from + " for "
                                + c.berthLabel(x) + " with " + other.getPnr() + " (" + other.getPassenger().getName()
                                + ", agreed to swap" + (sameBay ? ", same compartment" : "") + "). Both tickets updated.";
                    }
                }
            }
        }
        return null;
    }

    /**
     * Retries every waiting swap request, oldest first. Called automatically after a booking,
     * after someone agrees to swap, and after a cancellation. Returns what happened.
     */
    public String[] servePendingSwaps() {
        String[] temp = new String[swapCount];
        int n = 0;
        for (int i = 0; i < swapCount; i++) {
            SwapRequest r = swaps[i];
            if (!r.isOpen()) {
                continue;
            }
            String done = tryServe(r.getBooking(), r.getLineIndex(), r.getBerthNo(), r.getWantType());
            if (done != null) {
                r.markDone();
                temp[n++] = r.getId() + " matched: " + done;
            }
        }
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            out[i] = temp[i];
        }
        return out;
    }

    /** Passenger agrees (or stops agreeing) to swap; waiting requests are retried at once. */
    public String[] setSwapWilling(Booking b, boolean willing) {
        b.setSwapWilling(willing);
        return willing ? servePendingSwaps() : new String[0];
    }

    /** The other booking changed by the last direct match (null if none). */
    public Booking takeLastSwapPartner() {
        Booking p = lastSwapPartner;
        lastSwapPartner = null;
        return p;
    }

    /** Open (waiting) swap requests, exact-size array. */
    public SwapRequest[] getOpenSwaps() {
        int n = 0;
        for (int i = 0; i < swapCount; i++) {
            if (swaps[i].isOpen()) {
                n++;
            }
        }
        SwapRequest[] open = new SwapRequest[n];
        n = 0;
        for (int i = 0; i < swapCount; i++) {
            if (swaps[i].isOpen()) {
                open[n++] = swaps[i];
            }
        }
        return open;
    }

    // ------------------------------------------------------------------ report

    /** Revenue = total of berths still booked + 10% charge kept on every cancelled berth. */
    public double getTotalRevenue() {
        double sum = 0;
        for (int i = 0; i < bookingCount; i++) {
            sum += bookings[i].getTotal() + bookings[i].getChargesKept();
        }
        return sum;
    }

    /** Most popular = most berths sold. Returns null if nothing has been sold yet. */
    public CoachClass getMostPopular() {
        CoachClass best = null;
        for (CoachClass c : classes) {
            if (c.getBerthsSold() > 0 && (best == null || c.getBerthsSold() > best.getBerthsSold())) {
                best = c;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ ticket file

    /**
     * Saves the bill as a text ticket in the "tickets" folder.
     * UNIT V - try-with-resources: the PrintWriter is closed automatically, even on error.
     */
    public String saveTicket(Booking b) throws IOException {
        File folder = new File("tickets");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create the tickets folder.");
        }
        File file = new File(folder, b.getPnr() + ".txt");
        try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
            out.print(b);
        }
        return file.getPath();
    }

    // ------------------------------------------------------------------ getters

    public CoachClass[] getClasses() { return classes; }
    public int getBookingCount() { return bookingCount; }
    public Booking getBooking(int i) { return bookings[i]; }
    public WaitlistQueue getWaitlist() { return waitlist; }
}
