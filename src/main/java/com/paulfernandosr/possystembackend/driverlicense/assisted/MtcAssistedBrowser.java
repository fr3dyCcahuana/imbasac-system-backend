package com.paulfernandosr.possystembackend.driverlicense.assisted;

import java.util.List;
import java.util.Map;
import static com.paulfernandosr.possystembackend.driverlicense.assisted.MtcAssistedDtos.*;

public interface MtcAssistedBrowser extends AutoCloseable {
    record ChallengeView(String question, List<Option> options) {}
    record Observation(Map<String, String> identity, List<Map<String, String>> licenses, String pointsText, List<String> warnings) {}
    ChallengeView open(String documentType, String documentNumber);
    Observation submit(int imageIndex, boolean privacyAccepted);
    @Override void close();
}
