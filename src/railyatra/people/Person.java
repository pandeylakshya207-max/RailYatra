package railyatra.people;

/**
 * UNIT IV - Top of the inheritance chain:  Person -> abstract Passenger -> General / SeniorCitizen
 */
public class Person {

    protected String name;

    public Person(String name) {
        this.name = name;          // "this" separates the field from the parameter
    }

    // UNIT IV - final method: no subclass is allowed to override how a name is read.
    public final String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
