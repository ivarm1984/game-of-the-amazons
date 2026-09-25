package io.github.ivarm1984.bot.impl.mcts;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.BotMetadata;
import io.github.ivarm1984.bot.Deadline;
import io.github.ivarm1984.engine.Move;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static io.github.ivarm1984.bot.impl.mcts.FastBoard.DIRECTIONS;
import static io.github.ivarm1984.bot.impl.mcts.FastBoard.EMPTY;

/**
 * Monte Carlo Tree Search with no playouts at all: every new leaf is scored once by
 * {@link TerritoryEvaluator}, turned into a win probability, and that probability is backed up
 * the tree instead of a 0/1 game result.
 *
 * <p>{@link InformedMonteCarloBot} showed that early playout termination is the big win for MCTS
 * in Amazons. This bot follows that result all the way down and spends the time on three things:
 *
 * <ul>
 *   <li><b>Simulation throughput.</b> The search runs on {@link FastBoard}, a mutable padded
 *   board with moves packed into {@code int}s and made/unmade in place, and keeps its tree in
 *   parallel primitive arrays rather than node objects. A simulation allocates nothing, so a
 *   budget buys tens of times more of them than the object-based engine allows.</li>
 *   <li><b>A better leaf value.</b> Lieberum-style territory evaluation rather than a flat
 *   closer-square count, backed up as a probability so the tree can tell a narrow edge from a
 *   crushing one.</li>
 *   <li><b>Move ordering where it pays.</b> The root orders every move by a full evaluation of
 *   the position it leads to, and seeds each root child with that value. Interior nodes order
 *   their moves with a cheap arrow-and-mobility heuristic, keep only the best
 *   {@link Tuning#minCandidates} or so, and widen progressively into them, fetching more from
 *   the same deterministic ordering only if a node is visited often enough to need them.</li>
 * </ul>
 */
@BotMetadata(id = "mcts-territory", displayName = "Territory Monte Carlo Bot", difficulty = 11,
        description = "Playout-free MCTS on a fast in-place board: every leaf is scored by a "
                + "Lieberum-style territory evaluation and backed up as a win probability, with "
                + "progressive widening over heuristically ordered moves.")
@Component
public class TerritoryMonteCarloBot implements Bot {

    /** Search parameters, split out so variants can be compared head to head. */
    record Tuning(double exploration, double wideningBase, double wideningExponent,
                         double valueScale, int minCandidates) {
    }

    static final Tuning DEFAULT = new Tuning(0.35, 2.0, 0.5, 4.0, 24);

    private final Tuning tuning;

    public TerritoryMonteCarloBot() {
        this(DEFAULT);
    }

    TerritoryMonteCarloBot(Tuning tuning) {
        this.tuning = tuning;
    }

    @Override
    public Move decideMove(BotInput input) {
        if (input.legalMoves().size() == 1) {
            return input.legalMoves().get(0);
        }
        FastBoard board = FastBoard.of(input.state().board(), input.myColor());
        int best = new Search(board, tuning).run(input.deadline());
        Move chosen = FastBoard.toMove(best);
        // Never trust the private engine over the shared one with a forfeit on the line.
        return input.legalMoves().contains(chosen) ? chosen : input.legalMoves().get(0);
    }

    /** One move's search; owns all its working memory, so concurrent games never share any. */
    static final class Search {

        private static final byte UNEXPANDED = 0;
        private static final byte EXPANDED = 1;
        private static final byte TERMINAL = 2;
        private static final int MAX_DEPTH = 128;

        private final FastBoard board;
        private final Tuning tuning;
        private final TerritoryEvaluator evaluator = new TerritoryEvaluator();
        private final int[] moveBuffer = new int[FastBoard.MAX_MOVES];
        private final long[] keyBuffer = new long[FastBoard.MAX_MOVES];
        private final int[] arrowValue = new int[FastBoard.CELLS];
        private final int[] path = new int[MAX_DEPTH];

        // The tree, one slot per node. A node's children occupy [first, first + count) and are
        // kept in move-ordering order; value is summed from the point of view of the player who
        // played 'move' into the node.
        private int[] move;
        private int[] first;
        private int[] count;
        private int[] totalMoves;
        private int[] visits;
        private double[] value;
        private byte[] state;
        private int size;

        long simulations;

        Search(FastBoard board, Tuning tuning) {
            this.board = board;
            this.tuning = tuning;
            int capacity = 1 << 16;
            move = new int[capacity];
            first = new int[capacity];
            count = new int[capacity];
            totalMoves = new int[capacity];
            visits = new int[capacity];
            value = new double[capacity];
            state = new byte[capacity];
        }

        int run(Deadline deadline) {
            int root = allocate(1);
            expandRoot(root, deadline);
            while (!deadline.hasExpired()) {
                simulate(root);
                simulations++;
            }
            int best = -1;
            for (int c = first[root], end = c + count[root]; c < end; c++) {
                if (best < 0 || visits[c] > visits[best]
                        || (visits[c] == visits[best] && value[c] > value[best])) {
                    best = c;
                }
            }
            return move[best];
        }

