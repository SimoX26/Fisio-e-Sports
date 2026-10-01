package it.SimoSW.model.dao;

import it.SimoSW.model.WaitlistEntry;

import java.util.List;

public interface WaitlistEntryDAO {

    WaitlistEntry insert(WaitlistEntry entry);

    List<WaitlistEntry> findAllByTherapist(long therapistId);

    void deleteByIdAndTherapist(long id, long therapistId);
}
