package dao;

import model.RefillOrder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Core business process of ReFillGo: recording refill orders and moving them
 * through their lifecycle. Every change is written together with its audit log
 * entry in a single transaction.
 */
public class RefillOrderDao {
    public static final Set<String> STATUSES = Set.of("PENDING", "READY", "COMPLETED", "CANCELLED");

    private final ActivityLogDao activityLogDao;

    public RefillOrderDao(ActivityLogDao activityLogDao) {
        this.activityLogDao = activityLogDao;
    }

    /** Core Business Process (log type REFILL_PROCESSED). Returns the new order ID. */
    public int processRefill(int staffId, String customerName, String item, int quantity, double totalAmount)
            throws SQLException {
        String sql = "INSERT INTO refill_orders(customer_name, item, quantity, total_amount, processed_by) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, customerName);
                statement.setString(2, item);
                statement.setInt(3, quantity);
                statement.setDouble(4, totalAmount);
                statement.setInt(5, staffId);
                statement.executeUpdate();
                int orderId;
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Order was saved but no ID was returned.");
                    orderId = keys.getInt(1);
                }
                activityLogDao.logAction(connection, staffId, "REFILL_PROCESSED",
                        String.format("Processed refill order #%d for Customer %s - %d x %s - Total: P%.2f",
                                orderId, customerName, quantity, item, totalAmount));
                connection.commit();
                return orderId;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Lifecycle / State Change (log type REFILL_UPDATED).
     * Returns false if the order does not exist.
     */
    public boolean updateStatus(int staffId, int orderId, String newStatus) throws SQLException {
        String select = "SELECT status FROM refill_orders WHERE id = ?";
        String update = "UPDATE refill_orders SET status = ? WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String oldStatus;
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setInt(1, orderId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return false;
                        }
                        oldStatus = rs.getString("status");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setString(1, newStatus);
                    statement.setInt(2, orderId);
                    statement.executeUpdate();
                }
                activityLogDao.logAction(connection, staffId, "REFILL_UPDATED",
                        String.format("Updated Order #%d status from %s to %s", orderId, oldStatus, newStatus));
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public RefillOrder findById(int orderId) throws SQLException {
        String sql = "SELECT id, customer_name, item, quantity, total_amount, status, processed_by, created_at "
                + "FROM refill_orders WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapOrder(rs) : null;
            }
        }
    }

    public List<RefillOrder> getAllOrders() throws SQLException {
        String sql = "SELECT id, customer_name, item, quantity, total_amount, status, processed_by, created_at "
                + "FROM refill_orders ORDER BY id DESC";
        List<RefillOrder> orders = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) orders.add(mapOrder(rs));
        }
        return orders;
    }

    private RefillOrder mapOrder(ResultSet rs) throws SQLException {
        return new RefillOrder(rs.getInt("id"), rs.getString("customer_name"), rs.getString("item"),
                rs.getInt("quantity"), rs.getDouble("total_amount"), rs.getString("status"),
                rs.getInt("processed_by"), rs.getString("created_at"));
    }
}
