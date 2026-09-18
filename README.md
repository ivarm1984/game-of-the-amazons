# Game of the Amazons — Bot Arena

Watch bots play Game of the Amazons against each other, or write your own bot
and see how it does against the field.

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
