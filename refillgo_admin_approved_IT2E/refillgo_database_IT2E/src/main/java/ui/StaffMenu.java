package ui;

import dao.RefillOrderDao;
import model.RefillOrder;
import model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** Staff menu: records refill orders and updates their status (both are audit-logged by the DAO). */
public class StaffMenu {
    private final Scanner scanner;
    private final RefillOrderDao refillOrderDao;

    public StaffMenu(Scanner scanner, RefillOrderDao refillOrderDao) {
        this.scanner = scanner;
        this.refillOrderDao = refillOrderDao;
    }

    public void start(User staff) {
        boolean running = true;
        while (running) {
            System.out.println("\n--- STAFF MENU ---");
            System.out.println("Welcome, " + staff.username() + "!");
            System.out.println("1. View Staff Account");
            System.out.println("2. Process Refill Order");
            System.out.println("3. Update Order Status");
            System.out.println("4. View Refill Orders");
            System.out.println("5. Logout");
            switch (readIntInput("Choose: ")) {
                case 1 -> System.out.printf("Username: %s%nRole: %s%nStatus: %s%n",
                        staff.username(), staff.roles(), staff.status());
                case 2 -> processRefill(staff);
                case 3 -> updateStatus(staff);
                case 4 -> viewOrders();
                case 5 -> running = false;
                default -> System.out.println("Invalid choice. Please choose 1-5.");
            }
        }
    }

    private void processRefill(User staff) {
        System.out.print("Customer name (blank to cancel): ");
        String customer = scanner.nextLine().trim();
        if (customer.isBlank()) {
            System.out.println("Order cancelled.");
            return;
        }
        System.out.print("Item refilled (e.g. 5-gallon water, LPG 11kg): ");
        String item = scanner.nextLine().trim();
        if (item.isBlank()) {
            System.out.println("Item cannot be empty. Order cancelled.");
            return;
        }
        int quantity = readPositiveInt("Quantity: ");
        double total = readNonNegativeAmount("Total amount (P): ");
        try {
            int id = refillOrderDao.processRefill(staff.id(), customer, item, quantity, total);
            System.out.println("Refill order #" + id + " recorded. Status: PENDING");
        } catch (SQLException e) {
            System.out.println("Could not process refill: " + e.getMessage());
        }
    }

    private void updateStatus(User staff) {
        viewOrders();
        int orderId = readIntInput("Enter Order ID to update (0 to cancel): ");
        if (orderId == 0) {
            System.out.println("Update cancelled.");
            return;
        }
        try {
            RefillOrder order = refillOrderDao.findById(orderId);
            if (order == null) {
                System.out.println("Order ID not found.");
                return;
            }
            if (order.status().equals("COMPLETED") || order.status().equals("CANCELLED")) {
                System.out.println("Order #" + orderId + " is already " + order.status() + " and cannot be changed.");
                return;
            }
            String newStatus = readValidStatus();
            if (newStatus == null) {
                System.out.println("Update cancelled.");
                return;
            }
            if (newStatus.equals(order.status())) {
                System.out.println("Order is already " + newStatus + ".");
                return;
            }
            if (refillOrderDao.updateStatus(staff.id(), orderId, newStatus)) {
                System.out.println("Order #" + orderId + " status changed from " + order.status() + " to " + newStatus + ".");
            } else {
                System.out.println("No order was updated.");
            }
        } catch (SQLException e) {
            System.out.println("Could not update order: " + e.getMessage());
        }
    }

    private void viewOrders() {
        try {
            List<RefillOrder> orders = refillOrderDao.getAllOrders();
            String border = "+-------+----------------+------------------+-----+------------+------------+";
            System.out.println(border);
            System.out.printf("| %-5s | %-14s | %-16s | %-3s | %-10s | %-10s |%n",
                    "ID", "CUSTOMER", "ITEM", "QTY", "TOTAL", "STATUS");
            System.out.println(border);
            for (RefillOrder o : orders) {
                System.out.printf("| %-5s | %-14.14s | %-16.16s | %-3s | %-10s | %-10.10s |%n",
                        o.id(), o.customerName(), o.item(), o.quantity(),
                        String.format(Locale.ROOT, "P%.2f", o.totalAmount()), o.status());
            }
            System.out.println(border);
            if (orders.isEmpty()) System.out.println("No refill orders yet.");
        } catch (SQLException e) {
            System.out.println("Could not load orders: " + e.getMessage());
        }
    }

    private String readValidStatus() {
        while (true) {
            System.out.print("New status (PENDING, READY, COMPLETED, CANCELLED; 0 to cancel): ");
            String value = scanner.nextLine().trim();
            if (value.equals("0")) return null;
            String status = value.toUpperCase(Locale.ROOT);
            if (RefillOrderDao.STATUSES.contains(status)) return status;
            System.out.println("Invalid status.");
        }
    }

    private int readPositiveInt(String prompt) {
        while (true) {
            int value = readIntInput(prompt);
            if (value > 0) return value;
            System.out.println("Please enter a number greater than 0.");
        }
    }

    private double readNonNegativeAmount(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double value = Double.parseDouble(scanner.nextLine().trim());
                if (value >= 0 && !Double.isNaN(value) && !Double.isInfinite(value)) return value;
            } catch (NumberFormatException ignored) {
                // fall through to re-prompt
            }
            System.out.println("Invalid amount. Enter a number such as 150 or 150.50.");
        }
    }

    private int readIntInput(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }
}
