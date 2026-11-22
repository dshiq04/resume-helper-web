import { useEffect, useRef, useState } from "react";
import { Button, Modal } from "antd";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { USER_AGREEMENTS } from "../data/userAgreement";
import { useI18n } from "../i18n";

export function UserAgreementModal({
  open,
  onClose,
  onAgree,
}: {
  open: boolean;
  onClose: () => void;
  onAgree?: () => void;
}) {
  const { t, lang } = useI18n();
  const bodyRef = useRef<HTMLDivElement>(null);
  const [reachedBottom, setReachedBottom] = useState(false);

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

  const confirm = () => {
    onAgree?.();
    onClose();
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={t("agreement.title")}
      width={760}
      className="agreement-modal"
      footer={
        <div className="agreement-footer">
          {!reachedBottom && (
            <span className="agreement-scroll-hint">{t("agreement.scrollHint")}</span>
          )}
          <Button type="primary" disabled={!reachedBottom} onClick={confirm}>
            {t("agreement.confirm")}
          </Button>
        </div>
      }
    >
      <div className="agreement-disclaimer">{t("agreement.disclaimer")}</div>
      <div className="agreement-body" ref={bodyRef} onScroll={handleScroll}>
        <ReactMarkdown remarkPlugins={[remarkGfm]}>
          {USER_AGREEMENTS[lang]}
        </ReactMarkdown>
      </div>
    </Modal>
  );
}
