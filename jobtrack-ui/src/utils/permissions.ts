import type { User } from "../api/types";

export const ADMIN_USERS = "ADMIN_USERS";
export const ADMIN_ONLINE = "ADMIN_ONLINE";
export const ADMIN_OPERATIONS = "ADMIN_OPERATIONS";
export const ADMIN_PERMISSIONS = "ADMIN_PERMISSIONS";
export const ADMIN_CONSOLE = "ADMIN_CONSOLE";

function permissionSet(user?: User | null) {
  return new Set((user?.permissions ?? []).map((code) => code.toUpperCase()));
}

function isSuperAdmin(user?: User | null) {
  if (!user) return false;
  return (user.role ?? "").toUpperCase() === "ADMIN"
    || permissionSet(user).has("ADMIN");
}

/** 拥有指定管理员权限，或拥有兼容的 ADMIN_CONSOLE / ADMIN 权限。 */
export function hasAdminPermission(user: User | null | undefined, code: string) {
  if (!user) return false;
  const set = permissionSet(user);
  return isSuperAdmin(user) || set.has(ADMIN_CONSOLE) || set.has(code.toUpperCase());
}

export function canManageUsers(user?: User | null) {
  return hasAdminPermission(user, ADMIN_USERS);
}

export function canViewOnline(user?: User | null) {
  return hasAdminPermission(user, ADMIN_ONLINE);
}

export function canViewOperations(user?: User | null) {
  return hasAdminPermission(user, ADMIN_OPERATIONS);
}

export function canGrantPermissions(user?: User | null) {
  return hasAdminPermission(user, ADMIN_PERMISSIONS);
}

/** 当前登录用户是否可以进入管理员控制台（任意一个子权限即可）。 */
export function isAdminUser(user?: User | null) {
  return canManageUsers(user) || canViewOnline(user)
    || canViewOperations(user) || canGrantPermissions(user);
}
