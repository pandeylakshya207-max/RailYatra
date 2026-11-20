package railyatra.people;

/** UNIT IV - Concrete passenger with a 25% senior citizen concession. */
public class SeniorCitizen extends Passenger {

    public SeniorCitizen(String name) {
        super(name, "Senior Citizen");
    }

    @Override
    public double getDiscountRate() {
        return 0.25;
    }

    /** NOVELTY 1 - senior citizens get lower berths first. */
    @Override
    public boolean prefersLowerBerth() {
        return true;
    }
}
