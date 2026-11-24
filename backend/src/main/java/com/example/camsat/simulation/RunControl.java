package com.example.camsat.simulation;

/** Cooperative pause/stop flags shared between request threads and the simulation worker of one run. */
final class RunControl {
    private boolean paused;
    private boolean stopped;

    synchronized void pause() { paused = true; }

    synchronized void resume() { paused = false; notifyAll(); }

    synchronized void stop() { stopped = true; notifyAll(); }

    /** Blocks while paused. Returns false if the run was stopped (cancelled / shutdown) and the worker must exit. */
    synchronized boolean awaitRunnable() throws InterruptedException {
        while (paused && !stopped) wait(250);
        return !stopped;
    }

    synchronized boolean isStopped() { return stopped; }
}
