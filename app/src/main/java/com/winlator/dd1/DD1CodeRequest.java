package com.winlator.dd1;

import java.util.concurrent.CompletableFuture;

// Steam asks for a Steam Guard code by handing over a future and waiting on it.
// This is that wait: what is being asked, and the two ways it can end - a code
// somebody typed, or a cancellation. It must always end. The thread on the other
// side is inside Steam's sign-in poll, and a wait nobody answers is a sign-in
// that can never be started again.
public final class DD1CodeRequest {
    private CompletableFuture<String> pending;
    private DD1SignInCode prompt;

    public synchronized CompletableFuture<String> open(DD1SignInCode asked) {
        // Steam asks again after a wrong code rather than asking twice at once, so
        // an older wait here has already been answered. If one is somehow still
        // open, it is released rather than left behind.
        release(new IllegalStateException("Steam asked for another code"));
        prompt = asked;
        pending = new CompletableFuture<>();
        return pending;
    }

    public synchronized DD1SignInCode prompt() {
        return prompt;
    }

    // False when there was nothing to answer, or nothing was typed: Steam counts
    // a blank as a wrong code and spends one of the few tries it allows.
    public synchronized boolean submit(String code) {
        if (pending == null || code == null) return false;
        String typed = code.trim();
        if (typed.isEmpty()) return false;
        CompletableFuture<String> waiting = pending;
        pending = null;
        prompt = null;
        waiting.complete(typed);
        return true;
    }

    public synchronized void cancel(String why) {
        release(new IllegalStateException(why));
    }

    private void release(Throwable why) {
        if (pending == null) return;
        CompletableFuture<String> waiting = pending;
        pending = null;
        prompt = null;
        waiting.completeExceptionally(why);
    }
}
