package railyatra.util;

import railyatra.model.BookingLine;

/**
 * UNIT III - Our own stack, built on a plain array (no ArrayList allowed).
 * Last In, First Out: the last coach class added is the first one removed when the clerk enters -1.
 */
public class ItemStack {

    private final BookingLine[] items;
    private int top;                 // number of items currently on the stack

    public ItemStack(int capacity) {
        this.items = new BookingLine[capacity];
        this.top = 0;
    }

    public boolean isEmpty() {
        return top == 0;
    }

    public boolean isFull() {
        return top == items.length;
    }

    public void push(BookingLine line) {
        if (isFull()) {
            throw new IllegalStateException("Booking cannot have more than " + items.length + " lines.");
        }
        items[top++] = line;
    }

    public BookingLine pop() {
        if (isEmpty()) {
            return null;
        }
        BookingLine removed = items[--top];
        items[top] = null;           // let the garbage collector free it
        return removed;
    }

    public int size() {
        return top;
    }

    /** Returns the underlying array; only the first size() slots are valid. */
    public BookingLine[] toArray() {
        return items;
    }

    /** Total quantity of one coach class already in this booking (used for the berth check). */
    public int quantityOf(int coachClassId) {
        int sum = 0;
        for (int i = 0; i < top; i++) {
            if (items[i].getCoachClass().getId() == coachClassId) {
                sum += items[i].getQuantity();
            }
        }
        return sum;
    }
}
