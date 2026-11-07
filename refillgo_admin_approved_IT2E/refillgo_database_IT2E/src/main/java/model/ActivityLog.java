package model;

/** Immutable record for one audit-trail entry stored in activity_logs. */
public record ActivityLog(int id, int userId, String actionType, String description, String timestamp) {
}
