package dao;

import model.ActivityLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the audit trail lives here (no SQL in the UI layer).
 * Every query that receives a value uses a parameterized PreparedStatement.
 */
public class ActivityLogDao {

    /** Inserts one audit entry using its own connection. */
    public void logAction(int userId, String actionType, String description) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            logAction(connection, userId, actionType, description);
        }
    }

    /**
     * Inserts one audit entry on an existing connection, so the DAO that performs
     * a business change can write the change and its log in ONE transaction.
     */
    public void logAction(Connection connection, int userId, String actionType, String description)
            throws SQLException {
        String sql = "INSERT INTO activity_logs(user_id, action_type, description) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, actionType);
            statement.setString(3, description);
            statement.executeUpdate();
        }
    }

    public List<ActivityLog> getAllLogs() throws SQLException {
        String sql = "SELECT id, user_id, action_type, description, timestamp "
                + "FROM activity_logs ORDER BY timestamp DESC, id DESC";
        return query(sql, null);
    }

    public List<ActivityLog> getLogsByUserId(int userId) throws SQLException {
        String sql = "SELECT id, user_id, action_type, description, timestamp "
                + "FROM activity_logs WHERE user_id = ? ORDER BY timestamp DESC, id DESC";
        return query(sql, userId);
    }

    /** Deletes all log rows and returns how many were removed. */
    public int clearAllLogs() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            return statement.executeUpdate("DELETE FROM activity_logs");
        }
    }

    private List<ActivityLog> query(String sql, Integer userId) throws SQLException {
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (userId != null) statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    logs.add(new ActivityLog(rs.getInt("id"), rs.getInt("user_id"),
                            rs.getString("action_type"), rs.getString("description"),
                            rs.getString("timestamp")));
                }
            }
        }
        return logs;
    }
}
