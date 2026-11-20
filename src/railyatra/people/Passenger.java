package railyatra.people;

/**
 * UNIT IV - Abstract class.
 * A Passenger cannot be created directly (new Passenger(...) is a compile error);
 * only its concrete children General and SeniorCitizen can be created.
 * Each child MUST override getDiscountRate().
 */
public abstract class Passenger extends Person {

    private final String type;

    protected Passenger(String name, String type) {
        super(name);               // calls the Person constructor
        this.type = type;
    }

    /** Overridden in each subclass: 0.00 for General, 0.25 for Senior Citizen. */
    public abstract double getDiscountRate();

    /**
     * NOVELTY 1 - should this passenger be given lower berths first?
     * Normal passengers: no. SeniorCitizen overrides this to return true (runtime polymorphism).
     */
    public boolean prefersLowerBerth() {
        return false;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        // (int) cast turns 0.25 * 100 = 25.0 into 25 for display
        return getName() + " (" + type + ", " + (int) (getDiscountRate() * 100) + "% concession)";
    }
}
