package com.winlator;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XServerPathTest {
    @Test
    public void makesTheExecutableRelativeToTheWorkingDirectory() {
        assertEquals("_windowsnosteam\\win64\\Darkest.exe",
            XServerDisplayActivity.relativeTo("G:\\", "G:\\_windowsnosteam\\win64\\Darkest.exe"));
        assertEquals("win64\\Darkest.exe",
            XServerDisplayActivity.relativeTo("G:\\game", "G:\\game\\win64\\Darkest.exe"));
    }
}
