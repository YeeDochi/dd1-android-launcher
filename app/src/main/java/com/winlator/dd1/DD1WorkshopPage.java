package com.winlator.dd1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DD1WorkshopPage {
    public final List<DD1WorkshopItem> items;
    public final int total;

    public DD1WorkshopPage(List<DD1WorkshopItem> items, int total) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.total = total;
    }
}