        private void simulate(int root) {
            int node = root;
            int depth = 0;
            path[0] = root;
            double leafValue;
            while (true) {
                if (state[node] == TERMINAL) {
                    leafValue = 1.0; // the side to move here has no move: whoever moved in has won
                    break;
                }
                if (state[node] == UNEXPANDED) {
                    if (visits[node] == 0) {
                        leafValue = leafValue();
                        break;
                    }
                    expand(node);
                    if (state[node] == TERMINAL) {
                        leafValue = 1.0;
                        break;
                    }
                }
                int child = select(node);
                board.make(move[child]);
                node = child;
                path[++depth] = node;
            }
            double v = leafValue;
            for (int i = depth; i >= 0; i--) {
                int n = path[i];
                visits[n]++;
                value[n] += v;
                v = 1.0 - v;
                if (i > 0) {
                    board.unmake(move[n]);
                }
            }
        }

        /** Value of the current board for the player who just moved into it. */
        private double leafValue() {
            double eval = evaluator.evaluate(board);
            if (eval == TerritoryEvaluator.LOST) {
                return 1.0;
            }
            return winProbability(-eval);
        }

        private double winProbability(double eval) {
            return 1.0 / (1.0 + Math.exp(-eval / tuning.valueScale()));
        }

        private int select(int node) {
            int parentVisits = visits[node];
            int allowed = allowedChildren(parentVisits);
            if (allowed > count[node] && count[node] < totalMoves[node]) {
                widen(node, allowed);
            }
            int start = first[node];
            int end = start + Math.min(allowed, count[node]);
            double logParent = Math.log(Math.max(1, parentVisits));
            double exploration = tuning.exploration();
            int best = start;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int c = start; c < end; c++) {
                int n = visits[c];
                if (n == 0) {
                    return c; // children are in move-ordering order: try the best untried one
                }
                double score = value[c] / n + exploration * Math.sqrt(logParent / n);
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            return best;
        }

        private int allowedChildren(int parentVisits) {
            return (int) Math.ceil(tuning.wideningBase() * Math.pow(parentVisits + 1, tuning.wideningExponent()));
        }

        /**
         * The root orders every legal move by a full evaluation of the position after it, and
         * seeds each child with that evaluation as its first visit - the very value its first
         * simulation would have produced, so it is free information rather than a bias.
         *
         * <p>This is the one step not bounded by the simulation loop's deadline check - thousands
         * of full evaluations, which on a cold JVM or a starved CPU can take far longer than the
         * few tens of milliseconds they normally do - so it watches the deadline itself and, if it
         * runs out, keeps only the moves it got to.
         */
        private void expandRoot(int root, Deadline deadline) {
            int n = board.generateMoves(moveBuffer);
            if (n == 0) {
                state[root] = TERMINAL;
                return;
            }
            double[] values = new double[n];
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) {
                if ((i & 63) == 63 && deadline.hasExpired()) {
                    n = i;
                    break;
                }
                int m = moveBuffer[i];
                board.make(m);
                double eval = evaluator.evaluate(board);
                board.unmake(m);
                values[i] = eval == TerritoryEvaluator.LOST ? 1.0 : winProbability(-eval);
                order[i] = i;
            }
            Arrays.sort(order, 0, n, (a, b) -> Double.compare(values[b], values[a]));
            int start = allocate(n);
            for (int i = 0; i < n; i++) {
                int c = start + i;
                move[c] = moveBuffer[order[i]];
                visits[c] = 1;
                value[c] = values[order[i]];
            }
            first[root] = start;
            count[root] = n;
            totalMoves[root] = n;
            state[root] = EXPANDED;
        }

        private void expand(int node) {
            int n = scoreMoves();
            if (n == 0) {
                state[node] = TERMINAL;
                return;
            }
            int keep = Math.min(n, Math.max(tuning.minCandidates(), allowedChildren(visits[node]) * 2));
            selectTop(n, keep);
            int start = allocate(keep);
            for (int i = 0; i < keep; i++) {
                move[start + i] = (int) keyBuffer[i];
            }
            first[node] = start;
            count[node] = keep;
            totalMoves[node] = n;
            state[node] = EXPANDED;
        }

        /**
         * A node visited often enough to want more children than it kept: re-derive the same
         * deterministic ordering, keep twice as many, and carry the existing children (and so their
         * subtrees) over into the front of the new, larger block.
         */
        private void widen(int node, int allowed) {
            int n = scoreMoves();
            int keep = Math.min(n, Math.max(allowed * 2, count[node] * 2));
            selectTop(n, keep);
            int oldStart = first[node];
            int oldCount = count[node];
            int start = allocate(keep);
            for (int i = 0; i < keep; i++) {
                int c = start + i;
                if (i < oldCount) {
                    int o = oldStart + i;
                    move[c] = move[o];
                    first[c] = first[o];
                    count[c] = count[o];
                    totalMoves[c] = totalMoves[o];
                    visits[c] = visits[o];
                    value[c] = value[o];
                    state[c] = state[o];
                } else {
                    move[c] = (int) keyBuffer[i];
                }
            }
            first[node] = start;
            count[node] = keep;
        }

