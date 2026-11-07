package com.settorise.news;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PreviewImagesTest {

    @Test
    void readsOgImageAndDecodesEntities() {
        String head = "<meta name=\"twitter:image\" content=\"https://x.test/t.jpg\" />"
                + "<meta property=\"og:image\" content=\"https://x.test/a.webp?crop=0px&amp;w=1200\" />";
        assertEquals("https://x.test/a.webp?crop=0px&w=1200", PreviewImages.fromHead(head));
    }

    @Test
    void acceptsContentBeforeProperty() {
        assertEquals("https://x.test/b.jpg",
                PreviewImages.fromHead("<META content='https://x.test/b.jpg' property='og:image'>"));
    }

    @Test
    void fallsBackToTwitterImage() {
        assertEquals("https://x.test/t.jpg",
                PreviewImages.fromHead("<meta name=\"twitter:image\" content=\"https://x.test/t.jpg\">"));
    }

    @Test
    void nullWhenNoImage() {
        assertNull(PreviewImages.fromHead("<title>x</title><meta name=\"description\" content=\"y\">"));
    }
}
