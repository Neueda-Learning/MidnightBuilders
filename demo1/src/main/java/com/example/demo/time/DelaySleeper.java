package com.example.demo.time;

/** Isolates blocking delay so tests can run without real waiting. */
@FunctionalInterface
public interface DelaySleeper {

    void sleepSeconds(int seconds) throws InterruptedException;
}
