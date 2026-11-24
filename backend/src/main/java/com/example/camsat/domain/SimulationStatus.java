package com.example.camsat.domain;

import java.util.EnumSet;
import java.util.Set;

public enum SimulationStatus {
    CREATED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED;

    public static final Set<SimulationStatus> TERMINAL = EnumSet.of(COMPLETED, FAILED, CANCELLED);
    public static final Set<SimulationStatus> ACTIVE = EnumSet.of(RUNNING, PAUSED);

    public boolean isTerminal() { return TERMINAL.contains(this); }
}
