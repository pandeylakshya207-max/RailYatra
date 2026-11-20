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

    /**
     * NOVELTY 1 - allots real berth numbers.
     * If preferLower is true (senior citizen) we first scan for free LOWER berths,
     * then fill any remaining berths from whatever is free.
     * Call only after the booking has been fully validated.
     */
    public int[] allocate(int qty, boolean preferLower) {
        int[] given = new int[qty];
        int count = 0;
        if (preferLower) {                                           // pass 1: lower berths only
            for (int i = 0; i < occupied.length && count < qty; i++) {
                if (!occupied[i] && isLower(berthType(i + 1))) {
                    occupied[i] = true;
                    given[count++] = i + 1;
                }
            }
        }
        for (int i = 0; i < occupied.length && count < qty; i++) {   // pass 2: any free berth
            if (!occupied[i]) {
                occupied[i] = true;
                given[count++] = i + 1;
            }
        }
        berths -= qty;
        berthsSold += qty;
        return given;
    }

    /** Frees berths again when a booking is cancelled. */
    public void release(int[] berthNos) {
        for (int no : berthNos) {
            occupied[no - 1] = false;
        }
        berths += berthNos.length;
        berthsSold -= berthNos.length;
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
