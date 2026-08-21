package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DD1CpuBudgetTest {
    // Nothing here knows which core is which, only that Android numbers them from
    // the slowest up. The prime core is the last one on every phone this runs on.
    @Test
    public void allCoresIsEveryCore() {
        assertEquals("0,1,2,3,4,5,6,7", DD1CpuBudget.list(DD1CpuBudget.ALL, 8));
    }

    @Test
    public void withoutThePrimeCoreDropsOnlyTheLastOne() {
        assertEquals("0,1,2,3,4,5,6", DD1CpuBudget.list(DD1CpuBudget.NO_PRIME, 8));
    }

    @Test
    public void efficiencyOnlyKeepsTheSlowerHalf() {
        assertEquals("0,1,2,3", DD1CpuBudget.list(DD1CpuBudget.EFFICIENCY, 8));
    }

    // A six-core phone still has to end up with something runnable.
    @Test
    public void oddCoreCountsStillLeaveACoreToRunOn() {
        assertEquals("0,1,2,3,4", DD1CpuBudget.list(DD1CpuBudget.NO_PRIME, 6));
        assertEquals("0,1,2", DD1CpuBudget.list(DD1CpuBudget.EFFICIENCY, 6));
        assertEquals("0", DD1CpuBudget.list(DD1CpuBudget.NO_PRIME, 1));
        assertEquals("0", DD1CpuBudget.list(DD1CpuBudget.EFFICIENCY, 1));
    }

    // Reading a container back has to land on the choice that produced it, or the
    // screen would show the wrong one selected.
    @Test
    public void aListReadsBackAsTheChoiceThatWroteIt() {
        assertEquals(DD1CpuBudget.ALL, DD1CpuBudget.of("0,1,2,3,4,5,6,7", 8));
        assertEquals(DD1CpuBudget.NO_PRIME, DD1CpuBudget.of("0,1,2,3,4,5,6", 8));
        assertEquals(DD1CpuBudget.EFFICIENCY, DD1CpuBudget.of("0,1,2,3", 8));
        // Anything the launcher did not write is left alone as the full set.
        assertEquals(DD1CpuBudget.ALL, DD1CpuBudget.of("2,3", 8));
        assertEquals(DD1CpuBudget.ALL, DD1CpuBudget.of(null, 8));
    }
}
