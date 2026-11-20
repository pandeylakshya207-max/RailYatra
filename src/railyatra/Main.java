package railyatra;

import java.io.IOException;
import java.util.Scanner;

import railyatra.exceptions.BerthsNotAvailableException;
import railyatra.exceptions.CoachClassNotFoundException;
import railyatra.model.Booking;
import railyatra.model.BookingLine;
import railyatra.model.CoachClass;
import railyatra.model.WaitlistEntry;
import railyatra.people.General;
import railyatra.people.Passenger;
import railyatra.people.SeniorCitizen;
import railyatra.service.ReservationCounter;
import railyatra.util.InputHelper;
import railyatra.util.ItemStack;
import railyatra.util.WaitlistQueue;

/**
 * RailYatra - menu-driven railway reservation counter.
 * UNIT II - do-while + switch for the menu.
 * UNIT V  - try-with-resources (Scanner), nested try, multiple catch, finally.
 */
public class Main {

    private static final int MAX_LINES = 20;     // max coach-class lines in one booking

    private static ReservationCounter counter;
    private static InputHelper input;

    public static void main(String[] args) {
        counter = new ReservationCounter();

        // UNIT V - try-with-resources: the Scanner is closed automatically when the block ends
        try (Scanner sc = new Scanner(System.in)) {
            input = new InputHelper(sc);
            printBanner();

            int choice;
            do {                                              // UNIT II - do-while: menu shows at least once
                printMenu();
                choice = input.readInt("Enter choice (1-7): ", 1, 7);
                switch (choice) {                             // UNIT II - switch
                    case 1:
                        showClassList();
                        break;
                    case 2:
                        newBooking();
                        break;
                    case 3:
                        viewBill();
                        break;
                    case 4:
                        showReport();
                        break;
                    case 5:
                        System.out.println("\nThank you for using RailYatra. Happy journey!");
                        break;
                    case 6:
                        cancelBooking();
                        break;
                    case 7:
                        showWaitlist();
                        break;
                    default:
                        System.out.println("Invalid choice.");   // unreachable, kept for safety
                }
            } while (choice != 5);
        }
    }

    // ------------------------------------------------------------------ 1. class list

    private static void showClassList() {
        System.out.println("\n================== CLASS LIST ==================");
        System.out.printf("%-5s %-16s %-8s %10s %7s   %s%n", "Id", "Coach class", "Category", "Fare (Rs)", "Berths", "Status");
        System.out.println("------------------------------------------------------------");
        for (CoachClass c : counter.getClasses()) {
            System.out.println(c);
        }
        System.out.println("(LOW = fewer than " + CoachClass.LOW_LIMIT + " berths left)");
    }

    // ------------------------------------------------------------------ 2. new booking

