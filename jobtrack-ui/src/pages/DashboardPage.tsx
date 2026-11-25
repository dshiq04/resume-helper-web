import { useEffect, useMemo, useState } from "react";
import { App as AntdApp, Button, DatePicker, Empty, Input, Pagination, Popconfirm, Select } from "antd";
import dayjs from "dayjs";
import { useNavigate } from "react-router-dom";
import {
  CloudUploadOutlined,
  DownloadOutlined,
  FilterOutlined,
  PlusOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import { applicationApi } from "../api/applications";
import type { Application, ApplicationUpdate, Category, Status } from "../api/types";
import { ApplicationCards } from "../components/ApplicationCards";
import { ApplicationFormDrawer } from "../components/ApplicationFormDrawer";
import { ApplicationTable } from "../components/ApplicationTable";
import { ExportModal } from "../components/ExportModal";
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

/**
 * 列表内联修改只改动单个字段，但后端校验要求企业名称、状态等必填项齐全。
 * 因此把当前行数据一并带上，避免出现“企业名称/状态不能为空”的误报。
 */
function buildUpdatePayload(
  record: Application,
  patch: ApplicationUpdate,
): ApplicationUpdate {
  return {
    category: record.category,
    companyName: record.companyName,
    link: record.link,
    year: record.year,
    position: record.position,
    status: record.status,
    channel: record.channel,
    city: record.city,
    salary: record.salary,
    contact: record.contact,
    contactInfo: record.contactInfo,
    workMode: record.workMode,
    priority: record.priority,
    interviewRound: record.interviewRound,
    nextActionAt: record.nextActionAt,
    notes: record.notes,
    pinned: record.pinned,
    ...patch,
  };
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
  const loading = useAppSelector((state) => state.applications.loading);
  const selectedKeys = useAppSelector((state) => state.applications.selectedKeys);
  const filters = useAppSelector((state) => state.applications.filters);

  const [searchInput, setSearchInput] = useState(filters.search);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Application | null>(null);
  const [quickOpen, setQuickOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);

  const statusOptions = useMemo(
    () =>
      (["pending", "screening", "written", "interview", "offer", "rejected"] as Status[]).map(
        (value) => ({ value, label: t(`status.${value}`) }),
      ),
    [t],
  );

  // 切换投递分类后，上一分类的选中项已不可见，必须清空，避免批量操作误伤。
  useEffect(() => {
    dispatch(clearSelection());
  }, [category, dispatch]);

  useEffect(() => {
    dispatch(
      fetchApplications({
        search: filters.search,
        category,
        status: filters.status,
        channel: filters.channel,
        from: filters.from,
        to: filters.to,
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
    filters.from,
    filters.to,
  ]);

  const refresh = () => {
    dispatch(
      fetchApplications({
        search: filters.search,
        category,
        status: filters.status,
        channel: filters.channel,
        from: filters.from,
        to: filters.to,
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
      await applicationApi.update(
        record.id,
        buildUpdatePayload(record, { pinned: !record.pinned }),
      );
      refresh();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const handleStatus = async (record: Application, status: Status) => {
    try {
      await applicationApi.update(record.id, buildUpdatePayload(record, { status }));
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
      await applicationApi.update(record.id, buildUpdatePayload(record, { [field]: value }));
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
            <Button icon={<DownloadOutlined />} onClick={() => setExportOpen(true)}>
              {t("dash.export")}
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
          <DatePicker.RangePicker
            className="toolbar-range"
            allowClear
            value={
              filters.from && filters.to
                ? [dayjs(filters.from), dayjs(filters.to)]
                : null
            }
            onChange={(values) => {
              if (values && values[0] && values[1]) {
                dispatch(
                  setFilter({
                    from: values[0].startOf("day").format("YYYY-MM-DD HH:mm:ss"),
                    to: values[1].endOf("day").format("YYYY-MM-DD HH:mm:ss"),
                  }),
                );
              } else {
                dispatch(setFilter({ from: "", to: "" }));
              }
            }}
            placeholder={[t("dash.startDate"), t("dash.endDate")]}
            format="YYYY-MM-DD"
          />
          <Button
            type="text"
            icon={<FilterOutlined />}
            onClick={() => {
              setSearchInput("");
              dispatch(setFilter({ search: "", status: "all", channel: "", from: "", to: "" }));
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
          <>
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
            {total > 0 && (
              <div style={{ marginTop: 16, display: "flex", justifyContent: "center" }}>
                <Pagination
                  current={filters.page}
                  pageSize={filters.pageSize}
                  total={total}
                  size="small"
                  showSizeChanger
                  pageSizeOptions={[10, 20, 50, 100]}
                  onChange={(nextPage, nextSize) => {
                    if (nextSize !== filters.pageSize) {
                      dispatch(setPageSize(nextSize));
                    } else {
                      dispatch(setPage(nextPage));
                    }
                  }}
                />
              </div>
            )}
          </>
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
      <ExportModal open={exportOpen} onClose={() => setExportOpen(false)} />
      <QuickEntryModal
        open={quickOpen}
        defaultCategory={category === "all" ? seasonCategory() : category}
        onClose={() => setQuickOpen(false)}
        onSaved={refresh}
      />
    </>
  );
}
