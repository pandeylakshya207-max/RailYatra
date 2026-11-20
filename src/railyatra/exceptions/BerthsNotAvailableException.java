package railyatra.exceptions;

/**
 * UNIT V - Checked exception.
 * Thrown when a booking asks for more berths than are left in a coach class.
 */
public class BerthsNotAvailableException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int requested;
    private final int available;

    public BerthsNotAvailableException(String className, int requested, int available) {
        super("Only " + available + " berth(s) left in " + className
                + ", but " + requested + " were requested. Booking line rejected.");
        this.requested = requested;
        this.available = available;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
