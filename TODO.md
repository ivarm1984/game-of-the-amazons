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

## Informed Monte Carlo rewrite: verified (2026-09-20)

Both gaps left open after the rewrite are now closed. A head-to-head arena at the tournament's real
`softMoveBudgetMs = 3000`, 8 games per pairing, alternating colors, 8 games in parallel, gave
**80-0 across all ten pairings** - 4/4 as White and 4/4 as Black in every one:

| Opponent | W - L | | Opponent | W - L |
| --- | --- | --- | --- | --- |
| Territory | 8 - 0 | | Iterative Deepening | 8 - 0 |
| Minimax Mobility | 8 - 0 | | Alpha-Beta Master | 8 - 0 |
| Greedy Mobility | 8 - 0 | | Transposition Alpha-Beta | 8 - 0 |
| Monte Carlo (plain) | 8 - 0 | | Minimax King-Distance | 8 - 0 |
| previous Informed MCTS | 8 - 0 | | Minimax Territory | 8 - 0 |

Two things this settles:

- **The margin does not compress at the longer budget.** The worry was that the design wins partly by
  buying more simulations, and that handing every opponent 3s instead of 0.5-1s would give the same
  gift back. It does not: the 16-0 measured at the short budget became 80-0 at 3s.
- **The search bots are not the exception.** They were untested, not found weak - Iterative Deepening
  placed 2nd in the last tournament. All five lose every game, both colors.

Practical note for anyone rerunning this: the arena needs a heap cap (`-Xmx8g` for 8 parallel games
was comfortable; peak usage ran ~6.3GB). An earlier attempt at this run was killed by system memory
pressure with no cap set. Each MCTS node holds the full legal move list of its position, so a 3s
search at ~3700 simulations per move is genuinely memory-hungry, and 8 uncapped JVM heaps will take
whatever the machine has. Wall time for the 80 games was 29 minutes on 12 cores.

Note on methodology, learned here: a full round-robin gives each bot only ~9-11 games and the whole
Elo table spans ~200 points, so neighbouring ranks are indistinguishable from noise. The standings
that prompted this work put the bot 10th of 11 and gave no hint that the cause was a ~12x
performance bug. Judge bot changes by direct head-to-head against the previous build, run in
parallel across cores, not by tournament placing.
