package com.settorise;

import com.settorise.config.AppConfig.Section;
import com.settorise.news.Classifier;
import com.settorise.news.Story;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassifierTest {

    private final Classifier classifier = new Classifier();

    private Classifier.Tags tag(String headline, String summary, Section section) {
        return classifier.tag(new Story("id", headline, "Outlet", summary, "https://x", Instant.now(), section, null));
    }

    @Test
    void usBallotStoryIsElectionRelated() {
        assertTrue(tag("Washington voters approve ballot initiative", "", Section.GENERAL).electionRelated());
    }

    @Test
    void foreignElectionWithoutUsSignalDoesNotGetVoteGovLink() {
        Classifier.Tags t = tag("Israel's president taps rival to form government after election", "", Section.WORLD);
        assertTrue(t.global());
        assertFalse(t.electionRelated());
    }

    @Test
    void jobOfferIsNotEconomy() {
        assertFalse(tag("Trump offers press secretary job to UFC CEO", "", Section.GENERAL).topics().contains("Economy"));
    }

    @Test
    void electoralCollegeIsNotEducation() {
        assertFalse(tag("Democrats target the Electoral College", "", Section.GENERAL).topics().contains("Education"));
    }

    @Test
    void sectionHintDrivesTabs() {
        assertTrue(tag("Something happened", "", Section.POLITICS).politics());
        assertTrue(tag("Something happened", "", Section.WORLD).global());
    }
}
