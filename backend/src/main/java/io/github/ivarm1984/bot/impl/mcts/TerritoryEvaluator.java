package io.github.ivarm1984.bot.impl.mcts;

import java.util.Arrays;

import static io.github.ivarm1984.bot.impl.mcts.FastBoard.CELLS;
import static io.github.ivarm1984.bot.impl.mcts.FastBoard.DIRECTIONS;
import static io.github.ivarm1984.bot.impl.mcts.FastBoard.EMPTY;

/**
 * Amazons position evaluation after Jens Lieberum ("An evaluation function for the game of
 * Amazons", 2005), the design behind his tournament program Amazong. Where
 * {@code BoardEvaluator} gives each empty square to whichever side is strictly closer and counts,
 * this separates four views of territory and blends them by how much contact is left on the board:
 *
 * <ul>
 *   <li><b>t1 / t2</b> - ownership by queen-move and king-move distance, with ties worth a small
 *   {@link #TEMPO} to the side to move, since it reaches a shared square first.</li>
 *   <li><b>c1 / c2</b> - smoothed versions: c1 weighs each square by how <em>much</em> closer one
 *   side is ({@code 2^-d}), c2 by the king-distance gap. These see an advantage building before it
 *   flips a square outright, which is what matters while the board is still open.</li>
 *   <li><b>w</b> - how contested the board still is: the sum over squares both sides can reach of
 *   {@code 2^-|d1 - d2|}. It is large in the opening and falls to zero once every region is
 *   sealed, and it is what moves the weighting from the smooth terms to exact counting t1.</li>
 *   <li><b>m</b> - queen liberty: how much open room each queen has around it, taken through a
 *   square root so that boxing in one nearly-trapped queen counts for more than slightly
 *   cramping a free one.</li>
 * </ul>
 *
 * <p>Holds its scratch arrays, so each search needs its own instance; not thread-safe.
 */
final class TerritoryEvaluator {

    static final int FAR = 99;
    static final double TEMPO = 0.2;
    /** Returned for a side to move that has no legal move - a lost position. */
    static final double LOST = -1000;

    private static final double[] POW2_NEG = new double[FAR + 1];
    static {
        for (int d = 0; d < FAR; d++) {
            POW2_NEG[d] = Math.pow(2, -d);
        }
        POW2_NEG[FAR] = 0;
    }

    /** Every on-board square, as padded indices. */
    static final int[] PLAYABLE = new int[100];
    static {
        int i = 0;
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 10; col++) {
                PLAYABLE[i++] = FastBoard.square(row, col);
            }
        }
    }

    final int[][] queenDist = new int[2][CELLS];
    final int[][] kingDist = new int[2][CELLS];
    private final int[] queue = new int[CELLS];

    /** Evaluation from the side to move's point of view, roughly in units of squares. */
    double evaluate(FastBoard board) {
        if (!board.hasMove()) {
            return LOST;
        }
        byte[] cells = board.cells;
        int me = board.sideToMove;
        int them = me ^ 1;
        queenDistances(cells, board.queens[me], queenDist[me]);
        queenDistances(cells, board.queens[them], queenDist[them]);
        kingDistances(cells, board.queens[me], kingDist[me]);
        kingDistances(cells, board.queens[them], kingDist[them]);
        int[] q1 = queenDist[me];
        int[] q2 = queenDist[them];
        int[] k1 = kingDist[me];
        int[] k2 = kingDist[them];

        double t1 = 0;
        double t2 = 0;
        double c1 = 0;
        double c2 = 0;
        double w = 0;
        for (int sq : PLAYABLE) {
            if (cells[sq] != EMPTY) {
                continue;
            }
            int a = q1[sq];
            int b = q2[sq];
            if (a < b) {
                t1 += 1;
            } else if (b < a) {
                t1 -= 1;
            } else if (a != FAR) {
                t1 += TEMPO;
            }
            c1 += POW2_NEG[a] - POW2_NEG[b];
            if (a != FAR && b != FAR) {
                w += POW2_NEG[Math.abs(a - b)];
            }

            int ka = k1[sq];
            int kb = k2[sq];
            if (ka < kb) {
                t2 += 1;
            } else if (kb < ka) {
                t2 -= 1;
            } else if (ka != FAR) {
                t2 += TEMPO;
            }
            c2 += Math.max(-1.0, Math.min(1.0, (kb - ka) / 6.0));
        }
        c1 *= 2;

        double m = liberty(cells, board.queens[me]) - liberty(cells, board.queens[them]);

        // Contact-based phase: 1.0 while the board is wide open, 0.0 once every region is sealed.
        double open = Math.max(0.0, Math.min(1.0, w / 50.0));
        double wt1 = 0.9 - 0.7 * open;
        double wt2 = 0.1 + 0.25 * open;
        double wc1 = 0.05 + 0.15 * open;
        double wc2 = 0.05 + 0.15 * open;
        double wm = 0.02 + 0.3 * open;
        return wt1 * t1 + wt2 * t2 + wc1 * c1 + wc2 * c2 + wm * m;
    }

    /**
     * Sum over one side's queens of the square root of its liberty: every square it can reach in
     * one queen move, weighted by that square's own empty neighbours and discounted by distance
     * along the ray, so near, open squares count most.
     */
    private double liberty(byte[] cells, int[] queens) {
        double total = 0;
        for (int queen : queens) {
            double liberty = 0;
            for (int d : DIRECTIONS) {
                int step = 1;
                for (int sq = queen + d; cells[sq] == EMPTY; sq += d, step++) {
                    liberty += (double) emptyNeighbours(cells, sq) / step;
                }
            }
            total += Math.sqrt(liberty);
        }
        return total;
    }

    private static int emptyNeighbours(byte[] cells, int sq) {
        int count = 0;
        for (int d : DIRECTIONS) {
            if (cells[sq + d] == EMPTY) {
                count++;
            }
        }
        return count;
    }

    /** Multi-source BFS in queen moves; squares no queen of this side can reach stay at {@link #FAR}. */
    void queenDistances(byte[] cells, int[] queens, int[] dist) {
        Arrays.fill(dist, FAR);
        int head = 0;
        int tail = 0;
        for (int queen : queens) {
            dist[queen] = 0;
            queue[tail++] = queen;
        }
        while (head < tail) {
            int cell = queue[head++];
            int next = dist[cell] + 1;
            for (int d : DIRECTIONS) {
                for (int sq = cell + d; cells[sq] == EMPTY; sq += d) {
                    int known = dist[sq];
                    if (known > next) {
                        dist[sq] = next;
                        queue[tail++] = sq;
                    } else if (known < next) {
                        // That square's own scan in this direction already covers the rest of the ray.
                        break;
                    }
                }
            }
        }
    }

    /** Multi-source BFS in single king steps. */
    void kingDistances(byte[] cells, int[] queens, int[] dist) {
        Arrays.fill(dist, FAR);
        int head = 0;
        int tail = 0;
        for (int queen : queens) {
            dist[queen] = 0;
            queue[tail++] = queen;
        }
        while (head < tail) {
            int cell = queue[head++];
            int next = dist[cell] + 1;
            for (int d : DIRECTIONS) {
                int sq = cell + d;
                if (cells[sq] == EMPTY && dist[sq] > next) {
                    dist[sq] = next;
                    queue[tail++] = sq;
                }
            }
        }
    }
}
