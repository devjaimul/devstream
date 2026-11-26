package com.devsstream.terminal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Simulated card terminal SDK. Keep this file in Java and do not change it. */
public final class LegacyCardTerminal {

    public interface Callback {
        void onApproved(String authCode);
        void onDeclined(String reason);
    }

    private final Map<String, String> status = new ConcurrentHashMap<>();
    private final Map<String, Integer> attempts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();

    /** Callbacks arrive on a background thread. Calling again with the same ref is a second real charge. */
    public void charge(long amountPoisha, String ref, Callback cb) {
        attempts.merge(ref, 1, Integer::sum);
        long tail = (amountPoisha / 100) % 100;               // last two digits of the BDT amount
        if (tail == 13) {
            status.put(ref, "DECLINED");
            worker.schedule(() -> cb.onDeclined("INSUFFICIENT_FUNDS"), 1, TimeUnit.SECONDS);
            return;
        }
        String authCode = "AUTH-" + Math.abs(ref.hashCode() % 100000);
        status.put(ref, "APPROVED");                           // the money is taken from here on
        if (tail == 77) return;                                // approved, but the callback is lost
        worker.schedule(() -> cb.onApproved(authCode), 2, TimeUnit.SECONDS);
        if (tail == 55) worker.schedule(() -> cb.onApproved(authCode), 3, TimeUnit.SECONDS); // duplicate
    }

    /** "APPROVED", "DECLINED" or "NOT_FOUND". Never charges. */
    public String queryStatus(String ref) {
        return status.getOrDefault(ref, "NOT_FOUND");
    }

    /** For tests: how many times charge() was called with this ref. */
    public int attempts(String ref) {
        return attempts.getOrDefault(ref, 0);
    }
}
