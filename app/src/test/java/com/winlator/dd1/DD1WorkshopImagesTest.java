package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class DD1WorkshopImagesTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void sameUrlUsesTheSameOpaqueCacheFile() {
        File one = DD1WorkshopImages.file(folder.getRoot(), "https://cdn/a.jpg?x=1");
        File two = DD1WorkshopImages.file(folder.getRoot(), "https://cdn/a.jpg?x=1");

        assertEquals(one, two);
        assertEquals("dd1-workshop-images", one.getParentFile().getName());
        assertFalse(one.getName().contains("a.jpg"));
        assertTrue(one.getName().matches("[0-9a-f]{64}\\.img"));
    }

    @Test
    public void copyRejectsAResponseBeyondTheLimit() throws Exception {
        byte[] input = new byte[] {1, 2, 3, 4, 5};
        try {
            DD1WorkshopImages.copy(new ByteArrayInputStream(input),
                new ByteArrayOutputStream(), 4);
            fail("expected IOException");
        }
        catch (IOException expected) {
            assertEquals("Workshop image is too large", expected.getMessage());
        }
    }
}
