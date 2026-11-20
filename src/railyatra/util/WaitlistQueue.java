package railyatra.util;

import railyatra.model.WaitlistEntry;

/**
 * NOVELTY 2 - our own circular queue, built on a plain array (no ArrayList / LinkedList).
 * First In, First Out: whoever joined the waitlist first is confirmed first.
 *
 * Compare with ItemStack (LIFO, used for undo):
 *   Stack -> last in, first out  (undo the LAST thing added)
 *   Queue -> first in, first out (serve the FIRST person waiting)
 *
 * "Circular" means front and rear wrap around with % so freed slots at the start are reused.
 */
public class WaitlistQueue {

    private final WaitlistEntry[] items;
    private int front = 0;        // index of the first (oldest) entry
    private int size = 0;         // number of entries in the queue

    public WaitlistQueue(int capacity) {
        items = new WaitlistEntry[capacity];
    }

    public boolean isEmpty() { return size == 0; }
    public boolean isFull() { return size == items.length; }
    public int size() { return size; }

    /** Add at the rear. */
    public void enqueue(WaitlistEntry e) {
        if (isFull()) {
            throw new IllegalStateException("Waitlist is full.");
        }
        int rear = (front + size) % items.length;     // wrap around
        items[rear] = e;
        size++;
    }

    /** Remove from the front. */
    public WaitlistEntry dequeue() {
        if (isEmpty()) {
            return null;
        }
        WaitlistEntry e = items[front];
        items[front] = null;
        front = (front + 1) % items.length;          // wrap around
        size--;
        return e;
    }

    /** Look at the i-th entry from the front without removing it (for display). */
    public WaitlistEntry get(int i) {
        return items[(front + i) % items.length];
    }
}
