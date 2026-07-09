package de.puzzlewerk.core

/**
 * Abstraktion über die Systemzeit, damit zeitabhängige Logik testbar bleibt (Regel C2).
 */
public interface WallClock {

    /** Aktuelle Zeit in Millisekunden seit Epoch. */
    public fun nowMillis(): Long
}

/** Produktions-Implementierung auf Basis der Systemuhr. */
public class SystemWallClock : WallClock {

    override fun nowMillis(): Long = System.currentTimeMillis()
}
