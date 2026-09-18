package com.amazons.bot;

import java.time.Duration;

/**
 * Per-move time budget. Started by the match runner immediately before calling
 * the bot, so engine/serialization overhead is never charged against thinking
 * time. Search-based bots should poll {@link #hasExpired()} inside their search
 * loop (each iterative-deepening depth, or every simulation batch in MCTS) and
 * return their best move found so far once it fires, rather than trying to
 * predict up front how deep/how many simulations they can afford.
 *
 * <p>This is the soft, self-managed half of the time contract. The match runner
 * separately enforces a hard timeout as a backstop for bots that ignore it.
 */
public final class Deadline {

    private final long deadlineNanos;

    private Deadline(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    public static Deadline startingNow(Duration softBudget) {
        return new Deadline(System.nanoTime() + softBudget.toNanos());
    }

    public boolean hasExpired() {
        return System.nanoTime() >= deadlineNanos;
    }

    public Duration remaining() {
        long remainingNanos = deadlineNanos - System.nanoTime();
        return remainingNanos > 0 ? Duration.ofNanos(remainingNanos) : Duration.ZERO;
    }

    public long remainingMillis() {
        return remaining().toMillis();
    }
}
