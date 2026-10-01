package it.SimoSW.model.dao.database;

import it.SimoSW.model.User;
import it.SimoSW.model.UserRole;
import it.SimoSW.model.dao.RememberMeTokenDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

public class DatabaseRememberMeTokenDAO implements RememberMeTokenDAO {

    private static final String INSERT_TOKEN =
            "INSERT INTO remember_me_tokens (user_id, token_hash, expires_at) " +
                    "VALUES (?, ?, ?)";

    private static final String FIND_ACTIVE_USER_BY_TOKEN =
            "SELECT u.username, u.role, u.active " +
                    "FROM remember_me_tokens t " +
                    "JOIN users u ON u.id = t.user_id " +
                    "WHERE t.token_hash = ? AND t.expires_at > NOW() AND u.active = TRUE";

    private static final String DELETE_BY_TOKEN =
            "DELETE FROM remember_me_tokens WHERE token_hash = ?";

    private static final String DELETE_BY_USER_ID =
            "DELETE FROM remember_me_tokens WHERE user_id = ?";

    private static final String DELETE_EXPIRED =
            "DELETE FROM remember_me_tokens WHERE expires_at <= NOW()";

    @Override
    public void saveOrUpdate(long userId, String tokenHash, LocalDateTime expiresAt) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_TOKEN)) {

            stmt.setLong(1, userId);
            stmt.setString(2, tokenHash);
            stmt.setTimestamp(3, Timestamp.valueOf(expiresAt));
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error saving remember-me token", e);
        }
    }

    @Override
    public Optional<User> findActiveUserByTokenHash(String tokenHash) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_ACTIVE_USER_BY_TOKEN)) {

            stmt.setString(1, tokenHash);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                User user = new User(
                        rs.getString("username"),
                        "",
                        UserRole.valueOf(rs.getString("role")),
                        rs.getBoolean("active")
                );
                return Optional.of(user);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error reading remember-me token", e);
        }
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_BY_TOKEN)) {

            stmt.setString(1, tokenHash);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error deleting remember-me token by token hash", e);
        }
    }

    @Override
    public void deleteByUserId(long userId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_BY_USER_ID)) {

            stmt.setLong(1, userId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error deleting remember-me token by user id", e);
        }
    }

    @Override
    public void deleteExpired() {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_EXPIRED)) {

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error deleting expired remember-me tokens", e);
        }
    }
}
