import { useEffect, useRef, useState } from "react";
import {
  Alert,
  App as AntdApp,
  Button,
  Descriptions,
  Space,
  Spin,
  Tag,
} from "antd";
import { DownloadOutlined, FilePdfOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { adminApi } from "../../api/admin";
import type { UserAgreementRecord } from "../../api/types";
import { USER_AGREEMENTS } from "../../data/userAgreement";
import { useI18n } from "../../i18n";
import {
  agreementLanguageName,
  downloadAgreementHtml,
  printAgreementPdf,
  resolveAgreementLanguage,
} from "../../utils/agreementDocument";
import { errorMessage } from "../../utils/errors";

export function AdminUserAgreementPanel({ userId }: { userId: number }) {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const bodyRef = useRef<HTMLDivElement>(null);
  const [record, setRecord] = useState<UserAgreementRecord | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    void adminApi
      .userAgreement(userId)
      .then(setRecord)
      .catch((error) => message.error(errorMessage(error)))
      .finally(() => setLoading(false));
  }, [userId, message]);

  if (loading) {
    return <div className="admin-drawer-loading"><Spin /></div>;
  }

  if (!record) {
    return (
      <section className="admin-detail-card">
        <Alert type="error" showIcon message={t("admin.agreementNotFound")} />
      </section>
    );
  }

  const language = resolveAgreementLanguage(record.language);
  const exportData = () => ({
    language,
    bodyHtml: bodyRef.current?.innerHTML ?? "",
    signerName: record.signerName,
    username: record.username,
    userId: record.userId,
    signedAt: record.acceptedAt,
    location: record.location,
    timezone: record.timezone,
  });

  const exportHtml = () => {
    if (!record.signed) return;
    downloadAgreementHtml(exportData());
    message.success(t("admin.agreementExported"));
  };

  const exportPdf = () => {
    if (!record.signed) return;
    if (!printAgreementPdf(exportData())) {
      message.error(t("admin.agreementExportFailed"));
      return;
    }
    message.info(t("admin.agreementPdfHint"));
  };

  if (!record.signed) {
    return (
      <section className="admin-detail-card admin-agreement-panel">
        <div className="admin-tokens-head">
          <div>
            <h2>{t("admin.agreementTitle")}</h2>
            <p className="setting-hint">{t("admin.agreementHint")}</p>
          </div>
          <Tag color="default">{t("admin.agreementUnsigned")}</Tag>
        </div>
        <Alert
          type="warning"
          showIcon
          message={t("admin.agreementUnsignedTitle")}
          description={t("admin.agreementUnsignedHint")}
        />
      </section>
    );
  }

  return (
    <div className="admin-detail-sections">
      <section className="admin-detail-card admin-agreement-panel">
        <div className="admin-tokens-head">
          <div>
            <h2>{t("admin.agreementTitle")}</h2>
            <p className="setting-hint">{t("admin.agreementHint")}</p>
          </div>
          <Space wrap>
            <Tag color="green">{t("admin.agreementSigned")}</Tag>
            <Button icon={<DownloadOutlined />} onClick={exportHtml}>
              {t("admin.agreementExport")}
            </Button>
            <Button type="primary" icon={<FilePdfOutlined />} onClick={exportPdf}>
              {t("admin.agreementExportPdf")}
            </Button>
          </Space>
        </div>
        <Descriptions size="small" bordered column={3}>
          <Descriptions.Item label={t("admin.agreementStatus")}>
            <Tag color="green">{t("admin.agreementSigned")}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t("admin.agreementSignedAt")}>
            {record.acceptedAt ? dayjs(record.acceptedAt).format("YYYY-MM-DD HH:mm:ss") : "—"}
          </Descriptions.Item>
          <Descriptions.Item label={t("admin.agreementLanguage")}>
            {agreementLanguageName(record.language)}
            <span className="admin-muted"> ({language})</span>
          </Descriptions.Item>
          <Descriptions.Item label={t("admin.agreementSigner")}>
            {record.signerName || record.displayName || record.username || "—"}
            {record.username ? <span className="admin-muted"> @{record.username}</span> : null}
          </Descriptions.Item>
          <Descriptions.Item label={t("admin.agreementUserId")}>
            {record.userId}
          </Descriptions.Item>
          <Descriptions.Item label={t("admin.agreementTenant")}>
            {record.tenantName || "—"}
          </Descriptions.Item>
          <Descriptions.Item label={t("admin.agreementLocation")}>
            {record.location || record.timezone || "—"}
          </Descriptions.Item>
          <Descriptions.Item label={t("admin.agreementTimezone")}>
            {record.timezone || "—"}
          </Descriptions.Item>
          <Descriptions.Item label={t("admin.agreementIp")}>
            {record.signedIp || "—"}
          </Descriptions.Item>
        </Descriptions>
        <p className="setting-hint admin-agreement-pdf-hint">
          {t("admin.agreementPdfHint")}
        </p>
      </section>

      <section className="admin-detail-card">
        <div className="form-section-label">
          {t("admin.agreementBody")} · {agreementLanguageName(record.language)}
        </div>
        <div className="agreement-body admin-agreement-body" ref={bodyRef}>
          <ReactMarkdown remarkPlugins={[remarkGfm]}>
            {USER_AGREEMENTS[language]}
          </ReactMarkdown>
        </div>
      </section>
    </div>
  );
}
