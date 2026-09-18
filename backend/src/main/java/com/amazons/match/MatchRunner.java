package com.amazons.match;

import com.amazons.bot.Bot;
import com.amazons.bot.BotContext;
import com.amazons.bot.BotInput;
import com.amazons.bot.BotRegistry;
import com.amazons.bot.Deadline;
import com.amazons.bot.RegisteredBot;
import com.amazons.engine.AmazonsGame;
import com.amazons.engine.GameResult;
import com.amazons.engine.GameState;
import com.amazons.engine.GameStatus;
import com.amazons.engine.Move;
import com.amazons.engine.PieceColor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs one game turn by turn, enforcing the bot time-budget contract: each bot
 * is expected to self-manage its {@link Deadline}, and the runner enforces a
 * hard timeout as a backstop. A bot that times out, throws, or returns a move
 * outside {@code legalMoves} forfeits the game immediately - collapsed into an
 * ordinary {@link GameResult} so callers never need to special-case a forfeit.
 */
@Component
public class MatchRunner {

    private static final int MAX_PLIES = 2000;
    static final String REASON_TIMEOUT = "TIMEOUT";
    static final String REASON_ILLEGAL_MOVE = "ILLEGAL_MOVE_RETURNED";
    static final String REASON_BOT_THREW_PREFIX = "BOT_THREW: ";
    static final String REASON_MAX_PLIES_EXCEEDED = "MAX_PLIES_EXCEEDED";

    private final BotRegistry botRegistry;
    private final ExecutorService botExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public MatchRunner(BotRegistry botRegistry) {
        this.botRegistry = botRegistry;
    }

    public MatchResult runSync(MatchConfig config) {
        return runSync(config, event -> {
        });
    }

    public MatchResult runSync(MatchConfig config, MatchEventListener listener) {
        RegisteredBot white = requireBot(config.whiteBotId());
        RegisteredBot black = requireBot(config.blackBotId());
        Map<PieceColor, RegisteredBot> bots = Map.of(PieceColor.WHITE, white, PieceColor.BLACK, black);

        white.bot().onGameStart(new BotContext(PieceColor.WHITE, black.id()));
        black.bot().onGameStart(new BotContext(PieceColor.BLACK, white.id()));

        GameState state = AmazonsGame.initial();

        for (int ply = 0; ply < MAX_PLIES; ply++) {
            List<Move> legalMoves = AmazonsGame.legalMoves(state);
            Optional<GameResult> terminal = AmazonsGame.result(state, legalMoves);
            if (terminal.isPresent()) {
                return finish(config, state, bots, terminal.get(), listener);
            }

            PieceColor mover = state.sideToMove();
            RegisteredBot activeBot = bots.get(mover);

            MoveOutcome outcome = decideWithTimeout(activeBot, state, legalMoves, config.softMoveBudget());
            if (outcome.forfeitReason() != null) {
                GameResult result = GameResult.win(mover.opposite(), outcome.forfeitReason(), state.moveNumber());
                return finish(config, state, bots, result, listener);
            }

            state = AmazonsGame.apply(state, outcome.move());
            listener.onEvent(new MoveEvent(ply + 1, mover, outcome.move(), state.board(), state.sideToMove()));
        }

        GameResult aborted = new GameResult(GameStatus.DRAW, null, REASON_MAX_PLIES_EXCEEDED, state.moveNumber());
        return finish(config, state, bots, aborted, listener);
    }

    private MatchResult finish(MatchConfig config, GameState state, Map<PieceColor, RegisteredBot> bots,
                                GameResult result, MatchEventListener listener) {
        for (RegisteredBot registered : bots.values()) {
            try {
                registered.bot().onGameEnd(result);
            } catch (RuntimeException ignored) {
                // a misbehaving onGameEnd hook must not crash match bookkeeping
            }
        }
        listener.onEvent(new MatchFinishedEvent(result, state.board()));
        return new MatchResult(config.matchId(), result, state.history());
    }

    private MoveOutcome decideWithTimeout(RegisteredBot registeredBot, GameState state, List<Move> legalMoves,
                                           Duration softBudget) {
        Bot bot = registeredBot.bot();
        BotInput input = new BotInput(
                state, state.sideToMove(), legalMoves, state.history(), Deadline.startingNow(softBudget));

        long graceMillis = Math.max(500, softBudget.toMillis() / 5);
        long hardTimeoutMillis = softBudget.toMillis() + graceMillis;

        Callable<Move> task = () -> bot.decideMove(input);
        Future<Move> future = botExecutor.submit(task);
        try {
            Move move = future.get(hardTimeoutMillis, TimeUnit.MILLISECONDS);
            if (move == null || !legalMoves.contains(move)) {
                return MoveOutcome.forfeit(REASON_ILLEGAL_MOVE);
            }
            return MoveOutcome.ok(move);
        } catch (TimeoutException e) {
            future.cancel(true);
            return MoveOutcome.forfeit(REASON_TIMEOUT);
        } catch (Exception e) {
            future.cancel(true);
            return MoveOutcome.forfeit(REASON_BOT_THREW_PREFIX + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }

    private RegisteredBot requireBot(String id) {
        return botRegistry.find(id).orElseThrow(() -> new IllegalArgumentException("unknown bot id: " + id));
    }

    private record MoveOutcome(Move move, String forfeitReason) {
        static MoveOutcome ok(Move move) {
            return new MoveOutcome(move, null);
        }

        static MoveOutcome forfeit(String reason) {
            return new MoveOutcome(null, reason);
        }
    }
}
