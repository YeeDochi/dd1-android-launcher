package com.winlator.dd1;

public final class DD1WorkshopDescription {
    private DD1WorkshopDescription() {}

    public static String clean(String bbcode) {
        if (bbcode == null) return "";
        return bbcode.replace("\r", "")
            .replaceAll("(?is)\\[img(?:=[^]]*)?].*?\\[/img]", "")
            .replaceAll("(?is)\\[url=[^]]*](.*?)\\[/url]", "$1")
            .replaceAll("(?i)\\[br\\s*/?]", "\n")
            .replaceAll("(?i)\\[\\*]", "\n")
            .replaceAll("(?is)\\[/?[^]]+]", "")
            .replaceAll("[ \\t]+\n", "\n")
            .replaceAll("\n{3,}", "\n\n")
            .trim();
    }
}
