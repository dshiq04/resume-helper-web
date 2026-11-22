import { useI18n } from "../../i18n";
import { OperationLogTable } from "./OperationLogTable";

/** 管理员 · 用户操作记录。 */
export function AdminOperationsPage() {
  const { t } = useI18n();
  return (
    <div className="admin-detail-sections">
      <section className="setting-card admin-card">
        <div>
          <h2 className="admin-section-title">{t("admin.operationsTitle")}</h2>
          <p className="setting-hint">{t("admin.operationsSubtitle")}</p>
        </div>
      </section>
      <OperationLogTable />
    </div>
  );
}
