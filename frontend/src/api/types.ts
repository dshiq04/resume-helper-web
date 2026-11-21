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
  tenantName: string;
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
  result: "valid" | "invalid" | "expired" | "disabled";
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
  name: string;
  version: string;
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
  version: string;
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
