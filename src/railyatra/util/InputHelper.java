package railyatra.util;

import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Safe keyboard input. Point 7 of the problem: never crash on wrong input.
 * Letters where a number is expected are caught here and the user is asked again.
 *
 * UNIT III - method overloading: readInt(String) and readInt(String, int, int)
 * have the same name but different parameter lists.
 */
public class InputHelper {

    private final Scanner sc;

    public InputHelper(Scanner sc) {
        this.sc = sc;
    }

    /** Reads one line. If input ends (Ctrl+D / end of file) we exit cleanly instead of crashing. */
    public String readLine(String prompt) {
        System.out.print(prompt);
        try {
            return sc.nextLine().trim();
        } catch (NoSuchElementException e) {
            System.out.println("\nInput closed. Exiting RailYatra.");
            System.exit(0);
            return "";
        }
    }

    /** Overload 1: any whole number. */
    public int readInt(String prompt) {
        while (true) {
            String text = readLine(prompt);
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                System.out.println("  ! '" + text + "' is not a whole number. Please try again.");
            }
        }
    }

    /** Overload 2: a whole number inside [min, max]. */
    public int readInt(String prompt, int min, int max) {
        while (true) {
            int value = readInt(prompt);
            if (value >= min && value <= max) {
                return value;
            }
            System.out.println("  ! Please enter a number from " + min + " to " + max + ".");
        }
    }

    /** Reads a non-empty name made of letters, spaces, dots. */
    public String readName(String prompt) {
        while (true) {
            String name = readLine(prompt);
            if (!name.isEmpty() && name.matches("[A-Za-z .]+")) {
                return name;
            }
            System.out.println("  ! Name must contain letters only. Please try again.");
        }
    }

    /** UNIT I - char: reads a single-letter choice such as G or S. */
    public char readChoice(String prompt, String allowed) {
        while (true) {
            String text = readLine(prompt).toUpperCase();
            if (text.length() == 1 && allowed.indexOf(text.charAt(0)) >= 0) {
                return text.charAt(0);
            }
            System.out.println("  ! Please type one of: " + allowed);
        }
    }
}
