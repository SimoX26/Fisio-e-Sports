package it.SimoSW.model.dao;

import it.SimoSW.model.User;
import it.SimoSW.model.UserRole;

import java.util.Optional;

public interface UserDAO {
    User save(User user);

    Optional<User> findByUsername(String username);

    Optional<Long> findIdByUsernameAndRole(String username, UserRole role);

    boolean existsByIdAndRole(long id, UserRole role);

    void updatePasswordHashByUsername(String username, String passwordHash);
}
