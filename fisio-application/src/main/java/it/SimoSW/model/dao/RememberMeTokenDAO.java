package it.SimoSW.model.dao;

import it.SimoSW.model.User;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RememberMeTokenDAO {

    void saveOrUpdate(long userId, String tokenHash, LocalDateTime expiresAt);

    Optional<User> findActiveUserByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    void deleteByUserId(long userId);

    void deleteExpired();
}
