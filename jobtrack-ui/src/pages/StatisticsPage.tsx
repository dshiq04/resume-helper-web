import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { App as AntdApp, Button, DatePicker, Select, Skeleton } from "antd";
import { DownloadOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import ReactECharts from "echarts-for-react";
import html2canvas from "html2canvas";
import { applicationApi } from "../api/applications";
import { statsApi } from "../api/stats";
import type { DailyStat, FunnelStats, Stats, Status } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { StatsCards } from "../components/StatsCards";
import { useI18n } from "../i18n";
import { useAppSelector } from "../store/hooks";
import { errorMessage } from "../utils/errors";

const STATUS_COLORS: Record<Status, string> = {
  pending: "#9aa39b",
  screening: "#c8a24a",
  written: "#4f7fa8",
  interview: "#6b7fc4",
  offer: "#2f7d52",
  rejected: "#b23b32",
};

function quarterStart() {
  const now = dayjs();
  return now.month(Math.floor(now.month() / 3) * 3).startOf("month");
}

export function StatisticsPage() {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const themeMode = useAppSelector((state) => state.ui.themeMode);
  const [years, setYears] = useState<number[]>([]);
  const [year, setYear] = useState<number | "all">("all");
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [stats, setStats] = useState<Stats | null>(null);
  const [funnel, setFunnel] = useState<FunnelStats | null>(null);
  const [daily, setDaily] = useState<DailyStat[]>([]);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const captureRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    void applicationApi
      .years()
      .then((result) => setYears(result.items))
      .catch(() => setYears([]));
  }, []);

  const query = useMemo(() => {
    if (range) {
      return {
        // 统一发送本地墙上时间，与仪表盘/列表日期范围语义一致。
        from: range[0].startOf("day").format("YYYY-MM-DD HH:mm:ss"),
        to: range[1].endOf("day").format("YYYY-MM-DD HH:mm:ss"),
      };
    }
    if (year !== "all") {
      return { year };
    }
    return undefined;
  }, [range, year]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [statsResult, dailyResult, funnelResult] = await Promise.all([
        statsApi.overview(query),
        statsApi.daily(query),
        statsApi.funnel(query),
      ]);
      setStats(statsResult);
      setDaily(dailyResult.daily);
      setFunnel(funnelResult);
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [message, query]);

  useEffect(() => {
    void load();
  }, [load]);

  const dark = themeMode === "dark";
  const axisColor = dark ? "#8f9a93" : "#7a817a";
  const splitColor = dark ? "#242d29" : "#ece5d8";
  const palette = ["#4f46e5", "#8b5cf6", "#0ea5e9", "#f59e0b", "#10b981", "#ef4444"];

  const yearList = useMemo(() => {
    const current = new Date().getFullYear();
    return Array.from(new Set([current, ...years, ...Object.keys(stats?.byYear ?? {}).map(Number)]))
      .filter((item) => Number.isFinite(item))
      .sort((left, right) => right - left);
  }, [stats?.byYear, years]);

  const calendarYear = year === "all" ? new Date().getFullYear() : year;

  const trendOption = useMemo(() => {
    const dates = Array.from(new Set(daily.map((item) => item.date))).sort();
    const categories = ["spring", "autumn", "internship", "social"];
    const labels: Record<string, string> = {
      spring: t("category.spring"),
      autumn: t("category.autumn"),
      internship: t("category.internship"),
      social: t("category.social"),
    };
    const series = categories.map((category, index) => ({
      name: labels[category],
      type: "line",
      smooth: true,
      symbol: "none",
      stack: "total",
      areaStyle: { opacity: 0.16 },
      lineStyle: { width: 1.6 },
      itemStyle: { color: palette[index] },
      data: dates.map(
        (date) =>
          daily.find((item) => item.date === date && item.category === category)?.count ?? 0,
      ),
    }));
    return {
      tooltip: { trigger: "axis" },
      legend: { bottom: 0, textStyle: { color: axisColor }, itemWidth: 12, itemHeight: 8 },
      grid: { left: 40, right: 20, top: 20, bottom: 42 },
      xAxis: {
        type: "category",
        data: dates,
        boundaryGap: false,
        axisLine: { lineStyle: { color: splitColor } },
        axisLabel: { color: axisColor, fontSize: 11 },
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: axisColor, fontSize: 11 },
        splitLine: { lineStyle: { color: splitColor } },
      },
      series,
    };
  }, [axisColor, daily, palette, splitColor, t]);

  const statusOption = useMemo(() => {
    const statuses: Status[] = ["screening", "written", "interview", "pending", "offer", "rejected"];
    return {
      tooltip: { trigger: "item" },
      legend: {
        bottom: 0,
        textStyle: { color: axisColor, fontSize: 11 },
        itemWidth: 10,
        itemHeight: 8,
      },
      series: [
        {
          type: "pie",
          radius: ["52%", "74%"],
          center: ["50%", "44%"],
          itemStyle: { borderColor: dark ? "#19201d" : "#fffdf8", borderWidth: 2 },
          label: { show: false },
          data: statuses.map((status) => ({
            name: t(`status.${status}`),
            value: stats?.byStatus?.[status] ?? 0,
            itemStyle: { color: STATUS_COLORS[status] },
          })),
        },
      ],
    };
  }, [axisColor, dark, stats?.byStatus, t]);

  const funnelOption = useMemo(() => {
    const stages = funnel?.stages ?? [];
    return {
      tooltip: {
        trigger: "axis",
        formatter: (params: any) => {
          const item = params?.[0];
          return item ? `${item.name}<br/>${item.value} · ${item.data.conversion}%` : "";
        },
      },
      grid: { left: 76, right: 40, top: 16, bottom: 20 },
      xAxis: {
        type: "value",
        axisLabel: { color: axisColor, fontSize: 11 },
        splitLine: { lineStyle: { color: splitColor } },
      },
      yAxis: {
        type: "category",
        inverse: true,
        data: stages.map((stage) => (stage.key === "applied" ? t("dash.total") : t(`status.${stage.key}`))),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: axisColor, fontSize: 11 },
      },
      series: [
        {
          type: "bar",
          barWidth: 14,
          itemStyle: { color: "#4f46e5", borderRadius: [0, 3, 3, 0] },
          label: {
            show: true,
            position: "right",
            color: axisColor,
            fontSize: 11,
            formatter: (params: any) => `${params.value}`,
          },
          data: stages.map((stage) => ({ value: stage.count, conversion: stage.conversion })),
        },
      ],
    };
  }, [axisColor, funnel?.stages, splitColor, t]);

  const companyOption = useMemo(() => {
    const companies = (funnel?.companies ?? []).slice(0, 8).reverse();
    return {
      tooltip: { trigger: "axis" },
      legend: { bottom: 0, textStyle: { color: axisColor }, itemWidth: 10, itemHeight: 8 },
      grid: { left: 90, right: 20, top: 16, bottom: 42 },
      xAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: axisColor, fontSize: 11 },
        splitLine: { lineStyle: { color: splitColor } },
      },
      yAxis: {
        type: "category",
        data: companies.map((item) => item.company),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: axisColor, fontSize: 11, width: 80, overflow: "truncate" },
      },
      series: [
        {
          name: t("status.interview"),
          type: "bar",
          barWidth: 8,
          itemStyle: { color: "#f59e0b", borderRadius: [0, 2, 2, 0] },
          data: companies.map((item) => item.interviews),
        },
        {
          name: t("status.offer"),
          type: "bar",
          barWidth: 8,
          itemStyle: { color: "#4f46e5", borderRadius: [0, 2, 2, 0] },
          data: companies.map((item) => item.offers),
        },
      ],
    };
  }, [axisColor, funnel?.companies, splitColor, t]);

  const calendarOption = useMemo(() => {
    const totals = new Map<string, number>();
    for (const item of daily) {
      if (!item.date.startsWith(String(calendarYear))) continue;
      totals.set(item.date, (totals.get(item.date) ?? 0) + item.count);
    }
    const data = Array.from(totals.entries());
    const max = Math.max(1, ...data.map(([, count]) => count));
    return {
      tooltip: {
        formatter: (params: any) => `${params.value?.[0]} · ${params.value?.[1]}`,
      },
      visualMap: {
        min: 0,
        max,
        show: false,
        inRange: { color: ["#ece6d8", "#11604f"] },
      },
      calendar: {
        top: 30,
        left: 44,
        right: 16,
        bottom: 10,
        cellSize: ["auto", 14],
        range: String(calendarYear),
        splitLine: {
          lineStyle: { color: dark ? "#3b404d" : "#d5d8e4", width: 1 },
        },
        itemStyle: {
          color: "transparent",
          borderWidth: 1,
          borderColor: dark ? "#333845" : "#dcdfe9",
        },
        dayLabel: { color: axisColor, fontSize: 10 },
        monthLabel: { color: axisColor, fontSize: 11 },
        yearLabel: { show: false },
      },
      series: [
        {
          type: "heatmap",
          coordinateSystem: "calendar",
          data,
        },
      ],
    };
  }, [axisColor, calendarYear, daily, dark, splitColor]);

  const exportImage = async () => {
    if (!captureRef.current || exporting) return;
    setExporting(true);
    try {
      const canvas = await html2canvas(captureRef.current, {
        backgroundColor: dark ? "#111614" : "#f4f1e9",
        scale: Math.max(2, window.devicePixelRatio || 1),
        useCORS: true,
        logging: false,
      });
      const link = document.createElement("a");
      link.download = `jobtrack-stats-${dayjs().format("YYYYMMDD-HHmm")}.png`;
      link.href = canvas.toDataURL("image/png");
      link.click();
    } catch {
      message.error(t("stats.export"));
    } finally {
      setExporting(false);
    }
  };

  return (
    <>
      <PageHeader
        eyebrow={t("nav.statistics")}
        title={t("stats.title")}
        subtitle={t("stats.subtitle")}
        actions={
          <div className="head-actions">
            <Select
              className="toolbar-select"
              value={year}
              onChange={(value) => {
                setYear(value);
                setRange(null);
              }}
              options={[
                { value: "all", label: t("common.allYears") },
                ...yearList.map((value) => ({ value, label: `${value}` })),
              ]}
            />
            <DatePicker.RangePicker
              value={range}
              onChange={(value) => {
                if (value?.[0] && value?.[1]) {
                  setRange([value[0], value[1]]);
                  setYear("all");
                } else {
                  setRange(null);
                }
              }}
              presets={[
                { label: t("stats.thisMonth"), value: [dayjs().startOf("month"), dayjs().endOf("month")] },
                { label: t("stats.thisQuarter"), value: [quarterStart(), dayjs().endOf("day")] },
                { label: t("stats.thisYear"), value: [dayjs().startOf("year"), dayjs().endOf("year")] },
              ]}
            />
            <Button icon={<DownloadOutlined />} loading={exporting} onClick={() => void exportImage()}>
              {t("stats.export")}
            </Button>
          </div>
        }
      />

      <div ref={captureRef} className="stats-capture">
        <StatsCards stats={stats} category="all" showStuck={false} />

        {stats?.stuckScreening && stats.stuckScreening.count > 0 && (
          <div className="stuck-banner">
            <strong>{t("stats.stuckTitle")}</strong>
            <span>
              {t("stats.stuckBody", {
                count: stats.stuckScreening.count,
                avg: stats.stuckScreening.averageDays,
                max: stats.stuckScreening.maxDays,
              })}
            </span>
          </div>
        )}

        {loading ? (
          <div className="chart-grid">
            <Skeleton active className="chart-card span-2" />
            <Skeleton active className="chart-card" />
            <Skeleton active className="chart-card" />
          </div>
        ) : (
          <div className="chart-grid">
            <section className="chart-card span-2">
              <div className="panel-title-row">
                <h2>{t("stats.trend")}</h2>
                <span className="panel-hint">{t("stats.trendHint")}</span>
              </div>
              <ReactECharts option={trendOption} style={{ height: 300 }} notMerge />
            </section>

            <section className="chart-card">
              <div className="panel-title-row">
                <h2>{t("stats.statusDist")}</h2>
              </div>
              <ReactECharts option={statusOption} style={{ height: 300 }} notMerge />
            </section>

            <section className="chart-card">
              <div className="panel-title-row">
                <h2>{t("stats.funnel")}</h2>
                <span className="panel-hint">
                  {t("stats.avgInterview", { days: funnel?.averageDaysToInterview ?? 0 })}
                </span>
              </div>
              <ReactECharts option={funnelOption} style={{ height: 300 }} notMerge />
            </section>

            <section className="chart-card span-2">
              <div className="panel-title-row">
                <h2>{t("stats.company")}</h2>
                <span className="panel-hint">
                  {t("stats.avgOffer", { days: funnel?.averageInterviewToOffer ?? 0 })}
                </span>
              </div>
              <ReactECharts option={companyOption} style={{ height: 300 }} notMerge />
            </section>

            <section className="chart-card span-2">
              <div className="panel-title-row">
                <h2>{t("stats.calendar")}</h2>
                <span className="panel-hint">{calendarYear}</span>
              </div>
              <ReactECharts option={calendarOption} style={{ height: 240 }} notMerge />
            </section>
          </div>
        )}
      </div>
    </>
  );
}
