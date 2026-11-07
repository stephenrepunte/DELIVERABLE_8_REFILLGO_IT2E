package dao;

import model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDao {
    private final ActivityLogDao activityLogDao;

    public UserDao(ActivityLogDao activityLogDao) {
        this.activityLogDao = activityLogDao;
    }

    public boolean register(String username, String password, String role) throws SQLException {
        String sql = "INSERT INTO users(username, password, roles, status) VALUES (?, ?, ?, 'PENDING')";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username.trim());
            statement.setString(2, password);
            statement.setString(3, role);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 19 || (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique"))) return false;
            throw e;
        }
    }

    public Optional<User> findByCredentials(String username, String password) throws SQLException {
        String sql = "SELECT id, username, password, roles, status FROM users WHERE username = ? AND password = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username.trim());
            statement.setString(2, password);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return Optional.of(mapUser(rs));
            }
        }
        return Optional.empty();
    }

    public List<User> getAllUsers() throws SQLException {
        return queryUsers("SELECT id, username, password, roles, status FROM users ORDER BY id", null);
    }

    public List<User> getPendingUsers() throws SQLException {
        return queryUsers("SELECT id, username, password, roles, status FROM users WHERE status = ? ORDER BY id", "PENDING");
    }

    private List<User> queryUsers(String sql, String status) throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (status != null) statement.setString(1, status);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) users.add(mapUser(rs));
            }
        }
        return users;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password"),
                rs.getString("roles"), rs.getString("status"));
    }

    /** Account Management (log type USER_APPROVED). The change and its log commit together. */
    public boolean approveUser(int adminId, int userId) throws SQLException {
        String sql = "UPDATE users SET status = 'APPROVED' WHERE id = ? AND status = 'PENDING'";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                boolean updated = statement.executeUpdate() > 0;
                if (updated) {
                    activityLogDao.logAction(connection, adminId, "USER_APPROVED",
                            "Admin " + adminId + " approved account for User ID " + userId);
                }
                connection.commit();
                return updated;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Account Management (log type USER_UPDATED). The change and its log commit together. */
    public boolean updateUserRoleAndStatus(int adminId, int userId, String newRole, String newStatus)
            throws SQLException {
        String sql = "UPDATE users SET roles = ?, status = ? WHERE id = ?;";
        String role = newRole.trim().toUpperCase(java.util.Locale.ROOT);
        String status = newStatus.trim().toUpperCase(java.util.Locale.ROOT);

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, role);
                statement.setString(2, status);
                statement.setInt(3, userId);
                boolean updated = statement.executeUpdate() > 0;
                if (updated) {
                    activityLogDao.logAction(connection, adminId, "USER_UPDATED",
                            "Admin " + adminId + " changed role of User " + userId + " to " + role
                                    + " and status to " + status);
                }
                connection.commit();
                return updated;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public boolean updateStatus(int id, String status) throws SQLException {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, id);
            return statement.executeUpdate() > 0;
        }
    }
}
