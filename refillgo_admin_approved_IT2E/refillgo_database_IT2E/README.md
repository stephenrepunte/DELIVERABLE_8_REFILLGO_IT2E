# ReFillGo Account System (Java + SQLite)

## Architecture
- `Main.java` is the single application entry point.
- `dao/DatabaseInitializer.java` creates the required tables before the CLI starts.
- `dao/DatabaseConnection.java` enables SQLite foreign-key enforcement for every connection.
- `ui/Authentication.java` owns registration, login, and role routing.
- `ui/MenuUI.java` displays role-specific menus for Owner/Admin, Staff, and Customer.
- A single shared `Scanner` and `UserDao` are passed from `Main` through the UI classes.

## Features
- Register as `admin/owner`, `staff`, or `customer`.
- New accounts default to `PENDING` and cannot log in until activated.
- Owner/Admin can list accounts and change account status.
- Staff and Customer are routed to their own menus after successful login.
- `activity_logs` audit trail (Deliverable 8) with a foreign key to `users` (`ON DELETE CASCADE`).
- Staff can process refill orders and update their status (`refill_orders` table).
- Owner/Admin can view, filter, and clear the audit log from **View System Audit Logs**.

## Audit log (PF201 Deliverable 8)
Table `activity_logs(id, user_id, action_type, description, timestamp)`.

| Event | Action type | Where it is logged |
|---|---|---|
| Login / Logout | `LOGIN`, `LOGOUT` | `ui/Authentication.java` -> `login()` / `logout()` |
| Account approval | `USER_APPROVED` | `dao/UserDao.java` -> `approveUser()` |
| Role/status edit | `USER_UPDATED` | `dao/UserDao.java` -> `updateUserRoleAndStatus()` |
| Core business process | `REFILL_PROCESSED` | `dao/RefillOrderDao.java` -> `processRefill()` |
| Lifecycle / state change | `REFILL_UPDATED` | `dao/RefillOrderDao.java` -> `updateStatus()` |
| Admin clears the log | `LOGS_CLEARED` | `ui/AdminMenu.java` -> `clearLogs()` |

All SQL is in `dao/`. Approve/update/refill changes and their log rows are written in one transaction.
Older `refillgo.db` files with the previous `activity_logs` columns are migrated automatically on startup.

## First-run administrator
If no Owner/Admin account exists, the initializer creates:
- Username: `admin`
- Password: `admin123`
- Role: `admin/owner`
- Status: `ACTIVE`

Change this demo password before using the application beyond a classroom demo.

## Database location
The existing relative JDBC URL is preserved: `jdbc:sqlite:refillgo.db`. Maven creates/opens `refillgo.db` in the application's working directory (normally the project root when you run Maven there).

## Run
Use JDK 17+ and Maven. From the project root:

```bash
mvn clean compile
mvn exec:java
```

## Security note
This classroom version stores passwords as plain text to match the existing project. A real system should store salted password hashes instead.
