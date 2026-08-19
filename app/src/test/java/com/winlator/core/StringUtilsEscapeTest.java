package com.winlator.core;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class StringUtilsEscapeTest {
    @Test
    public void keepsWindowsPathsIntactThroughTheCommandSplitter() {
        assertArrayEquals(new String[] {"/dir", "G:\\_windowsnosteam\\win64", "Darkest.exe"},
            ProcessHelper.splitCommand("/dir " + StringUtils.escapeSpaces("G:\\_windowsnosteam\\win64")
                + " " + StringUtils.escapeSpaces("Darkest.exe")));
    }

    @Test
    public void keepsSpacedPathsAsOneArgument() {
        assertArrayEquals(new String[] {"/dir", "G:\\My Games", "Darkest.exe"},
            ProcessHelper.splitCommand("/dir " + StringUtils.escapeSpaces("G:\\My Games")
                + " " + StringUtils.escapeSpaces("Darkest.exe")));
    }
}
