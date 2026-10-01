package it.SimoSW.model;

public class User {

    private final String username;
    private final String passwordHash;
    private final UserRole role;
    private final boolean active;

    public User(String username, String passwordHash, UserRole role, boolean active) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

}
