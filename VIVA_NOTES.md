# RailYatra — Viva Notes (every team member must read this)

At every checkpoint the mentor picks **who** explains. If that person can't explain, the checkpoint gets **0**.
Each section below matches one checkpoint: what to show, where it is in the code, and short answers to likely questions.

---

## CP1 (0:20): Class list loaded in the array and printed
**Show:** menu option 1.
**Where:** `ReservationCounter()` constructor builds `CoachClass[] classes`; `CoachClass.toString()` prints a row.

| Question | Answer |
|---|---|
| Which data types did you use? | `int` (id, berths), `double` (fare, totals), `char` (G/S choice in `InputHelper.readChoice`), `boolean` (`finished` flag in `Main.newBooking`), `String`. |
| Where is type casting? | `(int) ((berthsSold * 100.0) / totalBerths)` in `CoachClass.getOccupancyPercent()`; `(int) (getDiscountRate() * 100)` in `Passenger.toString()`. That is **explicit (narrowing)** casting, double → int, which drops the decimals. `berthsSold * 100.0` is **implicit (widening)**: int → double automatically. |
| What is an array? | A fixed-size block of same-type elements accessed by index 0..n-1. We use `CoachClass[]`, `Booking[]`, `BookingLine[]`. |
| Why not ArrayList? | The rules forbid it. Arrays have fixed size, so we keep our own counter (`bookingCount`, `top`). |

---

## CP2 (0:40): Main menu loop and token counter
**Show:** the menu repeating; book twice and show Window 1 then Window 2.
**Where:** `Main.main()` has `do { ... switch (choice) ... } while (choice != 5);`. `Booking.getWindow()` has `(token & 1) == 1 ? "Window 1" : "Window 2"`. `CoachClass.getStatus()` has `berths < LOW_LIMIT ? "LOW" : "OK"`.

| Question | Answer |
|---|---|
| Why do-while, not while? | do-while runs the body **at least once**, and a menu must always show at least once. |
| `&` vs `&&`? | `&` is **bitwise AND** on numbers (and a non-short-circuit AND on booleans). `&&` is **logical AND** that **short-circuits** (skips the right side if the left side is false). |
| How does `token & 1` find odd/even? | An odd number's last binary bit is 1. 5 = `101`, so `101 & 001 = 1` → odd. 4 = `100`, so `100 & 001 = 0` → even. |
| What is `?:`? | The ternary operator: `condition ? valueIfTrue : valueIfFalse`, a one-line if-else that returns a value. |
| What happens without `break` in switch? | Fall-through: execution continues into the next case. |

---

## CP3 (1:05): Test cases 1 and 2, and undo
**Show:** book Case 1 (General: 101 × 2, 105 × 1 → **1,134.00**) and Case 2 (Senior: 102 × 3, 106 × 2 → **3,937.50**); add a line, enter `-1`, show it was removed. Or run `java -cp out railyatra.SelfTest`.
**Where:**
- `static int counter` in `Booking` → PNR1, PNR2 …
- `this` in every constructor (`this.fare = fare`)
- Overloading: `InputHelper.readInt(String)` and `readInt(String, int, int)`
- Recursion: `Booking.subtotalFrom(int index)`
- Stack: `util/ItemStack.java` (`push`, `pop`, `isEmpty`), built on an array

| Question | Answer |
|---|---|
| What does `static` mean? | It belongs to the **class**, not to one object. All bookings share one `counter`, so each new booking gets the next number. |
| What does `this` do? | It refers to the current object. `this.fare = fare` means "field fare = parameter fare". |
| Overloading vs overriding? | Overloading: **same name, different parameters, same class**, decided at **compile time**. Overriding: **same signature in a subclass**, decided at **run time**. |
| Explain your recursion. | `subtotalFrom(i)` = line i's total + `subtotalFrom(i+1)`. **Base case**: `i == lineCount` returns 0. Without a base case you get a StackOverflowError. |
| Why a stack for undo? | Undo removes the **last** thing added, which is **LIFO** (Last In, First Out). `pop()` takes from `items[--top]`. |
| Calculation for Case 2? | 3×1150 + 2×775 = 5000 → minus 25% = 3750 → plus 5% GST = 187.50 → **3937.50** |

