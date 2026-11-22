import { useEffect, useRef, useState } from "react";
import { Button, Modal } from "antd";
import { LogoutOutlined } from "@ant-design/icons";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { USER_AGREEMENTS } from "../data/userAgreement";
import { useI18n } from "../i18n";

export function UserAgreementModal({
  open,
  onClose,
  onAgree,
  required = false,
  confirmLoading = false,
  onLogout,
}: {
  open: boolean;
  onClose: () => void;
  onAgree?: () => void | Promise<void>;
  /** 登录后强制补签：不允许关闭，只能同意或退出登录。 */
  required?: boolean;
  confirmLoading?: boolean;
  onLogout?: () => void | Promise<void>;
}) {
  const { t, lang } = useI18n();
  const bodyRef = useRef<HTMLDivElement>(null);
  const [reachedBottom, setReachedBottom] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setReachedBottom(false);
    const frame = window.requestAnimationFrame(() => {
      const body = bodyRef.current;
      if (body && body.scrollHeight <= body.clientHeight + 8) {
        setReachedBottom(true);
      }
    });
    return () => window.cancelAnimationFrame(frame);
  }, [open, lang]);

  const handleScroll = () => {
    const body = bodyRef.current;
    if (!body) return;
    if (body.scrollTop + body.clientHeight >= body.scrollHeight - 8) {
      setReachedBottom(true);
    }
  };

  const confirm = async () => {
    if (submitting || confirmLoading) {
      return;
    }
    setSubmitting(true);
    try {
      await onAgree?.();
      if (!required) {
        onClose();
      }
    } finally {
      setSubmitting(false);
    }
  };

  const cancel = () => {
    if (required) {
      return;
    }
    onClose();
  };

  return (
    <Modal
      open={open}
      onCancel={cancel}
      title={t("agreement.title")}
      width={760}
      className="agreement-modal"
      closable={!required}
      maskClosable={!required}
      keyboard={!required}
      footer={
        <div className="agreement-footer">
          {required && onLogout && (
            <Button
              className="agreement-logout"
              icon={<LogoutOutlined />}
              onClick={() => void onLogout()}
            >
              {t("agreement.logout")}
            </Button>
          )}
          <span className="agreement-footer-spacer" />
          {!reachedBottom && (
            <span className="agreement-scroll-hint">{t("agreement.scrollHint")}</span>
          )}
          <Button
            type="primary"
            disabled={!reachedBottom}
            loading={submitting || confirmLoading}
            onClick={() => void confirm()}
          >
            {t("agreement.confirm")}
          </Button>
        </div>
      }
    >
      {required && <div className="agreement-required-hint">{t("agreement.requiredHint")}</div>}
      <div className="agreement-disclaimer">{t("agreement.disclaimer")}</div>
      <div className="agreement-body" ref={bodyRef} onScroll={handleScroll}>
        <ReactMarkdown remarkPlugins={[remarkGfm]}>
          {USER_AGREEMENTS[lang]}
        </ReactMarkdown>
      </div>
    </Modal>
  );
}
