import { useCallback, useEffect, useState } from "react";
import { App as AntdApp, Button, DatePicker, Empty, Form, Modal, Popconfirm, Select, Spin } from "antd";
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  LinkOutlined,
} from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import { useNavigate, useParams } from "react-router-dom";
import { applicationApi } from "../api/applications";
import { adminApi } from "../api/admin";
import type { Application, HistoryResponse, Status } from "../api/types";
import { ApplicationFormDrawer } from "../components/ApplicationFormDrawer";
import { PageHeader } from "../components/PageHeader";
import { PriorityChip, StatusChip } from "../components/StatusChip";
import { StatusTimeline } from "../components/StatusTimeline";
import { useI18n } from "../i18n";
import { errorMessage } from "../utils/errors";
import { formatDate, notifyDataChanged, openExternalLink } from "../utils/format";

export function ApplicationDetailPage({ adminUserId }: { adminUserId?: number } = {}) {
  const { id } = useParams();
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const navigate = useNavigate();
  const [record, setRecord] = useState<Application | null>(null);
  const [history, setHistory] = useState<HistoryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [editOpen, setEditOpen] = useState(false);
  const [fillOpen, setFillOpen] = useState(false);
  const [fillForm] = Form.useForm<{ status: Status; changedAt: Dayjs }>();

  const backTarget = adminUserId
    ? `/admin/users/${adminUserId}?tab=applications`
    : "/";

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const applicationId = Number(id);
      const [application, timeline] = adminUserId
        ? await Promise.all([
            adminApi.application(adminUserId, applicationId),
            adminApi.applicationHistory(adminUserId, applicationId),
          ])
        : await Promise.all([
            applicationApi.get(applicationId),
            applicationApi.history(applicationId),
          ]);
      setRecord(application);
      setHistory(timeline);
    } catch (error) {
      message.error(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [adminUserId, id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleDelete = async () => {
    if (!record) return;
    try {
      if (adminUserId) {
        await adminApi.deleteApplication(adminUserId, record.id);
      } else {
        await applicationApi.remove(record.id);
        notifyDataChanged();
      }
      message.success(t("common.delete"));
      navigate(backTarget);
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  const submitFill = async (values: { status: Status; changedAt: Dayjs }) => {
    if (!record) return;
    try {
      if (adminUserId) {
        await adminApi.appendApplicationHistory(adminUserId, record.id, {
          status: values.status,
          changedAt: values.changedAt.toISOString(),
        });
      } else {
        await applicationApi.appendHistory(
          record.id,
          values.status,
          values.changedAt.toISOString(),
        );
        notifyDataChanged();
      }
      setFillOpen(false);
      fillForm.resetFields();
      message.success(t("detail.fillSuccess"));
      await load();
    } catch (error) {
      message.error(errorMessage(error));
    }
  };

  if (loading) {
    return (
      <div className="page-loading">
        <Spin size="large" />
      </div>
    );
  }

  if (!record) {
    return (
      <Empty description={t("error.notFound")}>
        <Button onClick={() => navigate(backTarget)}>{t("detail.back")}</Button>
      </Empty>
    );
  }

  const facts = [
    { label: t("field.company"), value: record.companyName },
    { label: t("field.position"), value: record.position || "—" },
    { label: t("field.year"), value: String(record.year) },
    { label: t("field.category"), value: t(`category.${record.category}`) },
    { label: t("field.channel"), value: record.channel || "—" },
    { label: t("field.city"), value: record.city || "—" },
    { label: t("field.salary"), value: record.salary || "—" },
    { label: t("field.priority"), value: <PriorityChip priority={record.priority} /> },
    { label: t("field.workMode"), value: record.workMode ? t(`workMode.${record.workMode}`) : "—" },
    { label: t("field.interviewRound"), value: record.interviewRound ? String(record.interviewRound) : "—" },
    { label: t("field.contact"), value: record.contact || "—" },
    { label: t("field.contactInfo"), value: record.contactInfo || "—" },
    { label: t("field.appliedAt"), value: formatDate(record.appliedAt, true) },
    { label: t("field.nextAction"), value: record.nextActionAt ? formatDate(record.nextActionAt, true) : "—" },
  ];

  return (
    <>
      <PageHeader
        eyebrow={adminUserId ? `${t("admin.group")} · ${t("admin.users")}` : t("detail.title")}
        title={record.companyName}
        subtitle={
          adminUserId
            ? `${record.position || "—"} · ${record.year} · ${t("admin.viewingUser", { id: adminUserId })}`
            : `${record.position || "—"} · ${record.year}`
        }
        actions={
          <div className="head-actions">
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(backTarget)}>
              {t("detail.back")}
            </Button>
            <Button icon={<LinkOutlined />} onClick={() => openExternalLink(record.link)}>
              {t("detail.open")}
            </Button>
            <Button icon={<EditOutlined />} onClick={() => setEditOpen(true)}>
              {t("common.edit")}
            </Button>
            <Popconfirm
              title={t("dash.deleteConfirm", { name: record.companyName })}
              onConfirm={() => void handleDelete()}
              okText={t("common.confirm")}
              cancelText={t("common.cancel")}
            >
              <Button danger icon={<DeleteOutlined />}>
                {t("common.delete")}
              </Button>
            </Popconfirm>
          </div>
        }
      />

      <div className="detail-status-row">
        <StatusChip status={record.status} />
        <span className="muted">
          {t("detail.daysInStatus", { days: record.statusDays ?? 0 })}
        </span>
        {record.screeningDays !== undefined && record.status === "screening" && (
          <span className="muted">{t("detail.screeningDays", { days: record.screeningDays })}</span>
        )}
      </div>

      <div className="detail-grid">
        <section className="fact-panel">
          <div className="panel-title-row">
            <h2>{t("form.basic")}</h2>
          </div>
          <dl className="fact-list">
            {facts.map((fact) => (
              <div key={fact.label} className="fact-row">
                <dt>{fact.label}</dt>
                <dd>{fact.value}</dd>
              </div>
            ))}
          </dl>
          <div className="fact-link">
            <span>{t("field.link")}</span>
            <button type="button" className="inline-text" onClick={() => openExternalLink(record.link)}>
              {record.link}
            </button>
          </div>
          {record.notes && (
            <div className="fact-notes">
              <span>{t("field.notes")}</span>
              <p>{record.notes}</p>
            </div>
          )}
        </section>

        <StatusTimeline
          items={history?.items ?? []}
          current={record.status}
          missingStatuses={history?.missingStatuses ?? []}
          onFill={() => {
            fillForm.setFieldsValue({
              status: history?.missingStatuses?.[0],
              changedAt: dayjs(),
            });
            setFillOpen(true);
          }}
        />
      </div>

      <ApplicationFormDrawer
        open={editOpen}
        editing={record}
        userId={adminUserId}
        onClose={() => setEditOpen(false)}
        onSaved={() => {
          void load();
          if (!adminUserId) {
            notifyDataChanged();
          }
        }}
      />

      <Modal
        open={fillOpen}
        title={t("detail.fillTitle")}
        onCancel={() => setFillOpen(false)}
        onOk={() => fillForm.submit()}
        okText={t("common.save")}
        cancelText={t("common.cancel")}
      >
        <Form form={fillForm} layout="vertical" onFinish={submitFill}>
          <Form.Item name="status" label={t("detail.fillStatus")} rules={[{ required: true }]}>
            <Select
              options={(history?.missingStatuses ?? []).map((status) => ({
                value: status,
                label: t(`status.${status}`),
              }))}
            />
          </Form.Item>
          <Form.Item name="changedAt" label={t("detail.fillDate")} rules={[{ required: true }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
