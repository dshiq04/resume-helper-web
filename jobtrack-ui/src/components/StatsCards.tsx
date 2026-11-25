import type { Category, Stats } from "../api/types";
import { useI18n } from "../i18n";

export function StatsCards({ stats, category, showStuck = true }: { stats: Stats | null; category: Category | "all"; showStuck?: boolean }) {
  const { t } = useI18n();

  const categoryStats = category === "all" ? null : stats?.byCategoryStatus?.[category];
  const total = category === "all" ? stats?.total ?? 0 : stats?.byCategory?.[category] ?? 0;
  const offer = categoryStats ? categoryStats.offer : stats?.byStatus?.offer ?? 0;
  const interview = categoryStats ? categoryStats.interview : stats?.byStatus?.interview ?? 0;
  const active = categoryStats
    ? (categoryStats.pending ?? 0) +
      (categoryStats.screening ?? 0) +
      (categoryStats.written ?? 0) +
      (categoryStats.interview ?? 0)
    : (stats?.byStatus?.pending ?? 0) +
      (stats?.byStatus?.screening ?? 0) +
      (stats?.byStatus?.written ?? 0) +
      (stats?.byStatus?.interview ?? 0);
  const stuck = stats?.stuckScreening;

  const cards = [
    { label: t("dash.total"), value: total, note: t("stats.allTime"), tone: "ink" },
    { label: t("dash.active"), value: active, note: t("stats.activeNote"), tone: "sage" },
    { label: t("dash.interview"), value: interview, note: t("stats.interviewNote"), tone: "clay" },
    { label: t("dash.offer"), value: offer, note: t("stats.offerNote"), tone: "gold" },
  ];

  return (
    <section className="metric-row">
      {cards.map((card) => (
        <article key={card.label} className={`metric-card tone-${card.tone}`}>
          <span className="metric-label">{card.label}</span>
          <strong className="metric-value">{card.value}</strong>
          <span className="metric-note">{card.note}</span>
        </article>
      ))}
      {showStuck && stuck && stuck.count > 0 && (
        <article className="metric-note-card">
          <span className="metric-label">{t("dash.stuck")}</span>
          <strong>{stuck.count}</strong>
          <span>{t("dash.stuckHint", { avg: stuck.averageDays, max: stuck.maxDays })}</span>
        </article>
      )}
    </section>
  );
}