        /**
         * Generates the side to move's moves into {@link #keyBuffer} as {@code score << 32 | move},
         * so a plain descending sort orders them best-first with ties broken deterministically.
         *
         * <p>The score is a cheap two-part estimate, additive so it costs a few operations per move:
         * the queen's gain in open lines from its move, plus a per-square arrow value favouring
         * shots that crowd an enemy queen or land on ground the enemy would otherwise take, and
         * penalising shots into the mover's own territory.
         */
        private int scoreMoves() {
            byte[] cells = board.cells;
            int me = board.sideToMove;
            int them = me ^ 1;
            int[] myQueens = board.queens[me];
            int[] theirQueens = board.queens[them];
            int[] mine = evaluator.queenDist[me];
            int[] theirs = evaluator.queenDist[them];
            evaluator.queenDistances(cells, myQueens, mine);
            evaluator.queenDistances(cells, theirQueens, theirs);

            for (int sq : TerritoryEvaluator.PLAYABLE) {
                if (cells[sq] != EMPTY) {
                    continue;
                }
                int v = 0;
                if (mine[sq] < theirs[sq]) {
                    v -= 2;
                } else if (theirs[sq] < mine[sq]) {
                    v += 1;
                } else {
                    v += 2;
                }
                if (theirs[sq] == 1) {
                    v += 1;
                }
                for (int d : DIRECTIONS) {
                    byte neighbour = cells[sq + d];
                    if (neighbour == them + 1) {
                        v += 4;
                    } else if (neighbour == me + 1) {
                        v -= 1;
                    }
                }
                arrowValue[sq] = v;
            }

            int n = 0;
            for (int q = 0; q < 4; q++) {
                int from = myQueens[q];
                int fromOpen = openLineCount(cells, from);
                cells[from] = EMPTY;
                for (int d : DIRECTIONS) {
                    for (int to = from + d; cells[to] == EMPTY; to += d) {
                        int queenScore = openLineCount(cells, to) - fromOpen;
                        for (int d2 : DIRECTIONS) {
                            for (int arrow = to + d2; cells[arrow] == EMPTY; arrow += d2) {
                                // The vacated origin was never scored as an arrow square: treat as neutral.
                                int arrowScore = arrow == from ? 0 : arrowValue[arrow];
                                long score = queenScore + 4L * arrowScore;
                                keyBuffer[n++] = score << 32 | FastBoard.encode(from, to, arrow);
                            }
                        }
                    }
                }
                cells[from] = (byte) (me + 1);
            }
            return n;
        }

        private static int openLineCount(byte[] cells, int from) {
            int total = 0;
            for (int d : DIRECTIONS) {
                for (int sq = from + d; cells[sq] == EMPTY; sq += d) {
                    total++;
                }
            }
            return total;
        }

        /** Moves the {@code keep} largest keys to the front of {@link #keyBuffer}, sorted descending. */
        private void selectTop(int n, int keep) {
            long[] keys = keyBuffer;
            if (keep < n) {
                int lo = 0;
                int hi = n - 1;
                while (lo < hi) {
                    long pivot = keys[(lo + hi) >>> 1];
                    int i = lo;
                    int j = hi;
                    while (i <= j) {
                        while (keys[i] > pivot) {
                            i++;
                        }
                        while (keys[j] < pivot) {
                            j--;
                        }
                        if (i <= j) {
                            long t = keys[i];
                            keys[i] = keys[j];
                            keys[j] = t;
                            i++;
                            j--;
                        }
                    }
                    if (keep - 1 <= j) {
                        hi = j;
                    } else if (keep - 1 >= i) {
                        lo = i;
                    } else {
                        break;
                    }
                }
            }
            Arrays.sort(keys, 0, keep);
            for (int i = 0, j = keep - 1; i < j; i++, j--) {
                long t = keys[i];
                keys[i] = keys[j];
                keys[j] = t;
            }
        }

        private int allocate(int slots) {
            int start = size;
            size += slots;
            if (size > move.length) {
                int capacity = Math.max(size, move.length * 2);
                move = Arrays.copyOf(move, capacity);
                first = Arrays.copyOf(first, capacity);
                count = Arrays.copyOf(count, capacity);
                totalMoves = Arrays.copyOf(totalMoves, capacity);
                visits = Arrays.copyOf(visits, capacity);
                value = Arrays.copyOf(value, capacity);
                state = Arrays.copyOf(state, capacity);
            }
            return start;
        }
    }
}
