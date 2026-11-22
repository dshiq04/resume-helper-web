import type { User } from "../api/types";

/** 当前登录用户是否可以进入管理员控制台。 */
export function isAdminUser(user?: User | null) {
  if (!user) {
    return false;
  }
  return (user.permissions ?? []).some((code) => code.toUpperCase() === "ADMIN_CONSOLE")
    || (user.role ?? "").toUpperCase() === "ADMIN";
}