    private static void newBooking() {
        System.out.println("\n================== NEW BOOKING ==================");
        String name = input.readName("Passenger name: ");
        char type = input.readChoice("Passenger type (G = General, S = Senior Citizen): ", "GS");
        Passenger passenger = (type == 'G') ? new General(name) : new SeniorCitizen(name);
        System.out.println("Passenger: " + passenger);

        ItemStack cart = new ItemStack(MAX_LINES);
        boolean finished = false;

        try {                                                        // outer try
            showClassList();
            System.out.println("\nEnter coach class id and quantity.  -1 = undo last,  0 = finish");

            while (!finished) {
                int id = input.readInt("\nCoach class id: ");

                if (id == 0) {
                    if (cart.isEmpty()) {
                        System.out.println("No berths added, so no bill was made.");
                        return;                                      // finally still runs
                    }
                    finished = true;
                } else if (id == -1) {
                    BookingLine removed = cart.pop();
                    System.out.println(removed == null
                            ? "  Nothing to undo."
                            : "  Undo: removed " + removed.getQuantity() + " x " + removed.getCoachClass().getName());
                } else if (cart.isFull()) {
                    System.out.println("  ! A booking can have at most " + MAX_LINES + " lines. Enter 0 to finish.");
                } else {
                    CoachClass c = null;
                    int qty = 0;
                    try {                                            // nested try
                        c = counter.findCoachClass(id);
                        qty = input.readInt("Quantity for " + c.getName() + ": ");
                        if (qty <= 0) {
                            System.out.println("  ! Quantity must be 1 or more (you entered " + qty + ").");
                            continue;
                        }
                        counter.checkBerths(c, qty, cart);
                        cart.push(new BookingLine(c, qty));
                        System.out.printf("  Added %d x %s  (lines in booking: %d)%n", qty, c.getName(), cart.size());
                    } catch (CoachClassNotFoundException e) {        // multiple catch: 1
                        System.out.println("  ! CoachClassNotFoundException: " + e.getMessage());
                    } catch (BerthsNotAvailableException e) {        // multiple catch: 2
                        System.out.println("  ! BerthsNotAvailableException: " + e.getMessage());
                        System.out.println("    No berths were changed.");
                        offerAlternatives(passenger, c, qty);        // NOVELTY 2 + 3
                    }
                }
            }

            Booking booking = counter.confirm(passenger, cart);
            System.out.println();
            System.out.print(booking);

            try {
                String path = counter.saveTicket(booking);
                System.out.println("Ticket saved to " + path);
            } catch (IOException e) {
                System.out.println("(Bill shown above, but ticket file could not be saved: " + e.getMessage() + ")");
            }

        } catch (BerthsNotAvailableException | IllegalStateException e) {   // multi-catch
            System.out.println("  ! Booking rejected: " + e.getMessage());
            System.out.println("    No berths were changed.");
        } finally {
            System.out.println("Returning to main menu...");
        }
    }

    /**
     * NOVELTY 3 - suggest other classes that have enough berths (cheapest first).
     * NOVELTY 2 - offer to put the passenger on the waitlist for the class they wanted.
     */
    private static void offerAlternatives(Passenger passenger, CoachClass full, int qty) {
        CoachClass[] options = counter.suggestAlternatives(full, qty);
        if (options.length > 0) {
            System.out.println("    Suggestion: these classes have " + qty + " berth(s) free (cheapest first):");
            for (CoachClass o : options) {
                double diff = o.getFare() - full.getFare();
                System.out.printf("      %d  %-16s Rs. %,9.2f  (%s Rs. %,.2f per berth)%n",
                        o.getId(), o.getName(), o.getFare(), diff < 0 ? "saves" : "costs", Math.abs(diff));
            }
        }
        if (qty <= full.getTotalBerths()) {
            char yes = input.readChoice("    Join the waitlist for " + qty + " x " + full.getName() + "? (Y/N): ", "YN");
            if (yes == 'Y') {
                try {
                    WaitlistEntry e = counter.addToWaitlist(passenger, full, qty);
                    System.out.println("    Added to waitlist as " + e.getWlNo()
                            + ". It will be confirmed automatically when berths are cancelled.");
                } catch (IllegalStateException ex) {
                    System.out.println("    ! " + ex.getMessage());
                }
            }
        }
    }

    // ------------------------------------------------------------------ 6. cancel (NOVELTY 2)

    private static void cancelBooking() {
        System.out.println("\n================== CANCEL BOOKING ==================");
        if (counter.getBookingCount() == 0) {
            System.out.println("No bookings yet today.");
            return;
        }
        String pnr = input.readLine("Enter booking id to cancel (e.g. PNR1): ");
        Booking b = counter.findBooking(pnr);
        if (b == null) {
            System.out.println("  ! No booking found with id '" + pnr + "'.");
            return;
        }
        if (b.isCancelled()) {
            System.out.println("  ! " + b.getPnr() + " is already cancelled.");
            return;
        }
        System.out.print(b);
        char yes = input.readChoice("Cancel this booking? 10% charge applies. (Y/N): ", "YN");
        if (yes == 'N') {
            System.out.println("Not cancelled.");
            return;
        }
        double refund = counter.cancel(b);
        System.out.printf("%s cancelled. Refund: Rs. %,.2f. Berths returned to the chart.%n", b.getPnr(), refund);

        Booking[] promoted = counter.promoteWaitlist();
        if (promoted.length == 0) {
            System.out.println("No waitlisted passenger could be confirmed with the freed berths.");
        }
        for (Booking p : promoted) {
            System.out.println("\n*** Waitlist confirmed automatically ***");
            System.out.print(p);
            try {
                System.out.println("Ticket saved to " + counter.saveTicket(p));
            } catch (IOException e) {
                System.out.println("(Ticket file could not be saved: " + e.getMessage() + ")");
            }
        }
    }

