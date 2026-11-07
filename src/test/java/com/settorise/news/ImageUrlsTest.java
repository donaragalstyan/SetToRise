package com.settorise.news;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageUrlsTest {

    @Test
    void dropsRepeatedHostFromPath() {
        assertEquals("https://www.ms.now/wp-content/uploads/2026/11/a.jpg?w=1000&h=563&crop=1",
                ImageUrls.clean("https://www.ms.now/www.ms.now/wp-content/uploads/2026/11/a.jpg?w=1000&h=563&crop=1"));
    }

    @Test
    void leavesNormalUrlsAlone() {
        String url = "https://static01.nyt.com/images/2026/11/07/us/a.jpg";
        assertEquals(url, ImageUrls.clean(url));
    }

    @Test
    void rejectsNonHttpUrls() {
        assertNull(ImageUrls.clean("javascript:alert(1)"));
        assertNull(ImageUrls.clean("data:image/png;base64,AAAA"));
        assertNull(ImageUrls.clean("  "));
    }
}
