package io.github.ivarm1984.cli;

import io.github.ivarm1984.bot.Bot;
import io.github.ivarm1984.bot.BotInput;
import io.github.ivarm1984.bot.Deadline;
import io.github.ivarm1984.bot.impl.heuristic.GreedyMobilityBot;
import io.github.ivarm1984.bot.impl.trivial.RandomBot;
import io.github.ivarm1984.engine.AmazonsGame;
import io.github.ivarm1984.engine.GameResult;
import io.github.ivarm1984.engine.GameState;
import io.github.ivarm1984.engine.Move;
import io.github.ivarm1984.engine.PieceColor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Throwaway harness: plays one full game between two bots and prints the board
 * after every ply. Proves the engine + bot interface work together end to end
 * before any REST/WebSocket/frontend layer exists. Not wired into the Spring app.
 *
 * Run with: ./gradlew run (once configured) or directly via an IDE run
 * configuration on this main method.
 */
public final class MatchCli {

    public static void main(String[] args) {
        Map<PieceColor, Bot> bots = Map.of(
                PieceColor.WHITE, new GreedyMobilityBot(),
                PieceColor.BLACK, new RandomBot()
        );
        Duration softBudget = Duration.ofMillis(500);

        GameState state = AmazonsGame.initial();
        System.out.println("Initial position:");
        System.out.println(state.board().render());

        int ply = 0;
        while (true) {
            List<Move> legalMoves = AmazonsGame.legalMoves(state);
            var result = AmazonsGame.result(state, legalMoves);
            if (result.isPresent()) {
                printResult(result.get());
                return;
            }

            Bot toMove = bots.get(state.sideToMove());
            BotInput input = new BotInput(
                    state, state.sideToMove(), legalMoves, state.history(), Deadline.startingNow(softBudget));
            Move chosen = toMove.decideMove(input);

            state = AmazonsGame.apply(state, chosen);
            ply++;
            System.out.println("Ply " + ply + ": " + state.sideToMove().opposite() + " played " + chosen);
            System.out.println(state.board().render());
        }
    }

    private static void printResult(GameResult result) {
        System.out.println("Game over: " + result.status() + " (" + result.reason()
                + ") after " + result.totalPlies() + " plies. Winner: " + result.winner());
    }
}