    // ------------------------------------------------------------------ 7. waitlist (NOVELTY 2)

    private static void showWaitlist() {
        System.out.println("\n================== WAITLIST (first come, first served) ==================");
        WaitlistQueue q = counter.getWaitlist();
        if (q.isEmpty()) {
            System.out.println("Waitlist is empty.");
            return;
        }
        for (int i = 0; i < q.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + q.get(i));
        }
    }

    // ------------------------------------------------------------------ 3. view old bill

    private static void viewBill() {
        System.out.println("\n================== VIEW BILL ==================");
        if (counter.getBookingCount() == 0) {
            System.out.println("No bookings yet today.");
            return;
        }
        String pnr = input.readLine("Enter booking id (e.g. PNR1): ");
        Booking b = counter.findBooking(pnr);
        if (b == null) {
            System.out.println("  ! No booking found with id '" + pnr + "'. Valid ids: PNR1 to PNR" + counter.getBookingCount());
        } else {
            System.out.print(b);
        }
    }

    // ------------------------------------------------------------------ 4. report

    private static void showReport() {
        System.out.println("\n================ END-OF-DAY BOOKING REPORT ================");
        int n = counter.getBookingCount();
        System.out.println("Total bookings : " + n);
        System.out.printf("Total revenue  : Rs. %,.2f  (includes 10%% charge kept on cancellations)%n",
                counter.getTotalRevenue());

        CoachClass top = counter.getMostPopular();
        System.out.println("Most popular   : " + (top == null ? "none yet" : top.getName() + " (" + top.getBerthsSold() + " berths)"));

        if (n == 0) {
            return;
        }

        // Extra: split by passenger type, concession given, cancellations, waitlist
        int seniorCount = 0;
        int cancelledCount = 0;
        double concession = 0;
        double refunds = 0;
        for (int i = 0; i < n; i++) {
            Booking b = counter.getBooking(i);
            if (b.isCancelled()) {
                cancelledCount++;
                refunds += b.getRefund();
                continue;
            }
            if (b.getPassenger() instanceof SeniorCitizen) {
                seniorCount++;
            }
            concession += b.getDiscount();
        }
        int active = n - cancelledCount;
        System.out.println("Active / cancelled        : " + active + " / " + cancelledCount);
        System.out.println("General / Senior (active) : " + (active - seniorCount) + " / " + seniorCount);
        System.out.printf("Senior concession given   : Rs. %,.2f%n", concession);
        System.out.printf("Refunds paid              : Rs. %,.2f%n", refunds);
        System.out.println("Still on waitlist         : " + counter.getWaitlist().size());

        // Innovation: occupancy bar chart per coach class
        System.out.println("\nOccupancy by coach class (each # = 5%)");
        for (CoachClass c : counter.getClasses()) {
            int pct = c.getOccupancyPercent();
            System.out.printf("  %-16s |%-20s| %3d%%  (%d/%d sold)%n",
                    c.getName(), "#".repeat(pct / 5), pct, c.getBerthsSold(), c.getTotalBerths());
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void printBanner() {
        System.out.println("==================================================");
        System.out.println("        RAILYATRA  -  Railway Reservation Counter");
        System.out.println("==================================================");
    }

    private static void printMenu() {
        System.out.println("\n------------- MAIN MENU -------------");
        System.out.println(" 1  View class list");
        System.out.println(" 2  New booking");
        System.out.println(" 3  View bill");
        System.out.println(" 4  Booking report");
        System.out.println(" 5  Exit");
        System.out.println(" ---- RailYatra extras ----");
        System.out.println(" 6  Cancel booking (refund + auto-confirm waitlist)");
        System.out.println(" 7  View waitlist");
    }
}
