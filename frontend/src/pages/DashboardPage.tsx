import { useEffect, useMemo, useState } from "react";
import { App as AntdApp, Button, Empty, Input, Popconfirm, Select } from "antd";
import { useNavigate } from "react-router-dom";
import {
  CloudUploadOutlined,
  FilterOutlined,
  PlusOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import { applicationApi } from "../api/applications";
import type { Application, Category, Status } from "../api/types";
import { ApplicationCards } from "../components/ApplicationCards";
import { ApplicationFormDrawer } from "../components/ApplicationFormDrawer";
import { ApplicationTable } from "../components/ApplicationTable";
import { ImportModal } from "../components/ImportModal";
import { PageHeader } from "../components/PageHeader";
import { QuickEntryModal } from "../components/QuickEntryModal";
import { StatsCards } from "../components/StatsCards";
import { useMediaQuery } from "../hooks/useMediaQuery";
import { useI18n } from "../i18n";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import {
  clearSelection,
  fetchApplications,
  fetchYears,
  removeLocal,
  setFilter,
  setPage,
  setPageSize,
  setSelectedKeys,
} from "../store/slices/applicationsSlice";
import { errorMessage } from "../utils/errors";
import { notifyDataChanged } from "../utils/format";

const CATEGORY_TITLE: Record<Category, string> = {
  spring: "category.spring",
  autumn: "category.autumn",
  internship: "category.internship",
  social: "category.social",
};

const CHANNELS = ["官网", "内推", "Boss直聘", "牛客", "猎聘", "实习僧", "拉勾", "其他"];

function seasonCategory(): Category {
  const month = new Date().getMonth() + 1;
  if (month >= 8 && month <= 11) return "autumn";
  if (month >= 2 && month <= 5) return "spring";
  return "internship";
}

export function DashboardPage({ category }: { category: Category | "all" }) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const isCompact = useMediaQuery("(max-width: 1180px)");
  const stats = useAppSelector((state) => state.meta.stats);
  const items = useAppSelector((state) => state.applications.items);
  const total = useAppSelector((state) => state.applications.total);
  const years = useAppSelector((state) => state.applications.years);
  const loading = useAppSelector((state) => state.applications.loading);
  const selectedKeys = useAppSelector((state) => state.applications.selectedKeys);
  const filters = useAppSelector((state) => state.applications.filters);

  const [searchInput, setSearchInput] = useState(filters.search);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Application | null>(null);
  const [quickOpen, setQuickOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  const statusOptions = useMemo(
    () =>
      (["pending", "screening", "written", "interview", "offer", "rejected"] as Status[]).map(
        (value) => ({ value, label: t(`status.${value}`) }),
      ),
    [t],
  );

  const yearList = useMemo(() => {
    const current = new Date().getFullYear();
    return Array.from(new Set([current, ...years])).sort((left, right) => right - left);
  }, [years]);

  useEffect(() => {
    dispatch(fetchYears());
  }, [dispatch]);

  useEffect(() => {
    dispatch(
      fetchApplications({
        search: filters.search,
        category,
        status: filters.status,
        channel: filters.channel,
        year: filters.year,
        page: filters.page,
        pageSize: filters.pageSize,
      }),
    );
  }, [
    category,
    dispatch,
    filters.channel,
    filters.page,
    filters.pageSize,
    filters.search,
    filters.status,
    filters.year,
  ]);

  const refresh = () => {
    dispatch(
      fetchApplications({
        search: filters.search,
        category,
        status: filters.status,
        channel: filters.channel,
        year: filters.year,
        page: filters.page,
        pageSize: filters.pageSize,
      }),
    );
    notifyDataChanged();
  };

  const openCreate = () => {
    setEditing(null);
    setDrawerOpen(true);
  };

  const openEdit = (record: Application) => {
    setEditing(record);
    setDrawerOpen(true);
  };

  const handleDelete = async (record: Application) => {
    try {
      await applicationApi.remove(record.id);
      dispatch(removeLocal([record.id]));
      message.success(t("common.delete"));
      refresh();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const handlePin = async (record: Application) => {
    try {
      await applicationApi.update(record.id, { pinned: !record.pinned });
      refresh();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const handleStatus = async (record: Application, status: Status) => {
    try {
      await applicationApi.update(record.id, { status });
      refresh();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const handleInlineUpdate = async (
    record: Application,
    field: "companyName" | "link",
    value: string,
  ) => {
    try {
      await applicationApi.update(record.id, { [field]: value });
      refresh();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const handleBatchDelete = async () => {
    try {
      await applicationApi.batchDelete(selectedKeys);
      dispatch(clearSelection());
      message.success(t("common.delete"));
      refresh();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const handleBatchStatus = async (status: Status) => {
    try {
      await applicationApi.batchStatus(selectedKeys, status);
      dispatch(clearSelection());
      refresh();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const handleBatchCategory = async (next: Category) => {
    try {
      await applicationApi.batchCategory(selectedKeys, next);
      dispatch(clearSelection());
      refresh();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const emptyNode = (
    <Empty
      image={<div className="empty-mark">投</div>}
      description={
        <div className="empty-copy">
          <strong>{t("dash.emptyTitle")}</strong>
          <span>{t("dash.emptyHint")}</span>
        </div>
      }
    >
      <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
        {t("dash.emptyAction")}
      </Button>
    </Empty>
  );

  return (
    <>
      <PageHeader
        eyebrow={
          category === "all" ? t("dash.eyebrow") : `${t("dash.eyebrow")} / ${t(CATEGORY_TITLE[category])}`
        }
        title={category === "all" ? t("dash.title") : t(CATEGORY_TITLE[category])}
        subtitle={t("dash.subtitle")}
        actions={
          <div className="head-actions">
            <Button icon={<ThunderboltOutlined />} onClick={() => setQuickOpen(true)}>
              {t("dash.quick")}
            </Button>
            <Button icon={<CloudUploadOutlined />} onClick={() => setImportOpen(true)}>
              {t("dash.import")}
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              {t("dash.add")}
            </Button>
          </div>
        }
      />

      <StatsCards stats={stats} category={category} />

      <section className="ledger-panel">
        <div className="ledger-toolbar">
          <Input.Search
            className="toolbar-search"
            allowClear
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            onSearch={(value) => dispatch(setFilter({ search: value.trim() }))}
            placeholder={t("dash.searchPlaceholder")}
          />
          <Select
            className="toolbar-select"
            value={filters.status}
            onChange={(value) => dispatch(setFilter({ status: value }))}
            options={[{ value: "all", label: t("dash.allStatus") }, ...statusOptions]}
          />
          <Select
            className="toolbar-select"
            value={filters.channel}
            onChange={(value) => dispatch(setFilter({ channel: value }))}
            options={[
              { value: "", label: t("dash.allChannel") },
              ...CHANNELS.map((value) => ({ value, label: value })),
            ]}
          />
          <Select
            className="toolbar-select year-select"
            value={filters.year}
            onChange={(value) => dispatch(setFilter({ year: value }))}
            options={[
              { value: "all", label: t("common.allYears") },
              ...yearList.map((year) => ({ value: year, label: `${year}` })),
            ]}
          />
          <Button
            type="text"
            icon={<FilterOutlined />}
            onClick={() => {
              setSearchInput("");
              dispatch(setFilter({ search: "", status: "all", channel: "", year: "all" }));
            }}
          >
            {t("common.reset")}
          </Button>
          <Button type="text" icon={<ReloadOutlined />} onClick={() => refresh()} />
        </div>

        {selectedKeys.length > 0 && (
          <div className="selection-bar">
            <span className="selection-count">
              {t("dash.selected", { count: selectedKeys.length })}
            </span>
            <Select
              size="small"
              placeholder={t("dash.batchStatus")}
              value={undefined}
              onChange={(value: Status) => void handleBatchStatus(value)}
              options={statusOptions}
            />
            <Select
              size="small"
              placeholder={t("dash.batchCategory")}
              value={undefined}
              onChange={(value: Category) => void handleBatchCategory(value)}
              options={(["spring", "autumn", "internship", "social"] as Category[]).map((value) => ({
                value,
                label: t(`category.${value}`),
              }))}
            />
            <Popconfirm
              title={t("common.confirmDelete")}
              description={t("common.irreversible")}
              onConfirm={() => void handleBatchDelete()}
              okText={t("common.confirm")}
              cancelText={t("common.cancel")}
            >
              <Button size="small" danger>
                {t("dash.batchDelete")}
              </Button>
            </Popconfirm>
            <Button size="small" type="text" onClick={() => dispatch(clearSelection())}>
              {t("dash.clearSelection")}
            </Button>
          </div>
        )}

        {isCompact ? (
          <ApplicationCards
            items={items}
            loading={loading}
            selectedKeys={selectedKeys}
            onToggleSelect={(id) =>
              dispatch(
                setSelectedKeys(
                  selectedKeys.includes(id)
                    ? selectedKeys.filter((key) => key !== id)
                    : [...selectedKeys, id],
                ),
              )
            }
            onView={(record) => navigate(`/applications/${record.id}`)}
            onEdit={openEdit}
            onDelete={(record) => void handleDelete(record)}
            onPin={(record) => void handlePin(record)}
            onStatusChange={(record, status) => void handleStatus(record, status)}
            statusOptions={statusOptions}
            emptyText={emptyNode}
          />
        ) : (
          <ApplicationTable
            items={items}
            total={total}
            page={filters.page}
            pageSize={filters.pageSize}
            loading={loading}
            selectedKeys={selectedKeys}
            onSelectionChange={(keys) => dispatch(setSelectedKeys(keys))}
            onPageChange={(page) => dispatch(setPage(page))}
            onPageSizeChange={(size) => dispatch(setPageSize(size))}
            onView={(record) => navigate(`/applications/${record.id}`)}
            onEdit={openEdit}
            onDelete={(record) => void handleDelete(record)}
            onPin={(record) => void handlePin(record)}
            onStatusChange={(record, status) => void handleStatus(record, status)}
            onInlineUpdate={(record, field, value) => void handleInlineUpdate(record, field, value)}
            statusOptions={statusOptions}
            emptyText={emptyNode}
          />
        )}
      </section>

      <ApplicationFormDrawer
        open={drawerOpen}
        editing={editing}
        defaultCategory={category === "all" ? seasonCategory() : category}
        onClose={() => setDrawerOpen(false)}
        onSaved={refresh}
      />
      <ImportModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        onImported={refresh}
      />
      <QuickEntryModal
        open={quickOpen}
        defaultCategory={category === "all" ? seasonCategory() : category}
        onClose={() => setQuickOpen(false)}
        onSaved={refresh}
      />
    </>
  );
}
