import { Button, Modal } from "antd";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { USER_AGREEMENTS } from "../data/userAgreement";
import { useI18n } from "../i18n";

export function UserAgreementModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const { t, lang } = useI18n();

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={t("agreement.title")}
      width={760}
      className="agreement-modal"
      footer={
        <Button type="primary" onClick={onClose}>
          {t("common.close")}
        </Button>
      }
    >
      <div className="agreement-disclaimer">{t("agreement.disclaimer")}</div>
      <div className="agreement-body">
        <ReactMarkdown remarkPlugins={[remarkGfm]}>
          {USER_AGREEMENTS[lang]}
        </ReactMarkdown>
      </div>
    </Modal>
  );
}
