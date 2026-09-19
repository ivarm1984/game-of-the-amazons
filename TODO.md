# TODO / ideas

## Richer evaluation function (Lieberum-style)

`BoardEvaluator` (`backend/src/main/java/io/github/ivarm1984/bot/impl/heuristic/BoardEvaluator.java`)
currently blends mobility, queen-move territory, and king-move territory. Jens Lieberum's published
Amazons evaluation (used in his gold-medal program *Amazong*) goes further: it weighs territory by how
*contested* each square is rather than a flat "closer wins it" count, and separately tracks "liberties"
(how boxed-in each queen already is) instead of folding everything into raw mobility. Porting that finer
weighting into `BoardEvaluator.phaseAwareCombined` (or a new variant) and feeding it into the existing
alpha-beta/transposition bots is a plausible next step for a stronger search-based bot.

This would now reach the MCTS side too: `InformedMonteCarloBot` terminates its playouts after two plies
and scores the position with `BoardEvaluator.combined(board, me, 0.0, 1.0, 1.0)`, so it is a direct
consumer of the territory terms. One caveat learned the hard way there - it evaluates once per
simulation, tens of thousands of times per move, so a finer evaluation only helps if it stays cheap.
Adding the existing `mobilityDiff` term to that call (two full move generations) cost far more in lost
simulations than it gained in accuracy: roughly a 1-in-8 win rate against the territory-only version.

Not started - noted here as a direction, not a plan.

## Finish verifying the Informed Monte Carlo rewrite

The rewrite of `InformedMonteCarloBot` (early playout termination, one-pass root move ordering,
sampled child expansion) was measured at **0.5-1s per move**, where it went 16-0 against Territory,
Minimax Mobility, Greedy Mobility, plain MCTS, and its own previous implementation. Two gaps remain
before those numbers can be trusted as a ranking:

- **Never confirmed at the tournament's real 3s budget.** The 3s head-to-head run was killed by
  system memory pressure before finishing a single pairing, so no win rates exist at that budget.
  Only the throughput fix is confirmed there (opening simulations 309 -> 3740, visits on the chosen
  move 8 -> 76). This matters because the design wins partly by buying more simulations, and a
  longer budget hands the same gift to every opponent - the margin against Territory Bot could
  compress. Rerun: 8+ games per pairing, alternating colors, at `softMoveBudgetMs = 3000`.
- **Untested against the search bots**: Iterative Deepening, Alpha-Beta Master, Transposition
  Alpha-Beta, Minimax King-Distance, Minimax Territory. These were simply not in the arena, not
  found weak - Iterative Deepening was 2nd in the last tournament.

Note on methodology, learned here: a full round-robin gives each bot only ~9-11 games and the whole
Elo table spans ~200 points, so neighbouring ranks are indistinguishable from noise. The standings
that prompted this work put the bot 10th of 11 and gave no hint that the cause was a ~12x
performance bug. Judge bot changes by direct head-to-head against the previous build, run in
parallel across cores, not by tournament placing.
