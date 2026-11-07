package ui;

import dao.ActivityLogDao;
import dao.UserDao;
import model.ActivityLog;
import model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Scanner;

public class AdminMenu {
    private final Scanner scanner;
    private final UserDao userDao;
    private final ActivityLogDao activityLogDao;

    public AdminMenu(Scanner scanner, UserDao userDao, ActivityLogDao activityLogDao) {
        this.scanner = scanner;
        this.userDao = userDao;
        this.activityLogDao = activityLogDao;
    }

    public void start(User admin) {
        boolean running = true;
        while (running) {
            System.out.println("\n--- OWNER/ADMIN MENU ---");
            System.out.println("Signed in as: " + admin.username());
            System.out.println("1. View All System Users");
            System.out.println("2. View Pending Registrations");
            System.out.println("3. Approve User Account");
            System.out.println("4. Update User Role and Status");
            System.out.println("5. View System Audit Logs");
            System.out.println("6. Logout");
            int choice = readIntInput("Choose: ");
            switch (choice) {
                case 1 -> showUsers(false);
                case 2 -> showUsers(true);
                case 3 -> approveUser(admin);
                case 4 -> updateUserRoleAndStatus(admin);
                case 5 -> auditLogMenu(admin);
                case 6 -> running = false;
                default -> System.out.println("Invalid choice. Please choose 1-6.");
            }
        }
    }

    private void showUsers(boolean pendingOnly) {
        try {
            List<User> users = pendingOnly ? userDao.getPendingUsers() : userDao.getAllUsers();
            printUserTable(users);
            if (users.isEmpty()) System.out.println(pendingOnly ? "No pending registrations." : "No registered users.");
        } catch (SQLException e) {
            System.out.println("Could not load users: " + e.getMessage());
        }
    }

    private void approveUser(User admin) {
        try {
            List<User> pending = userDao.getPendingUsers();
            printUserTable(pending);
            if (pending.isEmpty()) {
                System.out.println("There are no pending accounts to approve.");
                return;
            }
            int id = readIntInput("Enter target User ID to approve (0 to cancel): ");
            if (id == 0) {
                System.out.println("Approval cancelled.");
                return;
            }
            boolean existsInPendingList = pending.stream().anyMatch(user -> user.id() == id);
            if (!existsInPendingList) {
                System.out.println("That ID is not in the pending registrations list.");
                return;
            }
            if (userDao.approveUser(admin.id(), id)) System.out.println("User ID " + id + " approved successfully. Status: APPROVED");
            else System.out.println("Approval failed. The account may no longer be pending.");
        } catch (SQLException e) {
            System.out.println("Could not approve user: " + e.getMessage());
        }
    }

    private void updateUserRoleAndStatus(User currentAdmin) {
        try {
            List<User> users = userDao.getAllUsers();
            printUserTable(users);
            if (users.isEmpty()) {
                System.out.println("No registered users to update.");
                return;
            }

            int targetUserId = readIntInput("Enter target User ID to update (0 to cancel): ");
            if (targetUserId == 0) {
                System.out.println("Update cancelled.");
                return;
            }

            User target = users.stream().filter(user -> user.id() == targetUserId).findFirst().orElse(null);
            if (target == null) {
                System.out.println("User ID not found.");
                return;
            }

            if (targetUserId == currentAdmin.id() && "ACTIVE".equalsIgnoreCase(currentAdmin.status())) {
                System.out.println("WARNING: Modifying the currently active admin account is restricted.");
                return;
            }

            String newRole = readValidRole();
            if (newRole == null) {
                System.out.println("Update cancelled.");
                return;
            }

            String newStatus = readValidStatus();
            if (newStatus == null) {
                System.out.println("Update cancelled.");
                return;
            }

            if (userDao.updateUserRoleAndStatus(currentAdmin.id(), targetUserId, newRole, newStatus)) {
                System.out.println("User ID " + targetUserId + " updated successfully.");
            } else {
                System.out.println("No user was updated. Please check the User ID.");
            }
        } catch (SQLException e) {
            System.out.println("Could not update user: " + e.getMessage());
        }
    }

    // ---------------- Audit log interface ----------------

    private void auditLogMenu(User admin) {
        boolean running = true;
        while (running) {
            System.out.println("\n--- SYSTEM AUDIT LOGS ---");
            System.out.println("1. View All System Logs");
            System.out.println("2. Filter Logs by User ID");
            System.out.println("3. Clear All Logs");
            System.out.println("4. Back");
            int choice = readIntInput("Choose: ");
            switch (choice) {
                case 1 -> viewAllLogs();
                case 2 -> viewLogsByUser();
                case 3 -> clearLogs(admin);
                case 4 -> running = false;
                default -> System.out.println("Invalid choice. Please choose 1-4.");
            }
        }
    }

