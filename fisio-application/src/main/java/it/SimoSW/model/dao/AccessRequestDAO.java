package it.SimoSW.model.dao;

import it.SimoSW.model.AccessRequest;
import it.SimoSW.model.AccessRequestStatus;

import java.util.List;
import java.util.Optional;

public interface AccessRequestDAO {
    AccessRequest save(AccessRequest accessRequest);

    Optional<AccessRequest> findById(long id);

    List<AccessRequest> findByStatus(AccessRequestStatus status);

    List<AccessRequest> findRecent(int limit);

    boolean existsPendingByUsernameOrEmail(String username, String email);

    boolean existsByEmail(String email);

    AccessRequest updateStatus(long id, AccessRequestStatus status, Long reviewedByUserId);
}
