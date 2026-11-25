import { Button } from "antd";
import { useI18n } from "../i18n";

/**
 * 账户设置 · 用户注销页：注销入口卡片，点击「申请注销」进入注销流程。
 */
export function AccountCancelEntryPage() {
  const { t } = useI18n();

  return (
    <div className="account-layout">
      <section className="setting-card account-cancel-entry-card">
        <div className="account-tokens-head">
          <div>
            <h2>{t("account.cancelTitle")}</h2>
            <p className="setting-hint">{t("account.cancelEntryHint")}</p>
          </div>
          <Button danger onClick={() => {
            const opened = window.open("/account/cancel/flow", "_blank", "noopener,noreferrer");
            if (opened) opened.opener = null;
          }}>
            {t("account.cancelEntryButton")}
          </Button>
        </div>
      </section>
    </div>
  );
}