---

## CP4 (1:30): Packages and passenger classes, then a surprise change in 10 minutes without AI
**Where:** packages `railyatra.model`, `people`, `exceptions`, `service`, `util`.
Inheritance: `Person → abstract Passenger → General / SeniorCitizen`. `super(name)` and `super(name, "General")` call the parent constructor.
Overriding: `getDiscountRate()` in General (0.0) and SeniorCitizen (0.25).
Interface: `model/Billable.java`, implemented by `Booking`.
Final methods: `Person.getName()` and `Booking.getTotal()`. `toString()` is overridden in Person, Passenger, CoachClass, BookingLine and Booking.

| Question | Answer |
|---|---|
| What does `super` do? | It calls the parent's constructor (`super(...)`) or a parent method (`super.method()`). It must be the first line in a constructor. |
| Abstract class vs interface? | An abstract class can have fields, constructors and normal methods plus abstract ones; a class can extend **only one**. An interface is a pure contract (abstract methods, plus default/static); a class can implement **many**. We used abstract `Passenger` because all passengers share a name and type, and the `Billable` interface because billing is a capability. |
| Why is Passenger abstract? | A "passenger with no type" makes no sense, so `new Passenger()` is not allowed. Each child must decide its discount. |
| What does `final` do? | A final **variable** cannot change, a final **method** cannot be overridden, and a final **class** cannot be extended. |
| Where is polymorphism? | `Passenger p = new SeniorCitizen(...)`; then `p.getDiscountRate()` calls SeniorCitizen's version at **run time**. |
| Why packages? | They group related classes, avoid name clashes and control access. Each file starts with `package railyatra.xxx;` and uses `import`. |

### Surprise change practice (do these by hand before the hackathon!)
| Likely change | Where to edit |
|---|---|
| GST changes to 12% | `Booking.java`: `GST = 0.12` |
| Senior concession changes to 40% | `SeniorCitizen.getDiscountRate()` returns `0.40` |
| Add a new passenger type, e.g. Student 10% | Copy `General.java` to `Student.java`, change the name and return `0.10`; in `Main.newBooking` change to `readChoice("... T = Student): ", "GST")` and replace the ternary with `if (type == 'G') ... else if (type == 'S') ... else passenger = new Student(name);` |
| Add a new coach class (e.g. 107 Executive) | Add one line in the `ReservationCounter()` constructor array |
| LOW limit becomes 15 | `CoachClass.LOW_LIMIT = 15` |
| Odd tokens → Window 2 instead | Swap the strings in `Booking.getWindow()` |
| Max berths per booking = 6 | In `Main.newBooking`, after reading qty: `if (qty + cart.quantityOf(c.getId()) > 6) { print; continue; }` |
| Booking id prefix changes to TKT | `Booking` constructor `"TKT" + counter`, and `findBooking` prefix |

---

## CP5 (1:45–2:00): Test cases 3 and 4, report, AI log
**Show:** book 41 × AC 3 Tier → BerthsNotAvailableException, then show the class list with berths unchanged. Enter id 999 → CoachClassNotFoundException and the program continues. Show menu option 4. Show AI_LOG.txt.
**Where (all in `Main.newBooking`):** outer `try`, **nested** `try` inside the loop, two separate `catch` blocks (**multiple catch**), a multi-catch `catch (BerthsNotAvailableException | IllegalStateException e)`, and `finally` printing "Returning to main menu...".
`throw new CoachClassNotFoundException(id)` and `throws` are in `ReservationCounter.findCoachClass`.
**try-with-resources**: `try (Scanner sc = new Scanner(System.in))` in `Main.main`, and `try (PrintWriter out = ...)` in `ReservationCounter.saveTicket`.

