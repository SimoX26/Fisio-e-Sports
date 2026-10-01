package it.SimoSW.model.dao.database;

import it.SimoSW.model.AccessRequest;
import it.SimoSW.model.AccessRequestStatus;
import it.SimoSW.model.UserRole;
import it.SimoSW.model.dao.AccessRequestDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseAccessRequestDAO implements AccessRequestDAO {

    private static final String INSERT =
            "INSERT INTO access_requests " +
                    "(first_name, last_name, email, username, password_hash, requested_role, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String FIND_BY_ID =
            "SELECT id, first_name, last_name, email, username, password_hash, requested_role, status, " +
                    "created_at, reviewed_at, reviewed_by_user_id " +
                    "FROM access_requests WHERE id = ?";

    private static final String FIND_BY_STATUS =
            "SELECT id, first_name, last_name, email, username, password_hash, requested_role, status, " +
                    "created_at, reviewed_at, reviewed_by_user_id " +
                    "FROM access_requests WHERE status = ? ORDER BY created_at ASC";

    private static final String FIND_RECENT =
            "SELECT id, first_name, last_name, email, username, password_hash, requested_role, status, " +
                    "created_at, reviewed_at, reviewed_by_user_id " +
                    "FROM access_requests ORDER BY created_at DESC LIMIT ?";

    private static final String EXISTS_PENDING_BY_USERNAME_OR_EMAIL =
            "SELECT 1 FROM access_requests WHERE status = 'PENDING' AND (username = ? OR email = ?)";

    private static final String EXISTS_BY_EMAIL =
            "SELECT 1 FROM access_requests WHERE email = ?";

    private static final String UPDATE_STATUS =
            "UPDATE access_requests SET status = ?, reviewed_at = CURRENT_TIMESTAMP, reviewed_by_user_id = ? " +
                    "WHERE id = ?";

    @Override
    public AccessRequest save(AccessRequest accessRequest) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, accessRequest.getFirstName());
            stmt.setString(2, accessRequest.getLastName());
            stmt.setString(3, accessRequest.getEmail());
            stmt.setString(4, accessRequest.getUsername());
            stmt.setString(5, accessRequest.getPasswordHash());
            stmt.setString(6, accessRequest.getRequestedRole().name());
            stmt.setString(7, accessRequest.getStatus().name());
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    accessRequest.setId(keys.getLong(1));
                }
            }

            return accessRequest;
        } catch (SQLException e) {
            throw new RuntimeException("Error saving access request", e);
        }
    }

    @Override
    public Optional<AccessRequest> findById(long id) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading access request by id", e);
        }
    }

    @Override
    public List<AccessRequest> findByStatus(AccessRequestStatus status) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_BY_STATUS)) {

            stmt.setString(1, status.name());
            try (ResultSet rs = stmt.executeQuery()) {
                List<AccessRequest> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading access requests by status", e);
        }
    }

    @Override
    public List<AccessRequest> findRecent(int limit) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(FIND_RECENT)) {

            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                List<AccessRequest> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading recent access requests", e);
        }
    }

    @Override
    public boolean existsPendingByUsernameOrEmail(String username, String email) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(EXISTS_PENDING_BY_USERNAME_OR_EMAIL)) {

            stmt.setString(1, username);
            stmt.setString(2, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking pending access requests", e);
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(EXISTS_BY_EMAIL)) {

            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking access request email", e);
        }
    }

    @Override
    public AccessRequest updateStatus(long id, AccessRequestStatus status, Long reviewedByUserId) {
        try (Connection conn = ConnectionFactory.getConnection();
             PreparedStatement stmt = conn.prepareStatement(UPDATE_STATUS)) {

            stmt.setString(1, status.name());
            if (reviewedByUserId == null) {
                stmt.setNull(2, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(2, reviewedByUserId);
            }
            stmt.setLong(3, id);
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Access request not found: " + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating access request status", e);
        }

        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found after update: " + id));
    }

    private AccessRequest mapRow(ResultSet rs) throws SQLException {
        AccessRequest accessRequest = new AccessRequest();
        accessRequest.setId(rs.getLong("id"));
        accessRequest.setFirstName(rs.getString("first_name"));
        accessRequest.setLastName(rs.getString("last_name"));
        accessRequest.setEmail(rs.getString("email"));
        accessRequest.setUsername(rs.getString("username"));
        accessRequest.setPasswordHash(rs.getString("password_hash"));
        accessRequest.setRequestedRole(UserRole.valueOf(rs.getString("requested_role")));
        accessRequest.setStatus(AccessRequestStatus.valueOf(rs.getString("status")));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            accessRequest.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        if (reviewedAt != null) {
            accessRequest.setReviewedAt(reviewedAt.toLocalDateTime());
        }

        long reviewedByUserId = rs.getLong("reviewed_by_user_id");
        accessRequest.setReviewedByUserId(rs.wasNull() ? null : reviewedByUserId);

        return accessRequest;
    }
}
