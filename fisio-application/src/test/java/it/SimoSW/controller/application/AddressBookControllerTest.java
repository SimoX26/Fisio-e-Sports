package it.SimoSW.controller.application;

import it.SimoSW.exception.PatientNotFoundException;
import it.SimoSW.model.Patient;
import it.SimoSW.model.PatientState;
import it.SimoSW.model.dao.PatientDAO;
import junit.framework.TestCase;

import java.util.List;
import java.util.Optional;

public class AddressBookControllerTest extends TestCase {

    public void testTherapistCannotReadOrUpdateAnotherTherapistsPatient() {
        Patient ownedBySecondTherapist = new Patient();
        ownedBySecondTherapist.setId(7);
        ownedBySecondTherapist.setTherapistId(2);
        ownedBySecondTherapist.setState(PatientState.ACTIVE);
        FakePatientDAO dao = new FakePatientDAO(ownedBySecondTherapist);
        AddressBookController controller = new AddressBookController(dao, null, null, null, null);

        assertTrue(controller.searchPatients("", 1).isEmpty());
        try {
            controller.getPatientById(7, 1);
            fail("La scheda di un altro terapista non deve essere leggibile");
        } catch (PatientNotFoundException expected) {
            // La ricerca per ID non ha trovato una scheda accessibile.
        }

        Patient changed = new Patient();
        changed.setId(7);
        try {
            controller.updatePatientProfile(changed, 1);
            fail("La scheda di un altro terapista non deve essere modificabile");
        } catch (PatientNotFoundException expected) {
            assertFalse(dao.updated);
        }
    }

    public void testNewPatientBelongsToAuthenticatedTherapist() {
        FakePatientDAO dao = new FakePatientDAO(null);
        AddressBookController controller = new AddressBookController(dao, null, null, null, null);
        Patient patient = new Patient();

        controller.registerPatient(patient, 4);

        assertEquals(4, patient.getTherapistId());
        assertEquals(PatientState.ACTIVE, patient.getState());
    }

    private static final class FakePatientDAO implements PatientDAO {
        private final Patient patient;
        private boolean updated;

        private FakePatientDAO(Patient patient) {
            this.patient = patient;
        }

        @Override
        public Patient save(Patient value) {
            return value;
        }

        @Override
        public Patient update(Patient value) {
            updated = true;
            return value;
        }

        @Override
        public Optional<Patient> findByIdForTherapist(long id, long therapistId) {
            return patient != null && patient.getId() == id && patient.getTherapistId() == therapistId
                    ? Optional.of(patient) : Optional.empty();
        }

        @Override
        public List<Patient> searchForTherapist(String query, long therapistId) {
            return patient != null && patient.getTherapistId() == therapistId ? List.of(patient) : List.of();
        }

        @Override
        public void mergeInto(long sourcePatientId, long targetPatientId, long therapistId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByIdDetachingHistory(long id, String fallbackTitle, long therapistId) {
            throw new UnsupportedOperationException();
        }
    }
}
