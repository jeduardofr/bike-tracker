import type { Pacing } from "../types";

const WALK_KMH = 4.7;

export default function PacingCard({ pacing }: { pacing: Pacing | null }) {
  if (pacing === null) return null;
  const m = pacing.morning;
  const walkingWins = m !== null && m.dryMin >= m.walkMinNow;

  return (
    <div className="card">
      <h2>Morning pacing — arrive dry</h2>
      <div style={{ fontSize: 13, color: "var(--muted)", margin: "4px 0 12px" }}>
        Your comfortable effort, inferred from flat cruising at {pacing.flatKmh.toFixed(1)} km/h, is
        ≈<strong style={{ color: "var(--text)" }}> {pacing.comfortWatts} W</strong> ({pacing.massKg}
        {" kg"} total). &ldquo;Dry pace&rdquo; is 90% of that effort (slow climbs cool you less);
        &ldquo;slightly warm&rdquo; is 120%.
      </div>
      <table className="splits-table">
        <thead>
          <tr>
            <th>Grade</th>
            <th>Dry pace</th>
            <th>Slightly warm</th>
            <th>You currently ride at</th>
            <th>Walking</th>
          </tr>
        </thead>
        <tbody>
          {pacing.targets.map((t) => (
            <tr key={t.gradePct}>
              <td>+{t.gradePct}%</td>
              <td>
                <strong>{t.dryKmh.toFixed(1)} km/h</strong>
              </td>
              <td>{t.pushKmh.toFixed(1)} km/h</td>
              <td>{t.currentKmh === null ? "—" : `${t.currentKmh.toFixed(1)} km/h`}</td>
              <td style={{ color: "var(--muted)" }}>{WALK_KMH} km/h</td>
            </tr>
          ))}
        </tbody>
      </table>
      {m === null ? null : (
        <div style={{ fontSize: 13, marginTop: 12, lineHeight: 1.6 }}>
          On the climbs you walk each morning ({m.walkMinNow.toFixed(1)} min on foot):{" "}
          {walkingWins ? (
            <>
              riding at dry pace would take <strong>{m.dryMin.toFixed(1)} min — walking is
              genuinely your best dry strategy</strong>, keep it. Riding &ldquo;slightly warm&rdquo;
              instead would take {m.pushMin.toFixed(1)} min, trading a bit of sweat for ≈
              {(m.walkMinNow - m.pushMin).toFixed(1)} min.
            </>
          ) : (
            <>
              riding at dry pace would take {m.dryMin.toFixed(1)} min (saves ≈
              {(m.walkMinNow - m.dryMin).toFixed(1)} min), and &ldquo;slightly warm&rdquo;{" "}
              {m.pushMin.toFixed(1)} min.
            </>
          )}
        </div>
      )}
    </div>
  );
}
