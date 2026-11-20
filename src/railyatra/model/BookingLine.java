package railyatra.model;

/** One line of a booking: a coach class, how many berths, and (after confirmation) which berths. */
public class BookingLine {

    private final CoachClass coachClass;
    private final int quantity;
    private int[] berthNos = new int[0];      // filled in when the booking is confirmed

    public BookingLine(CoachClass coachClass, int quantity) {
        this.coachClass = coachClass;
        this.quantity = quantity;
    }

    public double getLineTotal() {
        return coachClass.getFare() * quantity;
    }

    public void setBerthNos(int[] berthNos) {
        this.berthNos = berthNos;
    }

    public int[] getBerthNos() { return berthNos; }
    public CoachClass getCoachClass() { return coachClass; }
    public int getQuantity() { return quantity; }

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
                coachClass.getName(), quantity, coachClass.getFare(), getLineTotal());
        if (berthNos.length > 0) {
            row += "\n      Berths: " + getBerthText();
        }
        return row;
    }
}
