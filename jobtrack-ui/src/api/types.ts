export type Category = "spring" | "autumn" | "internship" | "social";
export type Status =
  | "pending"
  | "screening"
  | "written"
  | "interview"
  | "offer"
  | "rejected";

export const CATEGORIES: Category[] = ["spring", "autumn", "internship", "social"];
export const STATUSES: Status[] = [
  "pending",
  "screening",
  "written",
  "interview",
  "offer",
  "rejected",
];

export interface User {
  id: number;
  tenantId: number;
  username: string;
  displayName: string;
  role: string;
  /** 账号状态：NORMAL 正常 / CANCELLED 已注销 / BANNED 已封禁。 */
  status?: string;
  tenantName: string;
  avatarUrl?: string | null;
  /** 用户权限编码，如 USER / ADMIN / ADMIN_CONSOLE。 */
  permissions?: string[];
  enabled?: boolean;
  banType?: string | null;
  banUntil?: string | null;
  bannedAt?: string | null;
  banReason?: string | null;
  createdAt?: string | null;
}

export interface AdminUser {
  id: number;
  tenantId: number;
  username: string;
  displayName: string;
  role: string;
  status: string;
  enabled: boolean;
  tenantName: string;
  avatarUrl?: string | null;
  permissions: string[];
  online: boolean;
  onlineSessions: number;
  banType?: string | null;
  banUntil?: string | null;
  bannedAt?: string | null;
  banReason?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  /** 最后一次操作时间。 */
  lastOperationAt?: string | null;
  /** 最后一次成功登录时间。 */
  lastLoginAt?: string | null;
}

