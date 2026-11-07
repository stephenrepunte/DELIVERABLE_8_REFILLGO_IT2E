package ui;

import dao.ActivityLogDao;
import dao.RefillOrderDao;
import dao.UserDao;
import model.User;
import java.util.Scanner;

public class MenuUI {
    private final Scanner scanner;
    private final UserDao userDao;
    private final ActivityLogDao activityLogDao;
    private final RefillOrderDao refillOrderDao;

    public MenuUI(Scanner scanner, UserDao userDao, ActivityLogDao activityLogDao, RefillOrderDao refillOrderDao) {
        this.scanner = scanner;
        this.userDao = userDao;
        this.activityLogDao = activityLogDao;
        this.refillOrderDao = refillOrderDao;
    }

    public void openRoleMenu(User user) {
        switch (user.roles().toLowerCase()) {
            case "admin/owner" -> new AdminMenu(scanner, userDao, activityLogDao).start(user);
            case "staff" -> new StaffMenu(scanner, refillOrderDao).start(user);
            case "customer" -> customerMenu(user);
            default -> System.out.println("Unknown role. Access denied.");
        }
    }

    private void customerMenu(User user) {
        boolean running = true;
        while (running) {
            System.out.println("\n--- CUSTOMER MENU ---");
            System.out.println("Welcome, " + user.username() + "!");
            System.out.println("1. View Customer Account");
            System.out.println("2. Logout");
            System.out.print("Choose: ");
            switch (readInt()) {
                case 1 -> System.out.printf("Username: %s%nRole: %s%nStatus: %s%n", user.username(), user.roles(), user.status());
                case 2 -> running = false;
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private int readInt() {
        String value = scanner.nextLine().trim();
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return -1; }
    }
}
