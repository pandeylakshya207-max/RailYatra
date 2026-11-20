package railyatra.exceptions;

/**
 * UNIT V - Checked exception (extends Exception, NOT RuntimeException).
 * Thrown when the clerk types a coach class id that is not in the class list.
 * Because it is checked, every method that can throw it must declare "throws".
 */
public class CoachClassNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int badId;

    public CoachClassNotFoundException(int badId) {
        super("Coach class id " + badId + " does not exist. Please check the class list.");
        this.badId = badId;
    }

    public int getBadId() {
        return badId;
    }
}
