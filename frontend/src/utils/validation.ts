/** 前端与后端保持一致的邮箱、手机号基础格式校验。 */
export const EMAIL_PATTERN =
  /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$/;

export const LOCAL_PHONE_PATTERN = /^\d{5,15}$/;

export const FULL_PHONE_PATTERN = /^\+\d{6,15}$/;