export interface AdminUserPage {
  items: AdminUser[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AdminUserDetail {
  user: AdminUser;
  profile: UserProfile;
}

/** 用户协议签署记录。signed=false 表示尚未签署。 */
export interface UserAgreementRecord {
  signed: boolean;
  userId: number;
  username?: string;
  displayName?: string;
  tenantName?: string;
  acceptedAt?: string | null;
  /** 签署时界面语言：zh-CN / zh-TW / en / ja / ko / ru。 */
  language?: string | null;
  signerName?: string | null;
  location?: string | null;
  timezone?: string | null;
  signedIp?: string | null;
}

export type OperationType =
  | "LOGIN"
  | "LOGOUT"
  | "REGISTER"
  | "AGREEMENT"
  | "PROFILE"
  | "SECURITY"
  | "APPLICATION"
  | "RESUME"
  | "TOKEN"
  | "ACCOUNT"
  | "ADMIN"
  | "OTHER";

export interface UserOperationLog {
  id: number;
  userId: number;
  username?: string | null;
  displayName?: string | null;
  operationType: OperationType | string;
  action?: string | null;
  description?: string | null;
  method?: string | null;
  path?: string | null;
  ip?: string | null;
  userAgent?: string | null;
  createdAt: string;
}

export interface UserOperationPage {
  items: UserOperationLog[];
  total: number;
  page: number;
  pageSize: number;
}

export interface PermissionItem {
  id: number;
  code: string;
  name: string;
  description?: string | null;
}

export interface OnlineUser {
  userId: number;
  tenantId: number;
  username: string;
  displayName: string;
  tenantName: string;
  avatarUrl?: string | null;
  sessions: number;
  remainingSeconds: number[];
}

export interface ImportResult {
  imported: number;
  skipped: number;
  total: number;
  errors: string[];
}

export interface UserProfile {
  userId: number;
  realName?: string | null;
  gender?: string | null;
  birthday?: string | null;
  phone?: string | null;
  email?: string | null;
  location?: string | null;
  hometown?: string | null;
  website?: string | null;
  github?: string | null;
  linkedin?: string | null;
  wechat?: string | null;
  targetPosition?: string | null;
  expectedSalary?: string | null;
  workMode?: string | null;
  jobStatus?: string | null;
  availableDate?: string | null;
  school?: string | null;
  major?: string | null;
  degree?: string | null;
  graduationYear?: number | null;
  experienceYears?: number | null;
  currentCompany?: string | null;
  currentTitle?: string | null;
  skills?: string | null;
  languages?: string | null;
  bio?: string | null;
  avatarUrl?: string | null;
  updatedAt?: string | null;
}

export type TokenScope = "read" | "write" | "readwrite";
export type TokenResource = "applications" | "resumes" | "statistics" | "profile";

export interface AccessToken {
  id: number;
  name: string;
  code: string;
  scope: TokenScope;
  resources: TokenResource[];
  enabled: boolean;
  expiresOn?: string | null;
  lastUsedAt?: string | null;
  createdAt?: string | null;
}

export interface AccessTokenLog {
  id: number;
  tokenId?: number | null;
  tokenName?: string | null;
  codePrefix?: string | null;
  scope?: TokenScope | null;
  resources: TokenResource[];
  result: "valid" | "invalid" | "expired" | "disabled" | "banned";
  ip?: string | null;
  userAgent?: string | null;
  createdAt?: string | null;
}

export interface AccessTokenLogPage {
  items: AccessTokenLog[];
  total: number;
  page: number;
  pageSize: number;
}

export interface CancelStatus {
  applications: number;
  trash: number;
  resumes: number;
  /** 未删除的授权码数量。 */
  tokens: number;
  elevatedPermission: boolean;
}

export interface AccessTokenInput {
  name?: string;
  scope: TokenScope;
  resources?: TokenResource[];
  expiresOn?: string | null;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface CaptchaResponse {
  captchaId: string;
  image: string;
  expiresIn: number;
}

export interface PublicConfig {
  categories: Category[];
  statuses: Status[];
  timelineStatuses: Status[];
  channels: string[];
  priorities: string[];
  workModes: string[];
  maxResumeSizeMb: number;
  registrationEnabled: boolean;
  captchaEnabled: boolean;
  onlineMode: "redis" | "memory";
}

export interface Application {
  id: number;
  category: Category;
  companyName: string;
  link: string;
  year: number;
  position?: string;
  status: Status;
  channel?: string;
  city?: string;
  salary?: string;
  contact?: string;
  contactInfo?: string;
  workMode?: string;
  priority?: string;
  interviewRound?: number;
  nextActionAt?: string;
  notes?: string;
  pinned: boolean;
  appliedAt: string;
  createdAt: string;
  updatedAt: string;
  statusDays?: number;
  screeningDays?: number;
}

export interface ApplicationList {
  items: Application[];
  total: number;
  page: number;
  pageSize: number;
}

export interface ApplicationInput {
  category: Category;
  companyName: string;
  link: string;
  year?: number;
  position?: string;
  status: Status;
  channel?: string;
  city?: string;
  salary?: string;
  contact?: string;
  contactInfo?: string;
  workMode?: string;
  priority?: string;
  interviewRound?: number;
  nextActionAt?: string;
  notes?: string;
  pinned?: boolean;
}

export type ApplicationUpdate = Partial<ApplicationInput>;

export interface ListParams {
  search?: string;
  category?: Category | "all";
  status?: Status | "all";
  channel?: string;
  priority?: string;
  year?: number | "all";
  from?: string;
  to?: string;
  page?: number;
  pageSize?: number;
}

export interface HistoryItem {
  id: number;
  applicationId: number;
  status: Status;
  changedAt: string;
  daysSincePrevious?: number;
  daysInStatus?: number;
}

export interface HistoryResponse {
  items: HistoryItem[];
  missingStatuses: Status[];
  screeningDays: number;
  appliedAt: string;
}

export interface TrashItem {
  id: number;
  originalId: number;
  companyName: string;
  category: Category;
  status: Status;
  link: string;
  pinned: boolean;
  appliedAt: string;
  deletedAt: string;
  history: HistoryItem[];
}

export interface TrashList {
  items: TrashItem[];
  total: number;
}

export interface Resume {
  id: number;
  name: string;
  tags: string[];
  fileName: string;
  fileType: string;
  fileSize: number;
  encrypted: boolean;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ResumeList {
  items: Resume[];
  total: number;
}

export interface Stats {
  total: number;
  resumes: number;
  byCategory: Record<Category, number>;
  byStatus: Record<Status, number>;
  byCategoryStatus: Record<Category, Record<Status, number>>;
  byYear: Record<string, number>;
  stuckScreening: {
    count: number;
    averageDays: number;
    maxDays: number;
  };
}

export interface DailyStat {
  date: string;
  category: Category;
  count: number;
}

export interface DailyStats {
  daily: DailyStat[];
}

export interface FunnelStage {
  key: "applied" | Status;
  count: number;
  conversion: number;
}

export interface CompanyFunnel {
  company: string;
  total: number;
  interviews: number;
  offers: number;
  interviewRate: number;
  interviewToOfferRate: number;
  offerRate: number;
}

export interface FunnelStats {
  total: number;
  stages: FunnelStage[];
  averageDaysToInterview: number;
  averageInterviewToOffer: number;
  companies: CompanyFunnel[];
  interviewCount: number;
  offerCount: number;
}

export interface Preferences {
  idleLockEnabled: boolean;
  idleLockMinutes: number;
}

export interface OnlineStatus {
  mode: "redis" | "memory";
  onlineUsers: number;
  online: boolean;
}

export type DatabaseDriver = "mysql" | "postgresql";

export interface DatabaseSettings {
  type: DatabaseDriver | string;
  host: string;
  port: number;
  name: string;
  username: string;
  password: string;
  params: string;
  maxPoolSize: number;
  url?: string;
  driver?: string;
}

export interface DatabaseInfo {
  settings: DatabaseSettings;
  supportedTypes: DatabaseDriver[];
  profile: string;
  configFile: string;
  dataDir: string;
  editable: boolean;
}

export interface DatabaseTestResult {
  ok: boolean;
  message: string;
}

export interface SystemInfo {
  javaVersion: string;
  driver: string;
  database: string;
  location: string;
  profile: string;
  dataDir: string;
  onlineMode: "redis" | "memory";
}

export interface DeleteResult {
  deleted: number;
}

export interface QuickEntryDefaults {
  defaultCategory: Category;
  serverTime: string;
}

export interface YearList {
  items: number[];
}
