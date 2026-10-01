package it.SimoSW.service.whatsapp;

import it.SimoSW.util.AppProperties;

public class WhatsAppConfigurationService {

    public boolean hasConfiguration(long therapistId) {
        if (!Boolean.parseBoolean(AppProperties.get("whatsapp.baileys.enabled", "false"))) {
            return false;
        }

        Long configuredTherapistId = parseOptionalLong(AppProperties.get("whatsapp.baileys.therapistId"));
        return configuredTherapistId == null || configuredTherapistId == therapistId;
    }

    private Long parseOptionalLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
