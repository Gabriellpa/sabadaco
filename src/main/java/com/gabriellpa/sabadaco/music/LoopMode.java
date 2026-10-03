package com.gabriellpa.sabadaco.music;

public enum LoopMode {
    OFF, TRACK, QUEUE;

    public LoopMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
