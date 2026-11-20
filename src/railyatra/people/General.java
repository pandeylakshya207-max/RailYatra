package railyatra.people;

/** UNIT IV - Concrete passenger with no concession. */
public class General extends Passenger {

    public General(String name) {
        super(name, "General");
    }

    @Override
    public double getDiscountRate() {
        return 0.0;
    }
}
