import { FormEvent, useEffect, useMemo, useState } from 'react';
import {
  ApiError,
  createAdminUser,
  deleteAdminUser,
  fetchAdminDashboard,
  fetchAdminRun,
  fetchAdminRuns,
  fetchAdminRunTraces,
  fetchAdminUsers,
  updateAdminUser
} from '../api';
import type {
  AdminDashboardResponse,
  AdminPageResponse,
  AdminRunListItem,
  AgentRun,
  TraceEvent,
  User
} from '../types';
import './admin.css';

type AdminView = 'dashboard' | 'users' | 'runs';

interface UserFormState {
  username: string;
  password: string;
  email: string;
  phone: string;
  role: 'USER' | 'ADMIN';
  status: 'ACTIVE' | 'DISABLED';
}

const EMPTY_USER_FORM: UserFormState = {
  username: '',
  password: '',
  email: '',
  phone: '',
  role: 'USER',
  status: 'ACTIVE'
};

function formatDate(value?: string | null) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.toLocaleDateString('zh-CN')} ${date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`;
}

function toMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }
  return error instanceof Error ? error.message : String(error);
}

export function AdminConsole({
  user,
  onLogout,
  onEnterWorkspace
}: {
  user: User;
  onLogout: () => Promise<void> | void;
  onEnterWorkspace: () => void;
}) {
  const [view, setView] = useState<AdminView>('dashboard');
  const [dashboard, setDashboard] = useState<AdminDashboardResponse | null>(null);
  const [usersPage, setUsersPage] = useState<AdminPageResponse<User> | null>(null);
  const [runsPage, setRunsPage] = useState<AdminPageResponse<AdminRunListItem> | null>(null);
  const [selectedRun, setSelectedRun] = useState<AgentRun | null>(null);
  const [selectedRunTraces, setSelectedRunTraces] = useState<TraceEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [userKeyword, setUserKeyword] = useState('');
  const [userRole, setUserRole] = useState('');
  const [runKeyword, setRunKeyword] = useState('');
  const [runStatus, setRunStatus] = useState('');
  const [showUserModal, setShowUserModal] = useState(false);
  const [userModalMode, setUserModalMode] = useState<'create' | 'edit'>('create');
  const [editingUserId, setEditingUserId] = useState<string | null>(null);
  const [userForm, setUserForm] = useState<UserFormState>(EMPTY_USER_FORM);
  const [submittingUser, setSubmittingUser] = useState(false);

  const hasVisibleContent = (view === 'dashboard' && dashboard)
    || (view === 'users' && usersPage)
    || (view === 'runs' && runsPage);

  async function loadDashboard() {
    setLoading(true);
    setError(null);
    try {
      setDashboard(await fetchAdminDashboard());
    } catch (err) {
      setError(toMessage(err));
    } finally {
      setLoading(false);
    }
  }

  async function loadUsers() {
    setLoading(true);
    setError(null);
    try {
      setUsersPage(await fetchAdminUsers({ keyword: userKeyword, role: userRole, page: 1, size: 20 }));
    } catch (err) {
      setError(toMessage(err));
    } finally {
      setLoading(false);
    }
  }

  async function loadRuns() {
    setLoading(true);
    setError(null);
    try {
      setRunsPage(await fetchAdminRuns({ keyword: runKeyword, status: runStatus, page: 1, size: 20 }));
    } catch (err) {
      setError(toMessage(err));
    } finally {
      setLoading(false);
    }
  }

  async function openRun(runId: string) {
    setDetailLoading(true);
    setError(null);
    try {
      const [run, traces] = await Promise.all([fetchAdminRun(runId), fetchAdminRunTraces(runId)]);
      setSelectedRun(run);
      setSelectedRunTraces(traces);
    } catch (err) {
      setError(toMessage(err));
    } finally {
      setDetailLoading(false);
    }
  }

  useEffect(() => {
    if (view === 'dashboard') {
      void loadDashboard();
      return;
    }
    if (view === 'users') {
      void loadUsers();
      return;
    }
    void loadRuns();
  }, [view]);

  const summaryCards = useMemo(() => {
    if (!dashboard) return [];
    return [
      { label: '用户数', value: dashboard.summary.userCount },
      { label: '管理员', value: dashboard.summary.adminCount },
      { label: '知识库', value: dashboard.summary.knowledgeBaseCount },
      { label: '文档数', value: dashboard.summary.documentCount },
      { label: '24h 运行', value: dashboard.summary.runCount24h },
      { label: '24h 异常', value: dashboard.summary.failedRunCount24h }
    ];
  }, [dashboard]);

  function submitUserSearch(event: FormEvent) {
    event.preventDefault();
    void loadUsers();
  }

  function submitRunSearch(event: FormEvent) {
    event.preventDefault();
    void loadRuns();
  }

  function openCreateUserModal() {
    setUserModalMode('create');
    setEditingUserId(null);
    setUserForm(EMPTY_USER_FORM);
    setShowUserModal(true);
  }

  function openEditUserModal(item: User) {
    setUserModalMode('edit');
    setEditingUserId(item.userId);
    setUserForm({
      username: item.username,
      password: '',
      email: item.email || '',
      phone: item.phone || '',
      role: item.role,
      status: (item.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE')
    });
    setShowUserModal(true);
  }

  function closeUserModal() {
    if (submittingUser) return;
    setShowUserModal(false);
    setEditingUserId(null);
    setUserForm(EMPTY_USER_FORM);
  }

  async function submitUserModal(event: FormEvent) {
    event.preventDefault();
    setSubmittingUser(true);
    setError(null);
    try {
      if (userModalMode === 'create') {
        await createAdminUser(userForm);
      } else if (editingUserId) {
        await updateAdminUser(editingUserId, {
          ...userForm,
          password: userForm.password.trim() || undefined
        });
      }
      closeUserModal();
      await loadUsers();
      await loadDashboard();
    } catch (err) {
      setError(toMessage(err));
    } finally {
      setSubmittingUser(false);
    }
  }

  async function handleDeleteUser(item: User) {
    const confirmed = window.confirm(`确认删除用户 ${item.username} 吗？此操作不可撤销。`);
    if (!confirmed) return;
    setError(null);
    try {
      await deleteAdminUser(item.userId);
      await loadUsers();
      await loadDashboard();
    } catch (err) {
      setError(toMessage(err));
    }
  }

  return (
    <div className="admin-shell">
      <header className="admin-topbar">
        <div>
          <strong className="admin-brand">超级Agent 管理台</strong>
          <p className="admin-subtitle">管理员：{user.username}</p>
        </div>
        <div className="admin-topbar-actions">
          <button className="admin-ghost-button" onClick={onEnterWorkspace} type="button">进入工作台</button>
          <button className="admin-primary-button" onClick={() => void onLogout()} type="button">退出登录</button>
        </div>
      </header>

      <aside className="admin-sidebar-panel">
        <button className={view === 'dashboard' ? 'admin-nav-item active' : 'admin-nav-item'} onClick={() => setView('dashboard')} type="button">总览</button>
        <button className={view === 'users' ? 'admin-nav-item active' : 'admin-nav-item'} onClick={() => setView('users')} type="button">用户</button>
        <button className={view === 'runs' ? 'admin-nav-item active' : 'admin-nav-item'} onClick={() => setView('runs')} type="button">运行记录</button>
      </aside>

      <main className="admin-main-panel">
        {error && <div className="admin-error-banner">{error}</div>}
        {loading && !hasVisibleContent && <div className="admin-loading">加载中...</div>}

        {view === 'dashboard' && dashboard && (
          <section className="admin-section-stack">
            <div className="admin-card-grid">
              {summaryCards.map((card) => (
                <article className="admin-stat-card" key={card.label}>
                  <span>{card.label}</span>
                  <strong>{card.value}</strong>
                </article>
              ))}
            </div>

            <section className="admin-panel-card">
              <div className="admin-panel-header">
                <h2>系统状态</h2>
                <button className="admin-ghost-button" onClick={() => void loadDashboard()} type="button">刷新</button>
              </div>
              <div className="admin-health-grid">
                <div>
                  <span className="admin-label">Java</span>
                  <strong>{dashboard.health.status}</strong>
                </div>
                <div>
                  <span className="admin-label">Python Agent</span>
                  <strong>{dashboard.health.agent?.status ?? '-'}</strong>
                </div>
              </div>
            </section>

            <div className="admin-two-column">
              <section className="admin-panel-card">
                <div className="admin-panel-header">
                  <h2>最近异常运行</h2>
                </div>
                <div className="admin-list-block">
                  {dashboard.recentFailedRuns.length === 0 && <p className="admin-empty-text">暂无异常运行</p>}
                  {dashboard.recentFailedRuns.map((item) => (
                    <button className="admin-list-item" key={item.runId} onClick={() => { setView('runs'); void openRun(item.runId); }} type="button">
                      <strong>{item.username} · {item.status}</strong>
                      <span>{item.message}</span>
                    </button>
                  ))}
                </div>
              </section>

              <section className="admin-panel-card">
                <div className="admin-panel-header">
                  <h2>最近新增用户</h2>
                </div>
                <div className="admin-list-block">
                  {dashboard.recentUsers.length === 0 && <p className="admin-empty-text">暂无用户</p>}
                  {dashboard.recentUsers.map((item) => (
                    <div className="admin-list-item static" key={item.userId}>
                      <strong>{item.username} · {item.role}</strong>
                      <span>{formatDate(item.createdAt)}</span>
                    </div>
                  ))}
                </div>
              </section>
            </div>
          </section>
        )}

        {view === 'users' && usersPage && (
          <section className="admin-section-stack">
            <form className="admin-filter-bar" onSubmit={submitUserSearch}>
              <input value={userKeyword} onChange={(event) => setUserKeyword(event.target.value)} placeholder="搜索用户名、邮箱或手机号" />
              <select value={userRole} onChange={(event) => setUserRole(event.target.value)}>
                <option value="">全部角色</option>
                <option value="USER">USER</option>
                <option value="ADMIN">ADMIN</option>
              </select>
              <button className="admin-primary-button" type="submit">查询</button>
              <button className="admin-ghost-button" onClick={openCreateUserModal} type="button">新增用户</button>
            </form>

            <section className="admin-panel-card">
              <div className="admin-panel-header">
                <div className="admin-panel-header-main">
                  <h2>用户列表</h2>
                  {loading && <span className="admin-loading-inline admin-loading-inline-compact">正在刷新数据...</span>}
                </div>
                <span>共 {usersPage.total} 人</span>
              </div>
              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>用户名</th>
                      <th>角色</th>
                      <th>状态</th>
                      <th>邮箱</th>
                      <th>最近登录</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {usersPage.items.map((item) => (
                      <tr key={item.userId}>
                        <td>{item.username}</td>
                        <td>{item.role}</td>
                        <td>{item.status}</td>
                        <td>{item.email || '-'}</td>
                        <td>{formatDate((item as User & { lastLoginAt?: string | null }).lastLoginAt)}</td>
                        <td>
                          <div className="admin-table-actions">
                            <button className="admin-inline-button" onClick={() => openEditUserModal(item)} type="button">编辑</button>
                            <button className="admin-inline-button danger" onClick={() => void handleDeleteUser(item)} type="button">删除</button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </section>
        )}

        {view === 'runs' && runsPage && (
          <section className="admin-section-stack admin-runs-layout">
            <div>
              <form className="admin-filter-bar" onSubmit={submitRunSearch}>
                <input value={runKeyword} onChange={(event) => setRunKeyword(event.target.value)} placeholder="搜索运行 ID、用户或消息" />
                <select value={runStatus} onChange={(event) => setRunStatus(event.target.value)}>
                  <option value="">全部状态</option>
                  <option value="completed">completed</option>
                  <option value="failed">failed</option>
                </select>
                <button className="admin-primary-button" type="submit">查询</button>
              </form>

              <section className="admin-panel-card">
                <div className="admin-panel-header">
                  <h2>运行记录</h2>
                  <span>共 {runsPage.total} 条</span>
                </div>
                <div className="admin-table-wrap">
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>运行 ID</th>
                        <th>用户</th>
                        <th>能力</th>
                        <th>状态</th>
                        <th>时间</th>
                      </tr>
                    </thead>
                    <tbody>
                      {runsPage.items.map((item) => (
                        <tr className="clickable" key={item.runId} onClick={() => void openRun(item.runId)}>
                          <td>{item.runId.slice(0, 8)}</td>
                          <td>{item.username}</td>
                          <td>{item.capability}</td>
                          <td>{item.status}</td>
                          <td>{formatDate(item.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            </div>

            <aside className="admin-panel-card admin-detail-panel">
              <div className="admin-panel-header">
                <h2>运行详情</h2>
                {detailLoading && <span>加载中...</span>}
              </div>
              {!selectedRun && !detailLoading && <p className="admin-empty-text">点击左侧运行记录查看详情</p>}
              {selectedRun && (
                <div className="admin-detail-content">
                  <div className="admin-detail-block">
                    <span className="admin-label">状态</span>
                    <strong>{selectedRun.status}</strong>
                  </div>
                  <div className="admin-detail-block">
                    <span className="admin-label">消息</span>
                    <p>{selectedRun.message}</p>
                  </div>
                  <div className="admin-detail-block">
                    <span className="admin-label">路由原因</span>
                    <p>{selectedRun.routeReason || '-'}</p>
                  </div>
                  <div className="admin-detail-block">
                    <span className="admin-label">最终结果</span>
                    <p>{selectedRun.finalResult || '-'}</p>
                  </div>
                  <div className="admin-detail-block">
                    <span className="admin-label">Trace</span>
                    <div className="admin-trace-list">
                      {selectedRunTraces.length === 0 && <p className="admin-empty-text">暂无 trace</p>}
                      {selectedRunTraces.map((trace, index) => (
                        <div className="admin-trace-item" key={`${trace.timestamp}-${index}`}>
                          <strong>{trace.eventType}</strong>
                          <span>{trace.message}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </aside>
          </section>
        )}
      </main>

      {showUserModal && (
        <div className="admin-modal-backdrop" onClick={closeUserModal}>
          <div className="admin-modal-card" onClick={(event) => event.stopPropagation()}>
            <div className="admin-panel-header">
              <h2>{userModalMode === 'create' ? '新增用户' : '编辑用户'}</h2>
              <button className="admin-ghost-button" onClick={closeUserModal} type="button">关闭</button>
            </div>
            <form className="admin-user-form" onSubmit={submitUserModal}>
              <label>
                <span>用户名</span>
                <input value={userForm.username} onChange={(event) => setUserForm((state) => ({ ...state, username: event.target.value }))} required />
              </label>
              <label>
                <span>{userModalMode === 'create' ? '密码' : '新密码'}</span>
                <input
                  type="password"
                  value={userForm.password}
                  onChange={(event) => setUserForm((state) => ({ ...state, password: event.target.value }))}
                  placeholder={userModalMode === 'edit' ? '留空则不修改' : ''}
                  required={userModalMode === 'create'}
                />
              </label>
              <label>
                <span>邮箱</span>
                <input value={userForm.email} onChange={(event) => setUserForm((state) => ({ ...state, email: event.target.value }))} />
              </label>
              <label>
                <span>手机号</span>
                <input value={userForm.phone} onChange={(event) => setUserForm((state) => ({ ...state, phone: event.target.value }))} />
              </label>
              <label>
                <span>角色</span>
                <select value={userForm.role} onChange={(event) => setUserForm((state) => ({ ...state, role: event.target.value as 'USER' | 'ADMIN' }))}>
                  <option value="USER">USER</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </label>
              <label>
                <span>状态</span>
                <select value={userForm.status} onChange={(event) => setUserForm((state) => ({ ...state, status: event.target.value as 'ACTIVE' | 'DISABLED' }))}>
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="DISABLED">DISABLED</option>
                </select>
              </label>
              <div className="admin-modal-actions">
                <button className="admin-ghost-button" onClick={closeUserModal} type="button">取消</button>
                <button className="admin-primary-button" disabled={submittingUser} type="submit">
                  {submittingUser ? '提交中...' : userModalMode === 'create' ? '创建' : '保存'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}