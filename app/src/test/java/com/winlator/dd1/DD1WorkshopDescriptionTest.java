package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DD1WorkshopDescriptionTest {
    @Test
    public void bbcodeBecomesReadablePlainText() {
        assertEquals("Title\nRead this\n\nFirst\nSecond",
            DD1WorkshopDescription.clean("[h1]Title[/h1]\n[url=https://x]Read this[/url]"
                + "\n[list][*]First[*]Second[/list][img]https://image[/img]"));
    }
}
