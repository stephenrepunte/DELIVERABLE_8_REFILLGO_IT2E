package model;

/** Immutable user record used by the account system. */
public record User(int id, String username, String password, String roles, String status) {
}
