package model;

/** Immutable record for one refill order (the system's core business transaction). */
public record RefillOrder(int id, String customerName, String item, int quantity,
                          double totalAmount, String status, int processedBy, String createdAt) {
}
