package railyatra.model;

/**
 * One row of the class list (Sleeper, AC 3 Tier, ...) plus its berth chart.
 * UNIT I   - int / double fields, boolean[] array for the berth chart, type casting.
 * UNIT II  - ternary operator ?: for the LOW / OK status, % operator for the berth layout.
 * UNIT III - constructor with "this".
 *
 * NOVELTY 1 - Smart berth allotment:
 *   every berth has a number and a type (LB = lower, MB = middle, UB = upper, SL/SU = side lower/upper,
 *   W/M/A = window/middle/aisle seat). Senior citizens are given lower berths first.
 */
public class CoachClass {

    public static final int LOW_LIMIT = 10;   // below 10 berths = LOW

    private final int id;
    private final String name;
    private final String category;
    private final double fare;
    private final String coachCode;           // S = Sleeper, B = AC 3 Tier, A = AC 2 Tier ... (as on real tickets)
    private final String[] layout;            // berth pattern that repeats in every bay
    private final boolean[] occupied;         // occupied[0] is berth 1, occupied[1] is berth 2, ...
    private final int totalBerths;
    private int berths;                       // berths still available
    private int berthsSold;

    public CoachClass(int id, String name, String category, double fare, int berths,
                      String coachCode, String[] layout) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.fare = fare;
        this.berths = berths;
        this.totalBerths = berths;
        this.berthsSold = 0;
        this.coachCode = coachCode;
        this.layout = layout;
        this.occupied = new boolean[berths];  // all false = all free
    }

    /** UNIT II - ternary operator. */
    public String getStatus() {
        return berths < LOW_LIMIT ? "LOW" : "OK";
    }

    /** UNIT I - type casting from double to int. */
    public int getOccupancyPercent() {
        return (int) ((berthsSold * 100.0) / totalBerths);
    }

    /** Berth type from its number. UNIT II - % (modulus) makes the bay pattern repeat. */
    public String berthType(int berthNo) {
        return layout[(berthNo - 1) % layout.length];
    }

    public static boolean isLower(String type) {
        return type.equals("LB") || type.equals("SL");
    }

    /** Text printed on the ticket, e.g. "S-12 LB". */
    public String berthLabel(int berthNo) {
        return coachCode + "-" + berthNo + " " + berthType(berthNo);
    }

    // ------------------------------------------------------------------ bays (NOVELTY: family seating)

    /** Berths per bay = one repeat of the layout (8 in Sleeper / AC 3 Tier, 6 in AC 2 Tier ...). */
    public int bayCount() {
        return (occupied.length + layout.length - 1) / layout.length;
    }

    /** Bay number of a berth: berths 1-8 are Bay 1, 9-16 are Bay 2 ... (integer division). */
    public int bayOf(int berthNo) {
        return (berthNo - 1) / layout.length + 1;
    }

    /** "Bay" for sleeping coaches, "Row" for seating coaches. */
    public String groupWord() {
        return hasLowerBerths() ? "Bay" : "Row";
    }

    public boolean hasLowerBerths() {
        for (String t : layout) {
            if (isLower(t)) {
                return true;
            }
        }
        return false;
    }

    private int freeInBay(int bay, boolean lowerOnly) {
        int n = 0;
        for (int i = bay * layout.length; i < (bay + 1) * layout.length && i < occupied.length; i++) {
            if (!occupied[i] && (!lowerOnly || isLower(berthType(i + 1)))) {
                n++;
            }
        }
        return n;
    }

    /**
     * NOVELTY - FAMILY SEATING TOGETHER + lower berths for seniors.
     * Step 1: look for ONE bay that can hold the whole group (for seniors, the one with most free lower berths).
     * Step 2: if no single bay fits, use the bays with the most free berths first, so the group is split
     *         across as FEW bays as possible.
     * Step 3: inside the chosen bays, seniors get lower berths first, then any free berth.
     * Call only after the booking has been fully validated.
     */
    public int[] allocate(int qty, boolean preferLower) {
        int bays = bayCount();
        boolean[] chosen = new boolean[bays];

        // Step 1: one bay for everybody
        int best = -1;
        for (int b = 0; b < bays; b++) {
            if (freeInBay(b, false) >= qty) {
                if (best == -1 || (preferLower
                        && Math.min(freeInBay(b, true), qty) > Math.min(freeInBay(best, true), qty))) {
                    best = b;
                }
                if (!preferLower) {
                    break;                                   // first bay that fits is enough
                }
            }
        }
        if (best != -1) {
            chosen[best] = true;
        } else {
            // Step 2: fewest bays - keep taking the bay with the most free berths
            int stillNeeded = qty;
            while (stillNeeded > 0) {
                int pick = -1;
                for (int b = 0; b < bays; b++) {
                    if (!chosen[b] && freeInBay(b, false) > 0
                            && (pick == -1 || freeInBay(b, false) > freeInBay(pick, false))) {
                        pick = b;
                    }
                }
                chosen[pick] = true;
                stillNeeded -= freeInBay(pick, false);
            }
        }

        // Step 3: allot inside the chosen bays
        int[] given = new int[qty];
        int count = 0;
        if (preferLower) {                                            // pass 1: lower berths only
            for (int i = 0; i < occupied.length && count < qty; i++) {
                if (chosen[i / layout.length] && !occupied[i] && isLower(berthType(i + 1))) {
                    occupied[i] = true;
                    given[count++] = i + 1;
                }
            }
        }
        for (int i = 0; i < occupied.length && count < qty; i++) {    // pass 2: any free berth
            if (chosen[i / layout.length] && !occupied[i]) {
                occupied[i] = true;
                given[count++] = i + 1;
            }
        }
        berths -= qty;
        berthsSold += qty;
        return given;
    }

    // ------------------------------------------------------------------ NOVELTY: berth swap support

    /**
     * A free berth of the wanted type (LB, MB, UB, SL, SU).
     * sameBayOnly = true: only in the bay of nearBerth (stays with family); false: anywhere. -1 if none.
     */
    public int findFreeOfType(String type, int nearBerth, boolean sameBayOnly) {
        int bay = bayOf(nearBerth) - 1;
        int from = sameBayOnly ? bay * layout.length : 0;
        int to = sameBayOnly ? Math.min((bay + 1) * layout.length, occupied.length) : occupied.length;
        for (int i = from; i < to; i++) {
            if (!occupied[i] && berthType(i + 1).equals(type)) {
                return i + 1;
            }
        }
        return -1;
    }

    /** The different berth types in this coach, in layout order, e.g. LB, MB, UB, SL, SU. */
    public String[] berthTypes() {
        String[] tmp = new String[layout.length];
        int n = 0;
        for (String t : layout) {
            boolean seen = false;
            for (int i = 0; i < n; i++) {
                if (tmp[i].equals(t)) {
                    seen = true;
                }
            }
            if (!seen) {
                tmp[n++] = t;
            }
        }
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            out[i] = tmp[i];
        }
        return out;
    }

    public boolean hasType(String type) {
        for (String t : layout) {
            if (t.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /** Moves a passenger from one berth to a free berth of the same class. */
    public void moveBerth(int from, int to) {
        occupied[from - 1] = false;
        occupied[to - 1] = true;
    }

    /** Frees berths again when a booking is cancelled. */
    public void release(int[] berthNos) {
        for (int no : berthNos) {
            occupied[no - 1] = false;
        }
        berths += berthNos.length;
        berthsSold -= berthNos.length;
    }

    /** Read-only view of the berth chart (used by the web seat map). */
    public boolean isOccupied(int berthNo) {
        return occupied[berthNo - 1];
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getFare() { return fare; }
    public int getBerths() { return berths; }
    public int getTotalBerths() { return totalBerths; }
    public int getBerthsSold() { return berthsSold; }

    @Override
    public String toString() {
        return String.format("%-5d %-16s %-8s %,10.2f %7d   %-4s",
                id, name, category, fare, berths, getStatus());
    }
}
