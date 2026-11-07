package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the tables required by the ReFillGo account application before
 * the command-line menu starts.
 */
public final class DatabaseInitializer {
    private DatabaseInitializer() {
    }

    public static void initialize() {
        String createUsers = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                password TEXT NOT NULL,
                roles TEXT NOT NULL CHECK (upper(roles) IN ('ADMIN/OWNER', 'STAFF', 'CUSTOMER')),
                status TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'APPROVED', 'ACTIVE', 'DISABLED'))
            )
            """;

        String createActivityLogs = """
            CREATE TABLE IF NOT EXISTS activity_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                action_type TEXT NOT NULL,
                description TEXT NOT NULL,
                timestamp TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """;

        String createRefillOrders = """
            CREATE TABLE IF NOT EXISTS refill_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_name TEXT NOT NULL,
                item TEXT NOT NULL,
                quantity INTEGER NOT NULL CHECK (quantity > 0),
                total_amount REAL NOT NULL CHECK (total_amount >= 0),
                status TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'READY', 'COMPLETED', 'CANCELLED')),
                processed_by INTEGER,
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                FOREIGN KEY (processed_by) REFERENCES users(id) ON DELETE SET NULL
            )
            """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createUsers);
            migrateLegacyActivityLogs(connection, statement);
            statement.execute(createActivityLogs);
            statement.execute(createRefillOrders);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize ReFillGo database: " + e.getMessage(), e);
        }

        createFirstRunAdministrator();
    }

    /**
     * Older copies of refillgo.db have an activity_logs table with the columns
     * (action, details, created_at). CREATE TABLE IF NOT EXISTS would leave it
     * unchanged, so move its rows into the new schema once.
     */
    private static void migrateLegacyActivityLogs(Connection connection, Statement statement) throws SQLException {
        boolean exists = false;
        boolean hasNewColumns = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(activity_logs)")) {
            while (columns.next()) {
                exists = true;
                if ("action_type".equalsIgnoreCase(columns.getString("name"))) hasNewColumns = true;
            }
        }
        if (!exists || hasNewColumns) return;

        connection.setAutoCommit(false);
        try {
            statement.execute("ALTER TABLE activity_logs RENAME TO activity_logs_old");
            statement.execute("""
                CREATE TABLE activity_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    action_type TEXT NOT NULL,
                    description TEXT NOT NULL,
                    timestamp TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                INSERT INTO activity_logs(user_id, action_type, description, timestamp)
                SELECT user_id, action, COALESCE(details, ''), created_at
                FROM activity_logs_old WHERE user_id IS NOT NULL
                """);
            statement.execute("DROP TABLE activity_logs_old");
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void createFirstRunAdministrator() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement check = connection.prepareStatement(
                     "SELECT COUNT(*) FROM users WHERE roles = 'admin/owner'");
             ResultSet result = check.executeQuery()) {
            if (result.next() && result.getInt(1) == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO users(username, password, roles, status) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, "admin");
                    insert.setString(2, "admin123");
                    insert.setString(3, "admin/owner");
                    insert.setString(4, "ACTIVE");
                    insert.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create initial administrator: " + e.getMessage(), e);
        }
    }
}
