package railyatra.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import railyatra.exceptions.BerthsNotAvailableException;
import railyatra.exceptions.CoachClassNotFoundException;
import railyatra.model.Booking;
import railyatra.model.BookingLine;
import railyatra.model.CoachClass;
import railyatra.model.SwapRequest;
import railyatra.model.WaitlistEntry;
import railyatra.people.General;
import railyatra.people.Passenger;
import railyatra.people.SeniorCitizen;
import railyatra.service.ReservationCounter;
import railyatra.util.ItemStack;
import railyatra.util.WaitlistQueue;

/**
 * RailYatra web app: the SAME Java classes as the console program, shown in a browser.
 * Uses only the JDK's built-in web server (com.sun.net.httpserver) - no libraries.
 *
 * Run from the RailYatra folder:  java -cp out-web railyatra.web.WebServer
 * Then open:                      http://localhost:8080
 *
 * The browser page (web/static/index.html) calls the JSON API below with fetch().
 */
public class WebServer {

    // On a cloud host (Render) the PORT environment variable is set and we must listen on all addresses.
    // On a laptop it is not set: port 8080, localhost only.
    private static final String ENV_PORT = System.getenv("PORT");
    private static final int PORT = ENV_PORT == null ? 8080 : Integer.parseInt(ENV_PORT.trim());
    private static final String HOST = ENV_PORT == null ? "localhost" : "0.0.0.0";
    private static final Object LOCK = new Object();         // one request changes data at a time

