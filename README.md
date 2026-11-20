# RailYatra — Railway Reservation Counter (Java Hackathon, Problem 13)

A menu-driven Java 17 console program for a small railway station's reservation counter.
It uses arrays only (no ArrayList or HashMap), as the rules require.

## How to run

**Windows**: double-click `run.bat`, or in a terminal inside this folder run:
```
javac -d out src\railyatra\*.java src\railyatra\exceptions\*.java src\railyatra\model\*.java src\railyatra\people\*.java src\railyatra\service\*.java src\railyatra\util\*.java
java -cp out railyatra.Main
```

**Mac / Linux**:
```
javac -d out $(find src -name "*.java")
java -cp out railyatra.Main
```

**Run the official test cases and novelty checks automatically** (16 PASS/FAIL checks):
```
java -cp out railyatra.SelfTest
```

In IntelliJ / Eclipse / VS Code, open the folder, mark `src` as the source root, and run `railyatra.Main`.

## Menu
```
1  View class list     -> id, name, category, fare, berths, LOW/OK
2  New booking         -> name, type (G/S), then id + quantity; -1 = undo, 0 = finish
3  View bill           -> enter PNR1 / pnr1 / 1
4  Booking report      -> total bookings, revenue, most popular class (+ extras)
5  Exit
---- RailYatra extras ----
6  Cancel booking      -> whole booking OR only some berths; 90% refund, berths go back, waitlist confirmed automatically
7  View waitlist       -> WL1, WL2 ... in first-come order
```
Options 1-5 are exactly as the problem statement asks; 6 and 7 are our novelty features.

## Project structure
```
src/railyatra/
├── Main.java                        menu (do-while + switch), booking flow, nested try / multiple catch / finally
├── SelfTest.java                    runs the 4 official test cases (PASS/FAIL)
├── exceptions/
│   ├── CoachClassNotFoundException  checked exception (id 999)
│   └── BerthsNotAvailableException  checked exception (too many berths)
├── model/
│   ├── Billable.java                interface: subtotal, discount, GST, total
│   ├── Booking.java                 PNR counter (static), recursive subtotal, final getTotal(), token & 1
│   ├── BookingLine.java             one coach class + quantity + allotted berth numbers
│   ├── WaitlistEntry.java           WL1, WL2 ... (novelty 2)
│   └── CoachClass.java              one row of the class list, ?: LOW/OK, boolean[] berth chart (novelty 1)
├── people/
│   ├── Person.java                  base class, final getName()
│   ├── Passenger.java               abstract, abstract getDiscountRate()
│   ├── General.java                 0% concession
│   └── SeniorCitizen.java           25% concession
├── service/
│   └── ReservationCounter.java      CoachClass[] + Booking[], validation, cancel, waitlist promotion, suggestions, report
└── util/
    ├── InputHelper.java             safe input, overloaded readInt()
    ├── ItemStack.java               our own array-based stack for undo (-1)
    └── WaitlistQueue.java           our own circular queue for the waitlist (novelty 2)
```

## Billing
Subtotal → minus concession (General 0%, Senior Citizen 25%) → plus 5% GST on the discounted amount → 2 decimals.

| Case | Order | Expected | Our output |
|---|---|---|---|
| 1 | General: 2 × Sleeper, 1 × Second Sitting | ₹1,134.00 | ₹1,134.00 ✅ |
| 2 | Senior Citizen: 3 × AC 3 Tier, 2 × AC Chair Car | ₹3,937.50 | ₹3,937.50 ✅ |
| 3 | General: 41 × AC 3 Tier | BerthsNotAvailableException, berths unchanged | ✅ |
| 4 | Coach class id 999 | CoachClassNotFoundException, program continues | ✅ |

## Novelty (our own additions)
**N1. Smart berth allotment, with senior citizens first.** Every passenger gets a real berth number and type
(`S-12 LB`, `B-7 SL`, `C-3 W`), using real Indian Railways bay layouts (LB MB UB LB MB UB SL SU).
Senior citizens are given **lower berths first** (`Passenger.prefersLowerBerth()` is overridden in `SeniorCitizen`).
Built with a `boolean[]` berth chart per coach class.

**N2. Waitlist with automatic confirmation.** When a class is full, the passenger can join the waitlist (WL1, WL2 ...).
When anyone cancels the whole booking or just some berths (menu 6, 10% charge, 90% refund), freed berths are given to the waitlist in
**first-come, first-served** order and a new PNR is printed automatically. The waitlist is **our own circular queue on an array**
(FIFO), which pairs with our own stack for undo (LIFO).

**N3. Alternative class suggestions.** If a class doesn't have enough berths, RailYatra lists the classes that do,
**cheapest first** (bubble sort on an array), with how much each one saves or costs per berth.

## Other extras
1. **Ticket file**: every confirmed booking is saved as `tickets/PNR<n>.txt`, like a printed ticket.
2. **Smart berth check**: the cart counts what is already in this booking (adding 30 + 15 AC 3 Tier when 40 are left is caught).
3. **"You saved Rs. X" line** on senior citizen bills.
4. **Richer end-of-day report**: General vs Senior split, total concession given, and an occupancy bar chart per coach class.
5. **Automatic test runner** (`SelfTest`) for all official test cases.
6. **Forgiving bill lookup**: `PNR2`, `pnr2` or `2` all work.
7. **Validated input everywhere**: letters, negative/zero quantities, wrong menu numbers, empty names, wrong type letters, and closed input never crash the program.
