package railyatra;

import railyatra.exceptions.BerthsNotAvailableException;
import railyatra.exceptions.CoachClassNotFoundException;
import railyatra.model.Booking;
import railyatra.model.BookingLine;
import railyatra.model.CoachClass;
import railyatra.people.General;
import railyatra.people.SeniorCitizen;
import railyatra.service.ReservationCounter;
import railyatra.util.ItemStack;

/**
 * Innovation: runs the 4 official test cases automatically and prints PASS / FAIL.
 * Run with:  java -cp out railyatra.SelfTest
 */
public class SelfTest {

    private static int passed = 0;
    private static int total = 0;

    public static void main(String[] args) throws Exception {
        ReservationCounter rc = new ReservationCounter();
        System.out.println("RailYatra self-test (official test cases)\n");

        // Case 1: General: 2 x Sleeper, 1 x Second Sitting -> Rs 1,134.00
        ItemStack cart = new ItemStack(10);
        cart.push(new BookingLine(rc.findCoachClass(101), 2));
        cart.push(new BookingLine(rc.findCoachClass(105), 1));
        Booking b1 = rc.confirm(new General("Test One"), cart);
        check("Case 1  General: 2 x Sleeper, 1 x Second Sitting", "1134.00", String.format("%.2f", b1.getTotal()));

        // Case 2: Senior Citizen: 3 x AC 3 Tier, 2 x AC Chair Car -> Rs 3,937.50
        cart = new ItemStack(10);
        cart.push(new BookingLine(rc.findCoachClass(102), 3));
        cart.push(new BookingLine(rc.findCoachClass(106), 2));
        Booking b2 = rc.confirm(new SeniorCitizen("Test Two"), cart);
        check("Case 2  Senior: 3 x AC 3 Tier, 2 x AC Chair Car", "3937.50", String.format("%.2f", b2.getTotal()));

        // Undo check: push 2 lines, pop 1 -> only first line remains
        cart = new ItemStack(10);
        cart.push(new BookingLine(rc.findCoachClass(101), 1));
        cart.push(new BookingLine(rc.findCoachClass(104), 1));
        cart.pop();
        check("Undo    -1 removes the last coach class", "1 line, Sleeper",
                cart.size() + " line, " + cart.toArray()[0].getCoachClass().getName());

        // Case 3: General: 41 x AC 3 Tier -> BerthsNotAvailableException, berths unchanged
        int before = rc.findCoachClass(102).getBerths();
        String result;
        try {
            cart = new ItemStack(10);
            rc.checkBerths(rc.findCoachClass(102), 41, cart);
            result = "no exception";
        } catch (BerthsNotAvailableException e) {
            result = "BerthsNotAvailableException";
        }
        int after = rc.findCoachClass(102).getBerths();
        check("Case 3  General: 41 x AC 3 Tier", "BerthsNotAvailableException, berths unchanged",
                result + (before == after ? ", berths unchanged" : ", BERTHS CHANGED"));

        // Case 4: coach class id 999 -> CoachClassNotFoundException, program continues
        try {
            rc.findCoachClass(999);
            result = "no exception";
        } catch (CoachClassNotFoundException e) {
            result = "CoachClassNotFoundException";
        }
        check("Case 4  Coach class id 999", "CoachClassNotFoundException", result);

        // Window rule: PNR1 token 1 -> Window 1, PNR2 token 2 -> Window 2
        check("Token   odd -> Window 1, even -> Window 2", "Window 1 / Window 2",
                b1.getWindow() + " / " + b2.getWindow());

        // ---------------- NOVELTY checks ----------------
        System.out.println("\nNovelty checks");

        // N1: senior citizen gets lower berths (Case 2 booking: AC 3 Tier pattern LB MB UB LB MB UB SL SU)
        check("N1  Senior gets lower berths in AC 3 Tier", "B-1 LB, B-4 LB, B-7 SL", b2.getLine(0).getBerthText());
        check("N1  General gets berths in order (Sleeper)", "S-1 LB, S-2 MB", b1.getLine(0).getBerthText());

        // N3: AC First Class has 8 berths; ask for 9 -> suggestions exclude it, cheapest first
        CoachClass first = rc.findCoachClass(104);
        CoachClass[] alt = rc.suggestAlternatives(first, 9);
        check("N3  Suggestions cheapest first", "Second Sitting", alt[0].getName());

        // N2: fill AC First Class (8), waitlist 2 more, cancel -> waitlist confirmed automatically
        cart = new ItemStack(10);
        cart.push(new BookingLine(first, 8));
        Booking full = rc.confirm(new General("Fills First"), cart);
        rc.addToWaitlist(new SeniorCitizen("Waiting Senior"), first, 2);
        check("N2  Waitlist holds the request", "1", String.valueOf(rc.getWaitlist().size()));
        double refund = rc.cancel(full);
        check("N2  Refund after 10% charge", String.format("%.2f", full.getTotal() * 0.9), String.format("%.2f", refund));
        Booking[] promoted = rc.promoteWaitlist();
        check("N2  Waitlist auto-confirmed after cancel", "1 promoted, 6 berths left, queue empty",
                promoted.length + " promoted, " + first.getBerths() + " berths left, queue "
                        + (rc.getWaitlist().isEmpty() ? "empty" : "not empty"));

        // Case 1 and 2 totals must be unchanged by the new features
        check("Revenue counts kept cancellation charge", String.format("%.2f",
                b1.getTotal() + b2.getTotal() + full.getTotal() * 0.1 + promoted[0].getTotal()),
                String.format("%.2f", rc.getTotalRevenue()));

        System.out.println("\n" + passed + " / " + total + " checks passed.");
    }

    private static void check(String name, String expected, String actual) {
        total++;
        boolean ok = expected.equals(actual);
        if (ok) {
            passed++;
        }
        System.out.printf("[%s] %-48s expected: %s | got: %s%n", ok ? "PASS" : "FAIL", name, expected, actual);
    }
}
