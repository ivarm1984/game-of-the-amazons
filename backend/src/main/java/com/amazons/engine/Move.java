package com.amazons.engine;

/**
 * One full Amazons turn: move an amazon, then shoot an arrow from its new square.
 * The two halves are indivisible - they are always applied together.
 */
public record Move(Position amazonFrom, Position amazonTo, Position arrowTo) {

    @Override
    public String toString() {
        return amazonFrom + "-" + amazonTo + "/" + arrowTo;
    }
}