    private void viewAllLogs() {
        try {
            List<ActivityLog> logs = activityLogDao.getAllLogs();
            printLogTable(logs);
            if (logs.isEmpty()) System.out.println("No activity has been logged yet.");
        } catch (SQLException e) {
            System.out.println("Could not load audit logs: " + e.getMessage());
        }
    }

    private void viewLogsByUser() {
        int userId = readIntInput("Enter User ID to filter by (0 to cancel): ");
        if (userId == 0) {
            System.out.println("Filter cancelled.");
            return;
        }
        try {
            List<ActivityLog> logs = activityLogDao.getLogsByUserId(userId);
            printLogTable(logs);
            if (logs.isEmpty()) System.out.println("No logs found for User ID " + userId + ".");
        } catch (SQLException e) {
            System.out.println("Could not load audit logs: " + e.getMessage());
        }
    }

    private void clearLogs(User admin) {
        System.out.print("This permanently deletes ALL audit logs. Type YES to confirm: ");
        String answer = scanner.nextLine().trim();
        if (!answer.equals("YES")) {
            System.out.println("Clear cancelled. No logs were deleted.");
            return;
        }
        try {
            int removed = activityLogDao.clearAllLogs();
            // Keep the trail honest: record who cleared it.
            activityLogDao.logAction(admin.id(), "LOGS_CLEARED",
                    "Admin " + admin.id() + " cleared " + removed + " audit log entries");
            System.out.println(removed + " log entries deleted.");
        } catch (SQLException e) {
            System.out.println("Could not clear audit logs: " + e.getMessage());
        }
    }

    private static final String LOG_FORMAT = "%-5s | %-8s | %-18s | %-35s | %-20s%n";
    private static final int DESCRIPTION_WIDTH = 35;

    private void printLogTable(List<ActivityLog> logs) {
        String border = "-".repeat(5 + 3 + 8 + 3 + 18 + 3 + DESCRIPTION_WIDTH + 3 + 20);
        System.out.println(border);
        System.out.printf(LOG_FORMAT, "ID", "USER ID", "ACTION TYPE", "DESCRIPTION", "TIMESTAMP");
        System.out.println(border);
        for (ActivityLog log : logs) {
            List<String> lines = wrap(log.description(), DESCRIPTION_WIDTH);
            System.out.printf(LOG_FORMAT, String.valueOf(log.id()), String.valueOf(log.userId()),
                    log.actionType(), lines.get(0), log.timestamp());
            // Long descriptions continue on extra lines so nothing is cut off.
            for (int i = 1; i < lines.size(); i++) {
                System.out.printf(LOG_FORMAT, "", "", "", lines.get(i), "");
            }
        }
        System.out.println(border);
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        String remaining = text == null ? "" : text.trim();
        while (remaining.length() > width) {
            int cut = remaining.lastIndexOf(' ', width);
            if (cut <= 0) cut = width;
            lines.add(remaining.substring(0, cut).trim());
            remaining = remaining.substring(cut).trim();
        }
        lines.add(remaining);
        return lines;
    }

    private String readValidRole() {
        while (true) {
            System.out.print("Enter role (ADMIN/OWNER, STAFF, CUSTOMER; 0 to cancel): ");
            String value = scanner.nextLine().trim();
            if (value.equals("0")) return null;
            String role = value.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
            if (role.equals("ADMIN/OWNER") || role.equals("OWNER/ADMIN") || role.equals("ADMIN") || role.equals("OWNER")) {
                return "ADMIN/OWNER";
            }
            if (role.equals("STAFF")) return "STAFF";
            if (role.equals("CUSTOMER")) return "CUSTOMER";
            System.out.println("Invalid role. Enter ADMIN/OWNER, STAFF, or CUSTOMER, or 0 to cancel.");
        }
    }

    private String readValidStatus() {
        while (true) {
            System.out.print("Enter status (APPROVED or PENDING; 0 to cancel): ");
            String value = scanner.nextLine().trim();
            if (value.equals("0")) return null;
            String status = value.toUpperCase(Locale.ROOT);
            if (status.equals("APPROVED") || status.equals("PENDING")) return status;
            System.out.println("Invalid status. Enter APPROVED or PENDING, or 0 to cancel.");
        }
    }

    private void printUserTable(List<User> users) {
        String border = "+-------+-----------------+--------------+------------+";
        System.out.println(border);
        System.out.printf("| %-5s | %-15s | %-12s | %-10s |%n", "ID", "USERNAME", "ROLE", "STATUS");
        System.out.println(border);
        for (User user : users) {
            System.out.printf("| %-5d | %-15.15s | %-12.12s | %-10.10s |%n",
                    user.id(), user.username(), user.roles(), user.status());
        }
        System.out.println(border);
    }

    private int readIntInput(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }
}
