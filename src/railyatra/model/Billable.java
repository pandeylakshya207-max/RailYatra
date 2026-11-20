package railyatra.model;

/**
 * UNIT IV - Interface.
 * Anything that can be billed must say how to get its subtotal, discount, GST and total.
 * Interface = only a contract (what), abstract class = shared code + contract (what + some how).
 */
public interface Billable {

    double getSubtotal();

    double getDiscount();

    double getGst();

    double getTotal();
}
