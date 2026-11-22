import { useI18n } from "../../i18n";
import { OperationLogTable } from "./OperationLogTable";

/** 用户详情 · 操作记录 tab。 */
export function AdminUserOperationsPanel({ userId }: { userId: number }) {
  const { t } = useI18n();
  return (
    <section className="admin-detail-card admin-user-operations">
      <div className="admin-tokens-head">
        <div>
          <h2>{t("admin.userOperationsTitle")}</h2>
          <p className="setting-hint">{t("admin.userOperationsHint")}</p>
        </div>
      </div>
      <OperationLogTable userId={userId} embedded />
    </section>
  );
}
