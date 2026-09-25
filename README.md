# Game of the Amazons — Bot Arena

Watch bots play Game of the Amazons against each other, or write your own bot
and see how it does against the field.

[Game of the Amazons](https://en.wikipedia.org/wiki/Game_of_the_Amazons) is a
two-player strategy game on a 10×10 board. Each player has four amazons that
move like chess queens; after moving, the amazon shoots an arrow (also like a
queen) that permanently blocks the square it lands on. The board gradually
fills up, and the first player unable to move loses.

## Prerequisites

- JDK 21 (the Gradle wrapper downloads Gradle itself)
- Node.js 20.19+ or 22.12+

## Running locally

Two processes, run from their own directories:

```
cd backend && ./gradlew bootRun     # Spring Boot API on :8080
cd frontend && npm install && npm run dev  # Vue app on :5173
```

Open http://localhost:5173. The frontend dev server proxies `/api` to the
backend, so no CORS setup is needed.

## Writing your own bot

Copy `backend/src/main/java/io/github/ivarm1984/bot/impl/custom/TemplateBot.java`,
give it a new class name and a new `@BotMetadata` id, implement `decideMove`,
and register it as a `@Component`. It will show up in the bot list
automatically on next backend restart — no other wiring required.

Your bot receives the current board, its color, the pre-generated list of
legal moves, and a `Deadline` for its per-move time budget. See
`io.github.ivarm1984.bot.Bot` for the full contract, including how search-based bots
should self-manage their time budget via iterative deepening.

## Project layout

- `backend/` — Java/Spring Boot: game engine, bot interface + bots, match and
  tournament orchestration, REST + SSE API.
- `frontend/` — Vue 3 + TypeScript: bot catalog, match setup, live match
  viewer.

## License

[MIT](LICENSE)