    private static ReservationCounter counter = new ReservationCounter();
    private static Path staticDir = Paths.get("web", "static");

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            staticDir = Paths.get(args[0]);
        }
        if (!Files.exists(staticDir.resolve("index.html"))) {
            System.out.println("Cannot find " + staticDir.resolve("index.html").toAbsolutePath());
            System.out.println("Run this from the RailYatra folder (the one that contains 'web').");
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(HOST, PORT), 0);
        server.createContext("/", WebServer::handle);
        server.start();
        System.out.println("==================================================");
        System.out.println("  RailYatra web app is running");
        System.out.println(ENV_PORT == null ? "  Open http://localhost:" + PORT + " in your browser"
                : "  Listening on port " + PORT);
        System.out.println("  Press Ctrl+C here to stop");
        System.out.println("==================================================");
    }

    // ------------------------------------------------------------------ routing

    private static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (!path.startsWith("/api/")) {
                serveFile(ex, path);
                return;
            }
            String[][] p = params(ex);
            String json;
            synchronized (LOCK) {
                switch (path) {
                    case "/api/classes":   json = classesJson(); break;
                    case "/api/seatmap":   json = seatMapJson(intParam(p, "id")); break;
                    case "/api/book":      json = book(p); break;
                    case "/api/waitlist/join": json = joinWaitlist(p); break;
                    case "/api/bookings":  json = bookingsJson(); break;
                    case "/api/booking":   json = bookingOrError(param(p, "pnr")); break;
                    case "/api/cancel":    json = cancel(p); break;
                    case "/api/waitlist":  json = waitlistJson(); break;
                    case "/api/swaps":     json = swapsJson(); break;
                    case "/api/swap":      json = swap(p); break;
                    case "/api/willing":   json = willing(p); break;
                    case "/api/report":    json = reportJson(); break;
                    default:               json = error("Unknown API: " + path);
                }
            }
            send(ex, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {          // never crash the server on a bad request
            send(ex, 200, "application/json; charset=utf-8",
                    error("Bad request: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void serveFile(HttpExchange ex, String path) throws IOException {
        String name = path.equals("/") ? "index.html" : path.substring(1);
        Path file = staticDir.resolve(name).normalize();
        if (!file.startsWith(staticDir.normalize()) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                : name.endsWith(".css") ? "text/css" : name.endsWith(".js") ? "text/javascript" : "application/octet-stream";
        send(ex, 200, type, Files.readAllBytes(file));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream out = ex.getResponseBody()) {          // try-with-resources
            out.write(body);
        }
    }

    // ------------------------------------------------------------------ actions

    /** Book: name, type (G/S), lines = "101:2,105:1". Same checks as the console booking. */
    private static String book(String[][] p) {
        String name = param(p, "name").trim();
        if (!name.matches("[A-Za-z .]+")) {
            return error("Name must contain letters only.");
        }
        Passenger passenger = "S".equals(param(p, "type")) ? new SeniorCitizen(name) : new General(name);
        String lines = param(p, "lines");
        if (lines.isEmpty()) {
            return error("Add at least one coach class to the booking.");
        }
        ItemStack cart = new ItemStack(20);
        CoachClass c = null;
        int qty = 0;
        try {
            for (String part : lines.split(",")) {
                String[] idQty = part.split(":");
                c = counter.findCoachClass(Integer.parseInt(idQty[0].trim()));
                qty = Integer.parseInt(idQty[1].trim());
                if (qty <= 0) {
                    return error("Quantity must be 1 or more.");
                }
                counter.checkBerths(c, qty, cart);
                cart.push(new BookingLine(c, qty));
            }
            Booking b = counter.confirm(passenger, cart);
            String[] served = "1".equals(param(p, "willing")) ? counter.setSwapWilling(b, true) : new String[0];
            saveQuietly(b);
            return "{\"ok\":true,\"booking\":" + bookingJson(b) + ",\"swaps\":" + strArray(served) + "}";
        } catch (CoachClassNotFoundException e) {
            return error(e.getMessage());
        } catch (BerthsNotAvailableException e) {
            // Same help as the console: cheaper classes + waitlist offer
            StringBuilder sb = new StringBuilder("{\"ok\":false,\"error\":" + str(e.getMessage()));
            sb.append(",\"full\":{\"classId\":").append(c.getId()).append(",\"className\":").append(str(c.getName()))
              .append(",\"qty\":").append(qty).append(",\"canWaitlist\":").append(qty <= c.getTotalBerths())
              .append(",\"suggestions\":[");
            CoachClass[] alt = counter.suggestAlternatives(c, qty);
            for (int i = 0; i < alt.length; i++) {
                sb.append(i > 0 ? "," : "").append("{\"id\":").append(alt[i].getId()).append(",\"name\":")
                  .append(str(alt[i].getName())).append(",\"fare\":").append(alt[i].getFare()).append("}");
            }
            return sb.append("]}}").toString();
        } catch (NumberFormatException e) {
            return error("Coach class id and quantity must be whole numbers.");
        } catch (IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    private static String joinWaitlist(String[][] p) {
        String name = param(p, "name").trim();
        if (!name.matches("[A-Za-z .]+")) {
            return error("Name must contain letters only.");
        }
        Passenger passenger = "S".equals(param(p, "type")) ? new SeniorCitizen(name) : new General(name);
        String cat = param(p, "category");
        char category = cat.isEmpty() ? 'N' : cat.charAt(0);
        try {
            CoachClass c = counter.findCoachClass(intParam(p, "classId"));
            WaitlistEntry e = counter.addToWaitlist(passenger, c, intParam(p, "qty"), category);
            return "{\"ok\":true,\"message\":" + str("Added to the waitlist as " + e.getWlNo() + ", priority "
                    + e.getPriorityLabel() + ". It will be confirmed automatically when berths are cancelled.") + "}";
        } catch (CoachClassNotFoundException | IllegalStateException e) {    // multi-catch
            return error(e.getMessage());
        }
    }

    /** Cancel: pnr, mode W (whole) or S (some) with line + qty. Returns refund + waitlist confirmations. */
    private static String cancel(String[][] p) {
        Booking b = counter.findBooking(param(p, "pnr"));
        if (b == null) {
            return error("No booking found with id '" + param(p, "pnr") + "'.");
        }
        if (b.isCancelled()) {
            return error(b.getPnr() + " is already cancelled.");
        }
        double refund;
        try {
            if ("W".equals(param(p, "mode"))) {
                refund = counter.cancel(b);
            } else if (!param(p, "berths").isEmpty()) {          // exactly the berths the user picked
                String[] parts = param(p, "berths").split(",");
                int[] chosen = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    chosen[i] = Integer.parseInt(parts[i].trim());
                }
                refund = counter.cancelChosen(b, intParam(p, "line"), chosen);
            } else {
                refund = counter.cancelPart(b, intParam(p, "line"), intParam(p, "qty"));
            }
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            return error("Choose a coach class and a valid number of berths to cancel.");
        }
        Booking[] promoted = counter.promoteWaitlist();
        String[] served = counter.servePendingSwaps();
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"refund\":" + round(refund) + ",\"booking\":" + bookingJson(b)
                + ",\"swaps\":" + strArray(served) + ",\"promoted\":[");
        for (int i = 0; i < promoted.length; i++) {
            saveQuietly(promoted[i]);
            sb.append(i > 0 ? "," : "").append(bookingJson(promoted[i]));
        }
        saveQuietly(b);
        return sb.append("]}").toString();
    }

    /** Swap: pnr, line, berth (number), want (LB / MB / UB / SL / SU). Served automatically if possible. */
    private static String swap(String[][] p) {
        Booking b = counter.findBooking(param(p, "pnr"));
        if (b == null || b.isCancelled()) {
            return error("No active booking with id '" + param(p, "pnr") + "'.");
        }
        try {
            String result = counter.requestSwap(b, intParam(p, "line"), intParam(p, "berth"), param(p, "want"));
            Booking other = counter.takeLastSwapPartner();
            saveQuietly(b);
            StringBuilder sb = new StringBuilder("{\"ok\":true,\"waiting\":" + result.startsWith("No free")
                    + ",\"message\":" + str(result) + ",\"booking\":" + bookingJson(b));
            if (other != null) {
                saveQuietly(other);
                sb.append(",\"partner\":").append(bookingJson(other));
            }
            return sb.append("}").toString();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        } catch (ArrayIndexOutOfBoundsException e) {
            return error("Choose a berth from this booking.");
        }
    }

    /** Agree / stop agreeing to swap: pnr, on (1/0). Waiting requests are retried at once. */
    private static String willing(String[][] p) {
        Booking b = counter.findBooking(param(p, "pnr"));
        if (b == null || b.isCancelled()) {
            return error("No active booking with id '" + param(p, "pnr") + "'.");
        }
        String[] served = counter.setSwapWilling(b, "1".equals(param(p, "on")));
        return "{\"ok\":true,\"booking\":" + bookingJson(b) + ",\"swaps\":" + strArray(served) + "}";
    }

    private static void saveQuietly(Booking b) {
        try {
            counter.saveTicket(b);
        } catch (IOException e) {
            System.out.println("Ticket file not saved: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ JSON views

    private static String classesJson() {
        StringBuilder sb = new StringBuilder("[");
        CoachClass[] cs = counter.getClasses();
        for (int i = 0; i < cs.length; i++) {
            CoachClass c = cs[i];
            sb.append(i > 0 ? "," : "").append("{\"id\":").append(c.getId()).append(",\"name\":").append(str(c.getName()))
              .append(",\"category\":").append(str(c.getCategory())).append(",\"fare\":").append(c.getFare())
              .append(",\"berths\":").append(c.getBerths()).append(",\"total\":").append(c.getTotalBerths())
              .append(",\"status\":").append(str(c.getStatus())).append(",\"occupancy\":").append(c.getOccupancyPercent())
              .append(",\"group\":").append(str(c.groupWord())).append("}");
        }
        return sb.append("]").toString();
    }

    /** Every berth of one class: number, label, type, bay, taken, who holds it. */
    private static String seatMapJson(int id) {
        CoachClass c;
        try {
            c = counter.findCoachClass(id);
        } catch (CoachClassNotFoundException e) {
            return error(e.getMessage());
        }
        StringBuilder sb = new StringBuilder("{\"ok\":true,\"name\":" + str(c.getName()) + ",\"group\":"
                + str(c.groupWord()) + ",\"bays\":" + c.bayCount() + ",\"berths\":[");
        for (int n = 1; n <= c.getTotalBerths(); n++) {
            String holder = "";
            for (int i = 0; i < counter.getBookingCount() && holder.isEmpty(); i++) {
                Booking b = counter.getBooking(i);
                for (int l = 0; l < b.getLineCount(); l++) {
                    if (b.getLine(l).getCoachClass() == c && b.getLine(l).holds(n)) {
                        holder = b.getPnr() + " " + b.getPassenger().getName();
                    }
                }
            }
            sb.append(n > 1 ? "," : "").append("{\"no\":").append(n).append(",\"type\":").append(str(c.berthType(n)))
              .append(",\"label\":").append(str(c.berthLabel(n))).append(",\"bay\":").append(c.bayOf(n))
              .append(",\"lower\":").append(CoachClass.isLower(c.berthType(n)))
              .append(",\"taken\":").append(c.isOccupied(n)).append(",\"holder\":").append(str(holder)).append("}");
        }
        return sb.append("]}").toString();
    }

    private static String bookingsJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = counter.getBookingCount() - 1; i >= 0; i--) {              // newest first
            sb.append(i < counter.getBookingCount() - 1 ? "," : "").append(bookingJson(counter.getBooking(i)));
        }
        return sb.append("]").toString();
    }

    private static String bookingOrError(String pnr) {
        Booking b = counter.findBooking(pnr);
        return b == null ? error("No booking found with id '" + pnr + "'.") : "{\"ok\":true,\"booking\":" + bookingJson(b) + "}";
    }

    private static String bookingJson(Booking b) {
        String status = b.isCancelled() ? "CANCELLED" : (b.isPartlyCancelled() ? "PARTLY CANCELLED" : "CONFIRMED");
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"pnr\":").append(str(b.getPnr())).append(",\"token\":").append(b.getToken())
          .append(",\"window\":").append(str(b.getWindow())).append(",\"status\":").append(str(status))
          .append(",\"name\":").append(str(b.getPassenger().getName())).append(",\"type\":").append(str(b.getPassenger().getType()))
          .append(",\"discountRate\":").append(b.getPassenger().getDiscountRate())
          .append(",\"swapWilling\":").append(b.isSwapWilling())
          .append(",\"subtotal\":").append(round(b.getSubtotal())).append(",\"discount\":").append(round(b.getDiscount()))
          .append(",\"gst\":").append(round(b.getGst())).append(",\"total\":").append(round(b.getTotal()))
          .append(",\"refund\":").append(round(b.getRefund())).append(",\"notes\":[");
        for (int i = 0; i < b.getNoteCount(); i++) {
            sb.append(i > 0 ? "," : "").append(str(b.getNote(i)));
        }
        sb.append("],\"lines\":[");
        for (int i = 0; i < b.getLineCount(); i++) {
            BookingLine l = b.getLine(i);
            CoachClass c = l.getCoachClass();
            sb.append(i > 0 ? "," : "").append("{\"index\":").append(i).append(",\"className\":").append(str(c.getName()))
              .append(",\"qty\":").append(l.getActiveQty()).append(",\"cancelled\":").append(l.getCancelledQty())
              .append(",\"fare\":").append(c.getFare()).append(",\"total\":").append(round(l.getLineTotal()))
              .append(",\"seating\":").append(str(l.getBerthNos().length > 0 ? l.getBayText() : ""))
              .append(",\"sleeping\":").append(c.hasLowerBerths()).append(",\"types\":").append(strArray(c.berthTypes()))
              .append(",\"berths\":[");
            int[] nos = l.getBerthNos();
            for (int k = 0; k < nos.length; k++) {
                sb.append(k > 0 ? "," : "").append("{\"no\":").append(nos[k]).append(",\"label\":")
                  .append(str(c.berthLabel(nos[k]))).append(",\"type\":").append(str(c.berthType(nos[k])))
                  .append(",\"lower\":").append(CoachClass.isLower(c.berthType(nos[k]))).append("}");
            }
            sb.append("]}");
        }
        return sb.append("]}").toString();
    }

    private static String waitlistJson() {
        WaitlistQueue q = counter.getWaitlist();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (int level = WaitlistEntry.PRIORITY_MEDICAL; level >= WaitlistEntry.PRIORITY_NORMAL; level--) {
            for (int i = 0; i < q.size(); i++) {
                WaitlistEntry e = q.get(i);
                if (e.getPriority() == level) {
                    sb.append(first ? "" : ",").append("{\"wl\":").append(str(e.getWlNo())).append(",\"name\":")
                      .append(str(e.getPassenger().getName())).append(",\"type\":").append(str(e.getPassenger().getType()))
                      .append(",\"className\":").append(str(e.getCoachClass().getName())).append(",\"qty\":")
                      .append(e.getQuantity()).append(",\"priority\":").append(e.getPriority())
                      .append(",\"priorityLabel\":").append(str(e.getPriorityLabel())).append("}");
                    first = false;
                }
            }
        }
        return sb.append("]").toString();
    }

    private static String swapsJson() {
        SwapRequest[] open = counter.getOpenSwaps();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < open.length; i++) {
            SwapRequest r = open[i];
            CoachClass c = r.getLine().getCoachClass();
            sb.append(i > 0 ? "," : "").append("{\"id\":").append(str(r.getId())).append(",\"pnr\":")
              .append(str(r.getBooking().getPnr())).append(",\"name\":").append(str(r.getBooking().getPassenger().getName()))
              .append(",\"className\":").append(str(c.getName())).append(",\"berth\":").append(str(c.berthLabel(r.getBerthNo())))
              .append(",\"kind\":").append(str("wants a " + r.getWantType() + " berth"))
              .append("}");
        }
        return sb.append("]").toString();
    }

    private static String reportJson() {
        int n = counter.getBookingCount();
        int cancelled = 0;
        int partly = 0;
        int seniors = 0;
        double concession = 0;
        double refunds = 0;
        for (int i = 0; i < n; i++) {
            Booking b = counter.getBooking(i);
            refunds += b.getRefund();
            if (b.isCancelled()) {
                cancelled++;
                continue;
            }
            if (b.isPartlyCancelled()) {
                partly++;
            }
            if (b.getPassenger() instanceof SeniorCitizen) {
                seniors++;
            }
            concession += b.getDiscount();
        }
        CoachClass top = counter.getMostPopular();
        return "{\"bookings\":" + n + ",\"revenue\":" + round(counter.getTotalRevenue()) + ",\"mostPopular\":"
                + str(top == null ? "none yet" : top.getName() + " (" + top.getBerthsSold() + " berths)")
                + ",\"active\":" + (n - cancelled) + ",\"cancelled\":" + cancelled + ",\"partly\":" + partly
                + ",\"seniors\":" + seniors + ",\"concession\":" + round(concession) + ",\"refunds\":" + round(refunds)
                + ",\"waitlist\":" + counter.getWaitlist().size() + ",\"openSwaps\":" + counter.getOpenSwaps().length
                + ",\"classes\":" + classesJson() + "}";
    }

    // ------------------------------------------------------------------ helpers

    private static String error(String msg) {
        return "{\"ok\":false,\"error\":" + str(msg) + "}";
    }

    private static String strArray(String[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            sb.append(i > 0 ? "," : "").append(str(a[i]));
        }
        return sb.append("]").toString();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** JSON string with escaping. */
    private static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.append('"').toString();
    }

    /** Query string + form body, as pairs [name, value] (arrays only - no HashMap). */
    private static String[][] params(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getRawQuery();
        String body = "";
        if ("POST".equals(ex.getRequestMethod())) {
            try (InputStream in = ex.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        String all = (query == null ? "" : query) + (body.isEmpty() ? "" : "&" + body);
        String[] parts = all.split("&");
        String[][] out = new String[parts.length][];
        int n = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? part : part.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            out[n++] = new String[] { k, v };
        }
        String[][] exact = new String[n][];
        for (int i = 0; i < n; i++) {
            exact[i] = out[i];
        }
        return exact;
    }

    private static String param(String[][] p, String name) {
        for (String[] kv : p) {
            if (kv[0].equals(name)) {
                return kv[1];
            }
        }
        return "";
    }

    private static int intParam(String[][] p, String name) {
        try {
            return Integer.parseInt(param(p, name).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
