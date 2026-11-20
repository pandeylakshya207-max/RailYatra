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

**Run the official test cases and novelty checks automatically** (22 PASS/FAIL checks):
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
6  Cancel booking      -> whole booking OR exactly the berths you choose; 90% refund, berths go back, waitlist confirmed automatically
7  View waitlist       -> WL1, WL2 ... in serving order (priority first)
8  Berth swap          -> ask for a berth type (served automatically), or agree to swap
```
Options 1-5 are exactly as the problem statement asks; 6, 7 and 8 are our additions.

## Web version (optional extra, not the hackathon submission)
The hackathon submission is the console program above. As an extra, the **same Java classes** also run as a web app,
using only the JDK's built-in web server (`com.sun.net.httpserver`), with no libraries. All rules (family seating,
berth swap, priority waitlist, refunds) still run in `ReservationCounter`; the page in `web/static/index.html` only displays them.

**Windows:** double-click `run-web.bat` (it compiles, starts the server and opens the browser), or:
```
javac -d out-web (Get-ChildItem -Recurse -Filter *.java src, web\src).FullName
java -cp out-web railyatra.web.WebServer
```
Then open **http://localhost:8080**. Press Ctrl+C in the terminal to stop it.

Tabs: Book · Seat map (every berth, bay by bay) · Bills · Cancel (whole or some berths) · Waitlist (priority order) · Berth swap · Report.

### Live on Render
The web version is deployed from this repo with the `Dockerfile` (Java 17) and `render.yaml`:
[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/pandeylakshya207-max/RailYatra)

On the free plan the server sleeps after 15 minutes without visitors; the first visit after that takes about a minute.
Bookings are kept in memory, so they reset when the server restarts.

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
│   ├── WaitlistEntry.java           WL1, WL2 ... with priority level
│   ├── SwapRequest.java             SW1, SW2 ... berth-swap requests
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

## Novelty: features that railway booking does not offer today
**1. Family seating together.** Today a family booked on one ticket is often scattered across bays or coaches.
RailYatra puts every berth of one booking line **in the same bay** (the 8 berths that face each other in Sleeper / AC 3 Tier).
If no single bay has room, it uses the **fewest bays possible**, and the bill says so ("Bay 2 - all together" or
"Bays 1, 2 - split across 2 bays"). Code: `CoachClass.allocate()` (3 steps: one bay -> fewest bays -> lower berths for seniors).

**2. Automatic berth swap (menu 8).** Today someone stuck on an upper berth has to ask strangers on the train to swap.
In RailYatra every passenger is asked at booking: *"Happy to swap your berth if a fellow passenger needs it?"*
A passenger who wants a different berth just names the **type** they want (LB, MB, UB, SL, SU). RailYatra serves it **automatically**:
1. a free berth of that type in the same compartment (bay); 2. a passenger in the same compartment who agreed to swap;
3. a free berth anywhere in the coach; 4. a passenger anywhere in the coach who agreed to swap.
Both tickets are updated with a note. A request that cannot be served yet waits (SW1, SW2 ...) and is matched **automatically**
when a willing passenger books, someone agrees later (menu 8 → A), or berths are cancelled. A senior who agreed to swap is never
moved off a lower berth. Code: `model/SwapRequest.java`, `ReservationCounter.requestSwap()`, `tryServe()`, `servePendingSwaps()`.

**3. Priority waitlist.** A normal waitlist is strictly first come, first served. In RailYatra, when a cancellation frees berths,
**pregnant passengers and people with a disability or medical need are confirmed first, then senior citizens, then everyone else**
(first come, first served inside each level). Built on our own circular queue: `ReservationCounter.promoteWaitlist()` walks the
queue once per priority level.

## Features that a real counter has (we built them too)
- Real berth numbers with types (LB, MB, UB, SL, SU), lower berths first for senior citizens.
- Waitlist (WL1, WL2 ...) with automatic confirmation when someone cancels.
- Cancel the whole booking or only some berths (90% refund, 10% charge).
- Cheaper class suggestions when a class is full (bubble sort on an array).

## Other extras
1. **Ticket file**: every confirmed booking is saved as `tickets/PNR<n>.txt`, like a printed ticket.
2. **Smart berth check**: the cart counts what is already in this booking (adding 30 + 15 AC 3 Tier when 40 are left is caught).
3. **"You saved Rs. X" line** on senior citizen bills.
4. **Richer end-of-day report**: General vs Senior split, total concession given, and an occupancy bar chart per coach class.
5. **Automatic test runner** (`SelfTest`) for all official test cases.
6. **Forgiving bill lookup**: `PNR2`, `pnr2` or `2` all work.
7. **Validated input everywhere**: letters, negative/zero quantities, wrong menu numbers, empty names, wrong type letters, and closed input never crash the program.
