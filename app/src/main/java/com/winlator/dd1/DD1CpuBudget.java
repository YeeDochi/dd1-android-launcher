package com.winlator.dd1;

// Which cores the runtime is allowed to use. Translating x86 turned out to be
// where the battery goes - dropping the resolution took 9% off it, raising
// box64's preset put 7% back on - and with no frame limiter anywhere in the
// stack, the only way left to spend less is to leave the fastest cores alone.
//
// Android numbers cores from the slowest up, so the prime core is the last one
// and the efficiency cores are the first half. That holds on every phone this
// runs on and needs no table of models.
public final class DD1CpuBudget {
    public static final int ALL = 0;
    public static final int NO_PRIME = 1;
    public static final int EFFICIENCY = 2;

    private DD1CpuBudget() {}

    public static int cores() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    public static String list(int budget, int cores) {
        int keep;
        if (budget == NO_PRIME) keep = cores - 1;
        else if (budget == EFFICIENCY) keep = cores / 2;
        else keep = cores;
        keep = Math.max(1, Math.min(cores, keep));

        StringBuilder list = new StringBuilder();
        for (int core = 0; core < keep; core++) {
            if (core > 0) list.append(',');
            list.append(core);
        }
        return list.toString();
    }

    // A list the launcher did not write - the runtime's own default, or something
    // set by hand - is left alone rather than being forced onto one of these.
    public static int of(String cpuList, int cores) {
        if (cpuList == null) return ALL;
        String trimmed = cpuList.trim();
        if (trimmed.equals(list(NO_PRIME, cores))) return NO_PRIME;
        if (trimmed.equals(list(EFFICIENCY, cores))) return EFFICIENCY;
        return ALL;
    }
}
