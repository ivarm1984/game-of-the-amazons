// Fixed hue order, never cycled per-series - see dataviz skill (color-formula.md).
// Cycles only past 8 concurrent series, which round-robin tournaments rarely hit.
export const BOT_COLOR_PALETTE = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948']

/** Maps each bot id to a color, in the given id order, so any view sharing that order (e.g. tournamentStore.eloHistory's keys) shows the same bot in the same color as the Elo chart. */
export function colorMapFor(botIds: string[]): Record<string, string> {
  const map: Record<string, string> = {}
  botIds.forEach((id, i) => {
    map[id] = BOT_COLOR_PALETTE[i % BOT_COLOR_PALETTE.length]
  })
  return map
}
