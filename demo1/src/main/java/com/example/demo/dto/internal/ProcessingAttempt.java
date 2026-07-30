package com.example.demo.dto.internal;

/** Immutable details for one simulated network attempt. */
public final class ProcessingAttempt {

    private final int attemptNumber;
    private final int simulatedDelaySeconds;
    private final int actualWaitSeconds;
    private final boolean timedOut;

    public ProcessingAttempt(int attemptNumber, int simulatedDelaySeconds,
                             int actualWaitSeconds, boolean timedOut) {
        this.attemptNumber = attemptNumber;
        this.simulatedDelaySeconds = simulatedDelaySeconds;
        this.actualWaitSeconds = actualWaitSeconds;
        this.timedOut = timedOut;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public int getSimulatedDelaySeconds() {
        return simulatedDelaySeconds;
    }

    public int getActualWaitSeconds() {
        return actualWaitSeconds;
    }

    public boolean isTimedOut() {
        return timedOut;
    }
}
