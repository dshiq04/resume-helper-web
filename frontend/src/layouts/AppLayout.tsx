import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
  type ReactNode,
} from "react";
import {
  BarChartOutlined,
  CloseOutlined,
  DeleteOutlined,
  FileTextOutlined,
  InboxOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  MoonOutlined,
  SettingOutlined,
  SunOutlined,
} from "@ant-design/icons";
import { App as AntdApp, Breadcrumb, Drawer, Menu as AntdMenu, type MenuProps } from "antd";
import dayjs from "dayjs";
import {
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router-dom";
import type { PageTab } from "../store/slices/tabsSlice";
import { useAuth } from "../context/AuthContext";
import { useMediaQuery } from "../hooks/useMediaQuery";
import { useI18n } from "../i18n";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import { loadOnline } from "../store/slices/metaSlice";
import { closeTab, syncRoute } from "../store/slices/tabsSlice";
import { setSidebarCollapsed, toggleTheme } from "../store/slices/uiSlice";

function withCount(label: string, count?: number) {
  return (
    <span className="menu-label">
      <span>{label}</span>
      <span className="menu-count">{count ?? 0}</span>
    </span>
  );
}

const PATH_KEYS: Record<string, string> = {
  "/": "nav.all",
  "/spring": "nav.spring",
  "/autumn": "nav.autumn",
  "/internship": "nav.internship",
  "/social": "nav.social",
  "/resumes": "nav.resumes",
  "/statistics": "nav.statistics",
  "/trash": "nav.trash",
  "/settings": "nav.settings",
};

function tabForPath(path: string): PageTab | null {
  if (path.startsWith("/applications/")) {
    return { path, labelKey: "detail.title", closable: true };
  }
  const labelKey = PATH_KEYS[path];
  if (!labelKey) {
    return null;
  }
  return { path, labelKey, closable: path !== "/" };
}

export function AppLayout() {
  const { t } = useI18n();
  const { message } = AntdApp.useApp();
  const { user, logout } = useAuth();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const pageRef = useRef<HTMLDivElement>(null);
  const isMobile = useMediaQuery("(max-width: 900px)");
  const isTablet = useMediaQuery("(min-width: 901px) and (max-width: 1180px)");
  const isPhone = useMediaQuery("(max-width: 767px)");
  const stats = useAppSelector((state) => state.meta.stats);
  const themeMode = useAppSelector((state) => state.ui.themeMode);
  const collapsed = useAppSelector((state) => state.ui.sidebarCollapsed);
  const rail = !isMobile && (isTablet || collapsed);
  const tabs = useAppSelector((state) => state.tabs.tabs);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [openKeys, setOpenKeys] = useState<string[]>([
    "group:applications",
    "group:resumes",
  ]);
  const menuInteracted = useRef(false);

  useEffect(() => {
    const load = () => void dispatch(loadOnline());
    load();
    const timer = window.setInterval(load, 60_000);
    return () => window.clearInterval(timer);
  }, [dispatch]);

  useEffect(() => {
    const tab = tabForPath(location.pathname);
    if (tab) {
      dispatch(syncRoute(tab));
    }
  }, [dispatch, location.pathname]);

  useEffect(() => {
    const path = location.pathname;
    const group = path === "/resumes"
      ? "group:resumes"
      : (path === "/" || ["/spring", "/autumn", "/internship", "/social"].includes(path)
        || path.startsWith("/applications/"))
        ? "group:applications"
        : null;
    if (group) {
      setOpenKeys((current) => (current.includes(group) ? current : [...current, group]));
    }
  }, [location.pathname]);

  // 路由切换时重新触发页面进入动画，但不卸载页面组件。
  useLayoutEffect(() => {
    const node = pageRef.current;
    if (!node) return;
    node.classList.remove("is-entering");
    void node.offsetWidth;
    node.classList.add("is-entering");
  }, [location.pathname]);

  const menuItems = useMemo<MenuProps["items"]>(
    () => [
      {
        key: "group:applications",
        icon: <InboxOutlined />,
        label: t("dash.eyebrow"),
        children: [
          { key: "/", label: withCount(t("nav.all"), stats?.total) },
          { key: "/spring", label: withCount(t("nav.spring"), stats?.byCategory?.spring) },
          { key: "/autumn", label: withCount(t("nav.autumn"), stats?.byCategory?.autumn) },
          {
            key: "/internship",
            label: withCount(t("nav.internship"), stats?.byCategory?.internship),
          },
          { key: "/social", label: withCount(t("nav.social"), stats?.byCategory?.social) },
        ],
      },
      {
        key: "group:resumes",
        icon: <FileTextOutlined />,
        label: t("nav.resumes"),
        children: [
          { key: "/resumes", label: withCount(t("nav.resumeList"), stats?.resumes) },
        ],
      },
      { key: "/statistics", icon: <BarChartOutlined />, label: t("nav.statistics") },
      { key: "/trash", icon: <DeleteOutlined />, label: t("nav.trash") },
      { key: "/settings", icon: <SettingOutlined />, label: t("nav.settings") },
    ],
    [stats, t],
  );

  const selectedKeys = useMemo(() => {
    const path = location.pathname;
    const known = ["/", "/spring", "/autumn", "/internship", "/social",
      "/resumes", "/statistics", "/trash", "/settings"];
    return known.includes(path) ? [path] : [];
  }, [location.pathname]);


  const meta = useMemo(() => {
    if (location.pathname.startsWith("/applications/")) {
      return { eyebrow: t("nav.all"), title: t("detail.title") };
    }
    const labelKey = PATH_KEYS[location.pathname] ?? "nav.all";
    return { eyebrow: t("dash.eyebrow"), title: t(labelKey) };
  }, [location.pathname, t]);

  const handleLogout = async () => {
    await logout();
    message.success(t("auth.goLogin"));
  };

  const handleCloseTab = (event: ReactMouseEvent, path: string) => {
    event.stopPropagation();
    const remaining = tabs.filter((tab) => tab.path !== path);
    dispatch(closeTab(path));
    if (path === location.pathname) {
      navigate(remaining.length > 0 ? remaining[remaining.length - 1].path : "/");
    }
  };

  const sidebarContent = (
    <>
      <div className="brand">
        <img className="brand-logo" src="/logo.svg" alt="ResumeTrail" />
        {!rail && (
          <div className="brand-text">
            <strong>{t("app.name")}</strong>
            <span>RESUMETRAIL</span>
          </div>
        )}
      </div>

      <nav className="side-nav">
        <AntdMenu
          mode="inline"
          theme="dark"
          inlineCollapsed={rail}
          selectedKeys={selectedKeys}
          openKeys={rail ? undefined : openKeys}
          onOpenChange={(keys) => {
            const next = keys as string[];
            if (next.length > 0) {
              menuInteracted.current = true;
            } else if (!menuInteracted.current) {
              // 忽略挂载阶段内部触发的空数组，保留默认展开的两组菜单。
              return;
            }
            setOpenKeys(next);
          }}
          onClick={({ key }) => {
            if (key.startsWith("group:")) return;
            navigate(key);
            setDrawerOpen(false);
          }}
          items={menuItems}
        />
      </nav>

      <div className="side-foot">
        <div className="side-user">
          <div className="avatar">{user?.displayName?.slice(0, 1) || "U"}</div>
          {!rail && (
            <div className="side-user-text">
              <strong>{user?.displayName || user?.username}</strong>
              <span>
                <i className="online-dot" />
                {user?.tenantName}
              </span>
            </div>
          )}
        </div>
        {!rail && (
          <div className="side-foot-actions">
            <NavLink to="/settings" className="ghost-icon" title={t("nav.settings")}>
              <SettingOutlined />
            </NavLink>
            <button
              type="button"
              className="ghost-icon"
              title={t("nav.logout")}
              onClick={() => void handleLogout()}
            >
              <LogoutOutlined />
            </button>
          </div>
        )}
      </div>
    </>
  );

  return (
    <div className={`app-shell${rail ? " is-collapsed" : ""}`}>
      {!isMobile && (
        <aside className="sidebar">
          {sidebarContent}
        </aside>
      )}

      <div className="app-stage">
        <header className="topbar">
          <div className="topbar-left">
            {isMobile && (
              <button
                type="button"
                className="ghost-icon"
                onClick={() => setDrawerOpen(true)}
                title={t("nav.all")}
              >
                <MenuOutlined />
              </button>
            )}
            {!isMobile && !isTablet && (
              <button
                type="button"
                className="topbar-collapse"
                onClick={() => dispatch(setSidebarCollapsed(!collapsed))}
                title={rail ? t("common.view") : t("common.close")}
              >
                {rail ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              </button>
            )}
            <Breadcrumb
              className="topbar-crumb"
              items={[
                { title: <span className="crumb-section">{meta.eyebrow}</span> },
                { title: <span className="crumb-page">{meta.title}</span> },
              ]}
            />
          </div>
          <div className="topbar-right">
            <span className="topbar-date">{dayjs().format("YYYY.MM.DD")}</span>
            <button
              type="button"
              className="ghost-icon"
              onClick={() => dispatch(toggleTheme())}
              title={themeMode === "light" ? t("settings.dark") : t("settings.light")}
            >
              {themeMode === "light" ? <MoonOutlined /> : <SunOutlined />}
            </button>
          </div>
        </header>

        <div className="page-tabs" role="tablist">
          <div className="page-tabs-scroll">
            {tabs.map((tab) => {
              const active = location.pathname === tab.path;
              const suffix = tab.path.startsWith("/applications/")
                ? ` #${tab.path.split("/").pop()}`
                : "";
              return (
                <button
                  key={tab.path}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  className={`page-tab${active ? " is-active" : ""}`}
                  onClick={() => navigate(tab.path)}
                >
                  <span className="page-tab-dot" />
                  <span>{t(tab.labelKey)}{suffix}</span>
                  {tab.closable && (
                    <span
                      className="page-tab-close"
                      role="button"
                      tabIndex={0}
                      onClick={(event) => handleCloseTab(event, tab.path)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          handleCloseTab(event as unknown as ReactMouseEvent, tab.path);
                        }
                      }}
                    >
                      <CloseOutlined />
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>

        <main className="page-scroll">
          <div className="page-view is-entering" ref={pageRef}>
            <div className="page-inner">
              <Outlet />
            </div>
          </div>
        </main>
      </div>

      {isPhone && (
        <nav className="mobile-bottom-nav">
          <NavLink to="/" end className={({ isActive }) => (isActive ? "is-active" : "")}>
            <InboxOutlined />
            <span>{t("nav.all")}</span>
          </NavLink>
          <NavLink to="/resumes" className={({ isActive }) => (isActive ? "is-active" : "")}>
            <FileTextOutlined />
            <span>{t("nav.resumes")}</span>
          </NavLink>
          <NavLink to="/statistics" className={({ isActive }) => (isActive ? "is-active" : "")}>
            <BarChartOutlined />
            <span>{t("nav.statistics")}</span>
          </NavLink>
          <NavLink to="/trash" className={({ isActive }) => (isActive ? "is-active" : "")}>
            <DeleteOutlined />
            <span>{t("nav.trash")}</span>
          </NavLink>
          <NavLink to="/settings" className={({ isActive }) => (isActive ? "is-active" : "")}>
            <SettingOutlined />
            <span>{t("nav.settings")}</span>
          </NavLink>
        </nav>
      )}

      <Drawer
        placement="left"
        width={252}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        closable={false}
        className="mobile-drawer"
      >
        <div className="drawer-sidebar">
          {sidebarContent}
        </div>
      </Drawer>
    </div>
  );
}
