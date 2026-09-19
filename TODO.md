# TODO / ideas

## Richer evaluation function (Lieberum-style)

`BoardEvaluator` (`backend/src/main/java/io/github/ivarm1984/bot/impl/heuristic/BoardEvaluator.java`)
currently blends mobility, queen-move territory, and king-move territory. Jens Lieberum's published
Amazons evaluation (used in his gold-medal program *Amazong*) goes further: it weighs territory by how
*contested* each square is rather than a flat "closer wins it" count, and separately tracks "liberties"
(how boxed-in each queen already is) instead of folding everything into raw mobility. Porting that finer
weighting into `BoardEvaluator.phaseAwareCombined` (or a new variant) and feeding it into the existing
alpha-beta/transposition bots is a plausible next step for a stronger search-based bot, without touching
the MCTS side.

Not started - noted here as a direction, not a plan.
