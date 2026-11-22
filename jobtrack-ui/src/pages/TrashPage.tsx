import { useCallback, useEffect, useState } from "react";
import { App as AntdApp, Button, Empty, Popconfirm, Spin, Table, type TableColumnsType } from "antd";
import { DeleteOutlined, UndoOutlined } from "@ant-design/icons";
import { trashApi } from "../api/trash";
import type { TrashItem } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { StatusChip } from "../components/StatusChip";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";
import { formatDate, notifyDataChanged } from "../utils/format";

export function TrashPage() {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const [items, setItems] = useState<TrashItem[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await trashApi.list();
      setItems(result.items);
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  const restore = async (item: TrashItem) => {
    try {
      await trashApi.restore(item.id);
      message.success(t("trash.restored"));
      notifyDataChanged();
      await load();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const remove = async (item: TrashItem) => {
    try {
      await trashApi.remove(item.id);
      await load();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const empty = async () => {
    try {
      await trashApi.empty();
      await load();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const columns: TableColumnsType<TrashItem> = [
    {
      title: t("field.company"),
      dataIndex: "companyName",
      render: (value: string, record) => (
        <div className="company-sub-wrap">
          <strong>{value}</strong>
          <span className="muted">{t(`category.${record.category}`)}</span>
        </div>
      ),
    },
    {
      title: t("field.status"),
      dataIndex: "status",
      width: 120,
      render: (value) => <StatusChip status={value} compact />,
    },
    {
      title: t("field.appliedAt"),
      dataIndex: "appliedAt",
      width: 130,
      render: (value: string) => formatDate(value),
    },
    {
      title: t("field.deletedAt"),
      dataIndex: "deletedAt",
      width: 150,
      render: (value: string) => formatDate(value, true),
    },
    {
      title: t("common.actions"),
      width: 160,
      render: (_, record) => (
        <div className="row-actions">
          <Button size="small" icon={<UndoOutlined />} onClick={() => void restore(record)}>
            {t("trash.restore")}
          </Button>
          <Popconfirm
            title={t("trash.deleteConfirm", { name: record.companyName })}
            onConfirm={() => void remove(record)}
            okText={t("common.confirm")}
            cancelText={t("common.cancel")}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        eyebrow={t("nav.trash")}
        title={t("trash.title")}
        subtitle={t("trash.subtitle")}
        actions={
          items.length > 0 ? (
            <Popconfirm
              title={t("trash.clearConfirm")}
              onConfirm={() => void empty()}
              okText={t("common.confirm")}
              cancelText={t("common.cancel")}
            >
              <Button danger icon={<DeleteOutlined />}>
                {t("trash.clear")}
              </Button>
            </Popconfirm>
          ) : undefined
        }
      />

      {loading ? (
        <div className="page-loading">
          <Spin />
        </div>
      ) : items.length === 0 ? (
        <Empty
          image={<div className="empty-mark">空</div>}
          description={
            <div className="empty-copy">
              <strong>{t("trash.empty")}</strong>
              <span>{t("trash.emptyHint")}</span>
            </div>
          }
        />
      ) : (
        <section className="ledger-panel">
          <Table
            rowKey="id"
            size="middle"
            className="ledger-table"
            columns={columns}
            dataSource={items}
            pagination={false}
            scroll={{ x: 780 }}
          />
        </section>
      )}
    </>
  );
}
