package it.SimoSW.model.dao;

import java.util.Optional;

public interface ReminderTemplateDAO {

    Optional<String> findTemplateByTherapistId(long therapistId);

    void saveTemplate(long therapistId, String template);
}