| Question | Answer |
|---|---|
| Checked vs unchecked exception? | Checked exceptions extend `Exception`, and the compiler **forces** you to catch or declare them (ours, IOException). Unchecked exceptions extend `RuntimeException` and are not forced (NumberFormatException, ArrayIndexOutOfBounds). |
| `throw` vs `throws`? | `throw` **raises** one exception object inside a method. `throws` in the method **signature** **declares** that the method may raise it. |
| When does `finally` run? | **Always**: after try, after catch, and even after a `return` inside try. We use it for the "Returning to main menu" message. |
| What is try-with-resources? | `try (Resource r = ...)` closes `r` automatically (it must implement `AutoCloseable`), so no `close()` in finally is needed. |
| How is "rejected booking doesn't change berths" guaranteed? | `confirm()` does **two passes**: first it validates all lines and throws if any fail, and only then deducts berths. Each line is also checked when added, counting what is already in the cart. |
| How does the program never crash on letters? | `InputHelper.readInt` catches `NumberFormatException` and asks again; quantities ≤ 0 are rejected with a message. |
| How is "most popular" found? | Loop over `CoachClass[]` and keep the one with the highest `berthsSold`. |

---

## NOVELTY: be ready to demo and explain all three

| # | Feature | Demo (in the program) | Where in code | Concepts it shows |
|---|---|---|---|---|
| N1 | Smart berth allotment, seniors get lower berths | Book 3 × AC 3 Tier as Senior → `B-1 LB, B-4 LB, B-7 SL` | `CoachClass.allocate()`, `berthType()`, `Passenger.prefersLowerBerth()` overridden in `SeniorCitizen` | `boolean[]` array, `%` operator, two-pass search, **runtime polymorphism** |
| N2 | Waitlist + cancellation + auto-confirm | Book 8 × AC First Class (General); new booking asks 3 × 104 → join waitlist (Y); menu 6 → cancel PNR1 → WL1 confirmed as new PNR | `util/WaitlistQueue.java`, `ReservationCounter.cancel()`, `promoteWaitlist()`, `Booking.cancel()` | **own circular queue (FIFO)**, overloaded constructor in `Booking`, static WL counter |
| N3 | Alternative class suggestions | Ask more berths than left → list of classes with enough berths, cheapest first | `ReservationCounter.suggestAlternatives()` | **bubble sort** on an array, exact-size array copy |

| Likely question | Answer |
|---|---|
| Why is this novel? | Real railway counters do these three things (berth types, senior lower-berth quota, waitlist), but the hackathon template doesn't ask for them. They solve the real problem in the statement: concessions missed and coaches overbooked. |
| Stack vs queue? | A stack is **LIFO**, last in first out, so undo removes the last coach class added. A queue is **FIFO**, first in first out, so the first person on the waitlist is confirmed first. |
| What is "circular"? | When `rear` reaches the end of the array it wraps to index 0 using `% capacity`, so slots freed by `dequeue` are reused. |
| How does a senior get lower berths? | `allocate()` makes two passes over `boolean[] occupied`. Pass 1 (only if `prefersLowerBerth()` is true) takes free LB/SL berths; pass 2 fills any remaining free berths. |
| How is a berth type found? | `layout[(berthNo - 1) % layout.length]`. AC 3 Tier repeats LB MB UB LB MB UB SL SU every 8 berths, so berth 12 → index 3 → LB. |
| What if the waitlisted request is still too big? | `promoteWaitlist()` dequeues each entry once; if it doesn't fit, it is enqueued again at the rear, so order is kept. |
| Do the new features change the official answers? | No. Test cases 1–4 still give 1,134.00, 3,937.50 and the two exceptions; SelfTest checks this. Case 3 still throws the exception and leaves berths unchanged; joining the waitlist is optional afterwards. |
| Why is 41 × AC 3 Tier not offered the waitlist? | The class has only 40 berths in total, so 41 can never be confirmed. |

---

## 30-second pitch for the evaluator
"Small stations still fill reservation forms by hand, so senior concessions are missed and coaches get overbooked.
RailYatra is a Java console counter that tracks berths live, blocks overbooking with custom checked exceptions,
applies the 25% senior concession and 5% GST automatically, and gives each booking a PNR with an odd/even window token.
Our novelty: it allots real berth numbers and gives senior citizens lower berths first, it runs a waitlist that is
confirmed automatically when someone cancels, and it suggests cheaper classes when a class is full.
It uses all five units: arrays, operators, OOP with a recursive bill, our own stack for undo and our own queue for the waitlist, inheritance with an interface, and exception handling. All four official test cases pass."
