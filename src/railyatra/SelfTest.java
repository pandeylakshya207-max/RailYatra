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
        double fullTotal = full.getTotal();
        double refund = rc.cancel(full);
        check("N2  Refund after 10% charge", String.format("%.2f", fullTotal * 0.9), String.format("%.2f", refund));
        Booking[] promoted = rc.promoteWaitlist();
        check("N2  Waitlist auto-confirmed after cancel", "1 promoted, 6 berths left, queue empty",
                promoted.length + " promoted, " + first.getBerths() + " berths left, queue "
                        + (rc.getWaitlist().isEmpty() ? "empty" : "not empty"));

        // Case 1 and 2 totals must be unchanged by the new features
        check("Revenue counts kept cancellation charge", String.format("%.2f",
                b1.getTotal() + b2.getTotal() + fullTotal * 0.1 + promoted[0].getTotal()),
                String.format("%.2f", rc.getTotalRevenue()));

        // Partial cancellation: General books 2 x Sleeper + 1 x Second Sitting, then cancels 1 Sleeper only
        CoachClass sleeper = rc.findCoachClass(101);
        cart = new ItemStack(10);
        cart.push(new BookingLine(sleeper, 2));
        cart.push(new BookingLine(rc.findCoachClass(105), 1));
        Booking part = rc.confirm(new General("Part Cancel"), cart);
        int sleeperBefore = sleeper.getBerths();
        double partRefund = rc.cancelPart(part, 0, 1);
        check("P   Cancel 1 of 2 Sleeper: refund", "425.25", String.format("%.2f", partRefund));
        check("P   Bill keeps the rest", "661.50, PARTLY", String.format("%.2f", part.getTotal())
                + (part.isPartlyCancelled() ? ", PARTLY" : ", NOT PARTLY"));
        check("P   Only 1 berth freed", String.valueOf(sleeperBefore + 1), String.valueOf(sleeper.getBerths()));

        // ---------------- NEW NOVELTY: family seating, berth swap, priority waitlist ----------------
        System.out.println("\nNew novelty checks");

        // F: family seating together (fresh counter). Sleeper bays are 8 berths each.
        ReservationCounter fam = new ReservationCounter();
        CoachClass sl = fam.findCoachClass(101);
        cart = new ItemStack(10);
        cart.push(new BookingLine(sl, 6));
        Booking f1 = fam.confirm(new General("Family A"), cart);           // takes 6 of Bay 1
        cart = new ItemStack(10);
        cart.push(new BookingLine(sl, 4));
        Booking f2 = fam.confirm(new General("Family B"), cart);           // Bay 1 has only 2 left
        check("F   Family of 6 in one bay", "Bay 1 - all together", f1.getLine(0).getBayText());
        check("F   Family of 4 kept together in next bay", "Bay 2 - all together", f2.getLine(0).getBayText());

        // S: automatic berth swap (fresh counter). AC First Class = LB UB LB UB in each bay of 4.
        ReservationCounter sw = new ReservationCounter();
        CoachClass fc = sw.findCoachClass(104);
        cart = new ItemStack(10);
        cart.push(new BookingLine(fc, 4));
        Booking g1 = sw.confirm(new General("Willing"), cart);             // H-1..H-4, Bay 1
        cart = new ItemStack(10);
        cart.push(new BookingLine(fc, 4));
        Booking g2 = sw.confirm(new SeniorCitizen("Wanter"), cart);        // H-5..H-8, Bay 2 (class full)
        String r1 = sw.requestSwap(g2, 0, 6, "LB");                       // H-6 UB wants LB: nobody willing yet
        check("S   No willing passenger: request waits", "waits", r1.startsWith("No free") ? "waits" : r1);
        String[] served = sw.setSwapWilling(g1, true);                    // Willing agrees -> served at once
        check("S   Agreeing to swap serves it automatically", "1 served, Wanter has an LB in place of H-6",
                served.length + " served, Wanter "
                        + (!g2.getLine(0).holds(6) && (g2.getLine(0).holds(1) || g2.getLine(0).holds(3))
                        ? "has an LB in place of H-6" : "unchanged"));

        // Q: priority waitlist (fresh counter). Fill AC First, waitlist normal first, pregnant second.
        ReservationCounter pq = new ReservationCounter();
        CoachClass pf = pq.findCoachClass(104);
        cart = new ItemStack(10);
        cart.push(new BookingLine(pf, 8));
        Booking filler = pq.confirm(new General("Filler"), cart);
        pq.addToWaitlist(new General("Normal First"), pf, 1, 'N');
        pq.addToWaitlist(new General("Pregnant Second"), pf, 1, 'P');
        pq.cancelPart(filler, 0, 1);                                       // frees ONE berth
        Booking[] got = pq.promoteWaitlist();
        check("Q   Pregnant passenger confirmed first", "Pregnant Second",
                got.length == 1 ? got[0].getPassenger().getName() : got.length + " promoted");

        // C: cancel exactly the chosen berths (fresh counter). AC First: H-1 LB, H-2 UB, H-3 LB, H-4 UB.
        ReservationCounter ch = new ReservationCounter();
        CoachClass chc = ch.findCoachClass(104);
        cart = new ItemStack(10);
        cart.push(new BookingLine(chc, 4));
        Booking pick = ch.confirm(new General("Chooser"), cart);
        ch.cancelChosen(pick, 0, new int[] { 1, 3 });                        // cancel H-1 and H-3 only
        check("C   Cancel chosen berths H-1, H-3", "H-2 UB, H-4 UB kept; H-1, H-3 free",
                pick.getLine(0).getBerthText() + " kept; "
                        + (!chc.isOccupied(1) && !chc.isOccupied(3) ? "H-1, H-3 free" : "NOT freed"));

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
