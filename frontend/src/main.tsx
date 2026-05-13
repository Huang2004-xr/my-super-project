import { ChangeEvent, FormEvent, ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  ApiError,
  createAgentRun,
  createConversation,
  createKnowledgeBase,
  deleteKnowledgeBase,
  fetchCapabilities,
  fetchAssetContentBlob,
  fetchConversationMessages,
  fetchConversations,
  fetchImages,
  fetchKnowledgeBases,
  fetchKnowledgeDocuments,
  fetchMe,
  fetchVideos,
  login,
  logout,
  searchKnowledgeBase,
  setAuthToken,
  uploadKnowledgeDocument,
  uploadImage,
  uploadVideo
} from './api';
import { AdminConsole } from './admin/AdminConsole';
import { AiProviderSettings } from './settings/AiProviderSettings';
import type {
  AgentRun,
  Capability,
  CapabilityDefinition,
  Conversation,
  ConversationMessage,
  GalleryImage,
  GalleryVideo,
  ImageAsset,
  KnowledgeBase,
  KnowledgeDocument,
  KnowledgeSearchHit,
  User
} from './types';
import './styles.css';

type View = 'agent' | 'knowledge' | 'gallery' | 'settings';
type ThemeMode = 'dark' | 'light';

const THEME_KEY = 'super_agent_theme';
const RECENT_SEARCH_KEY = 'super_agent_recent_search';

interface ErrorState {
  message: string;
  detail?: string;
}

type GlobalSearchMatchKind = 'exact' | 'prefix' | 'contains' | 'recent' | null;

type GlobalSearchItem =
  | { id: string; type: 'view'; label: string; hint: string; keywords: string[]; action: () => void }
  | { id: string; type: 'conversation'; label: string; hint: string; keywords: string[]; action: () => void }
  | { id: string; type: 'knowledge'; label: string; hint: string; keywords: string[]; action: () => void }
  | { id: string; type: 'quick'; label: string; hint: string; keywords: string[]; action: () => void };

interface GlobalSearchResultEntry {
  item: GlobalSearchItem;
  score: number;
  groupScore: number;
  matchKind: GlobalSearchMatchKind;
  recentLabel: string | null;
  recentBoost: number;
}

const QUICK_ACTIONS: Array<{ label: string; capability: Capability; prompt: string }> = [
  { label: '文字沟通', capability: 'TEXT_CHAT', prompt: '你好，介绍一下你能做什么' },
  { label: '视频创作', capability: 'VIDEO_CREATION', prompt: '帮我生成一条介绍产品卖点的短视频脚本' },
  { label: '图片创作', capability: 'IMAGE_CREATION', prompt: '帮我设计一张科技感产品海报' },
  { label: '知识库检索', capability: 'KNOWLEDGE_RETRIEVAL', prompt: '根据知识库说明，超级Agent支持哪些能力？' }
];

const CAPABILITY_LABELS: Record<Capability, string> = {
  TEXT_CHAT: '文字沟通',
  VIDEO_CREATION: '视频创作',
  IMAGE_CREATION: '图片创作',
  KNOWLEDGE_RETRIEVAL: '知识库检索'
};

function App() {
  const [user, setUser] = useState<User | null>(null);
  const [isAdminConsole, setIsAdminConsole] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const [theme, setTheme] = useState<ThemeMode>(() => {
    const stored = localStorage.getItem(THEME_KEY);
    return stored === 'light' ? 'light' : 'dark';
  });
  const [view, setView] = useState<View>('agent');
  const [error, setError] = useState<ErrorState | null>(null);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [currentConversation, setCurrentConversation] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<ConversationMessage[]>([]);
  const [runsById, setRunsById] = useState<Record<string, AgentRun>>({});
  const [currentRun, setCurrentRun] = useState<AgentRun | null>(null);
  const [capabilities, setCapabilities] = useState<CapabilityDefinition[]>([]);
  const [prompt, setPrompt] = useState('');
  const [useKnowledgeBase, setUseKnowledgeBase] = useState(false);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState<string | null>(null);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imageAsset, setImageAsset] = useState<ImageAsset | null>(null);
  const [sending, setSending] = useState(false);
  const [isGlobalSearchOpen, setIsGlobalSearchOpen] = useState(false);
  const [globalSearchQuery, setGlobalSearchQuery] = useState('');
  const [selectedGlobalSearchIndex, setSelectedGlobalSearchIndex] = useState(0);
  const [recentSearchUsage, setRecentSearchUsage] = useState<Record<string, number>>(() => {
    try {
      const stored = localStorage.getItem(RECENT_SEARCH_KEY);
      return stored ? JSON.parse(stored) as Record<string, number> : {};
    } catch {
      return {};
    }
  });
  const imageInputRef = useRef<HTMLInputElement>(null);
  const composerInputRef = useRef<HTMLTextAreaElement>(null);
  const globalSearchInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(THEME_KEY, theme);
  }, [theme]);

  useEffect(() => {
    localStorage.setItem(RECENT_SEARCH_KEY, JSON.stringify(recentSearchUsage));
  }, [recentSearchUsage]);

  useEffect(() => {
    fetchMe()
      .then((me) => {
        setUser(me);
        setIsAdminConsole(me.role === 'ADMIN');
        return bootstrap();
      })
      .catch(() => setAuthToken(null))
      .finally(() => setAuthReady(true));
  }, []);

  useEffect(() => {
    function handleGlobalShortcut(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setIsGlobalSearchOpen(true);
      }
      if (event.key === 'Escape') {
        setIsGlobalSearchOpen(false);
      }
    }

    window.addEventListener('keydown', handleGlobalShortcut);
    return () => window.removeEventListener('keydown', handleGlobalShortcut);
  }, []);

  useEffect(() => {
    if (!isGlobalSearchOpen) {
      setGlobalSearchQuery('');
      setSelectedGlobalSearchIndex(0);
      return;
    }
    window.setTimeout(() => globalSearchInputRef.current?.focus(), 0);
  }, [isGlobalSearchOpen]);

  async function bootstrap() {
    const [conversationList, capabilityList, knowledgeBaseList] = await Promise.all([
      fetchConversations(),
      fetchCapabilities().catch(() => [] as CapabilityDefinition[]),
      fetchKnowledgeBases().catch(() => [] as KnowledgeBase[])
    ]);
    setConversations(conversationList);
    setCapabilities(capabilityList);
    setKnowledgeBases(knowledgeBaseList);
    setSelectedKnowledgeBaseId((current) => {
      if (!knowledgeBaseList.length) return null;
      if (current && knowledgeBaseList.some((item) => item.knowledgeBaseId === current)) return current;
      return knowledgeBaseList[0].knowledgeBaseId;
    });
  }

  async function reloadKnowledgeBases(nextSelectedId?: string | null) {
    const knowledgeBaseList = await fetchKnowledgeBases();
    setKnowledgeBases(knowledgeBaseList);
    setSelectedKnowledgeBaseId((current) => {
      if (!knowledgeBaseList.length) return null;
      const preferred = nextSelectedId ?? current;
      if (preferred && knowledgeBaseList.some((item) => item.knowledgeBaseId === preferred)) {
        return preferred;
      }
      return knowledgeBaseList[0].knowledgeBaseId;
    });
  }

  function showError(errorLike: unknown) {
    if (errorLike instanceof ApiError) {
      setError({ message: errorLike.message, detail: errorLike.detail.rawMessage });
      return;
    }
    setError({ message: errorLike instanceof Error ? errorLike.message : String(errorLike) });
  }

  async function handleLogin(username: string, password: string) {
    try {
      const response = await login(username, password);
      setAuthToken(response.token);
      setUser(response.user);
      setIsAdminConsole(response.user.role === 'ADMIN');
      await bootstrap();
    } catch (err) {
      showError(err);
    }
  }

  async function handleLogout() {
    try {
      await logout();
    } catch {
      // Local logout should still clear token if the server is unavailable.
    }
    setAuthToken(null);
    setUser(null);
    setConversations([]);
    setCurrentConversation(null);
    setMessages([]);
    setCurrentRun(null);
    setKnowledgeBases([]);
    setSelectedKnowledgeBaseId(null);
    setUseKnowledgeBase(false);
    setIsAdminConsole(false);
  }

  async function handleNewConversation() {
    try {
      const conversation = await createConversation();
      setCurrentConversation(conversation);
      setConversations((items) => [conversation, ...items]);
      setMessages([]);
      setCurrentRun(null);
      setPrompt('');
      setView('agent');
    } catch (err) {
      showError(err);
    }
  }

  function handleSearchNavigate() {
    setIsGlobalSearchOpen(true);
  }

  function handleHelpNavigate() {
    setView('settings');
  }

  async function openConversation(conversation: Conversation) {
    try {
      setView('agent');
      setCurrentConversation(conversation);
      setCurrentRun(null);
      const list = await fetchConversationMessages(conversation.conversationId);
      setMessages(list);
      const lastRunId = conversation.lastRunId;
      const lastRun = lastRunId ? runsById[lastRunId] : null;
      if (lastRun) setCurrentRun(lastRun);
    } catch (err) {
      showError(err);
    }
  }

  const globalSearchItems = useMemo<GlobalSearchItem[]>(() => {
    const viewItems: GlobalSearchItem[] = [
      {
        id: 'view-agent',
        type: 'view',
        label: '打开超级Agent',
        hint: '进入对话工作台',
        keywords: ['agent', '聊天', '首页', '工作台'],
        action: () => setView('agent')
      },
      {
        id: 'view-knowledge',
        type: 'view',
        label: '打开知识库',
        hint: '管理知识库与文档',
        keywords: ['knowledge', '检索', '文档', '向量'],
        action: () => setView('knowledge')
      },
      {
        id: 'view-gallery',
        type: 'view',
        label: '打开图库',
        hint: '查看图片与视频资产',
        keywords: ['gallery', '图片', '视频', '素材'],
        action: () => setView('gallery')
      },
      {
        id: 'view-settings',
        type: 'view',
        label: '打开设置',
        hint: '查看系统与运行模式',
        keywords: ['settings', '帮助', '系统', '配置'],
        action: () => setView('settings')
      }
    ];

    const conversationItems = conversations.map((conversation) => ({
      id: `conversation-${conversation.conversationId}`,
      type: 'conversation' as const,
      label: conversation.title || '未命名对话',
      hint: conversation.firstMessage || '打开对话记录',
      keywords: [conversation.title, conversation.firstMessage, conversation.memorySummary].filter(Boolean) as string[],
      action: () => {
        void openConversation(conversation);
      }
    }));

    const knowledgeItems = knowledgeBases.map((item) => ({
      id: `knowledge-${item.knowledgeBaseId}`,
      type: 'knowledge' as const,
      label: item.name,
      hint: item.description || `状态：${item.status}`,
      keywords: [item.name, item.description || '', item.status],
      action: () => {
        setView('knowledge');
        setSelectedKnowledgeBaseId(item.knowledgeBaseId);
      }
    }));

    const quickItems = QUICK_ACTIONS.map((item) => ({
      id: `quick-${item.capability}`,
      type: 'quick' as const,
      label: item.label,
      hint: item.prompt,
      keywords: [item.label, item.prompt, item.capability],
      action: () => {
        setView('agent');
        setPrompt(item.prompt);
        window.setTimeout(() => composerInputRef.current?.focus(), 0);
      }
    }));

    return [...viewItems, ...quickItems, ...knowledgeItems, ...conversationItems];
  }, [conversations, knowledgeBases]);

  const filteredGlobalSearchResults = useMemo<GlobalSearchResultEntry[]>(() => {
    const query = globalSearchQuery.trim();
    const ranked = globalSearchItems
      .map((item, index) => ({ item, index, ...scoreGlobalSearchItem(item, query, recentSearchUsage) }))
      .filter((entry) => entry.score > Number.NEGATIVE_INFINITY)
      .sort((left, right) => {
        if (right.score !== left.score) return right.score - left.score;
        return left.index - right.index;
      })
      .slice(0, 12)
      .map(({ item, score, groupScore, matchKind, recentLabel, recentBoost }) => ({
        item,
        score,
        groupScore,
        matchKind,
        recentLabel,
        recentBoost
      }));

    const topRecentIds = new Set(
      ranked
        .filter((entry) => entry.recentLabel && entry.recentBoost > 0)
        .sort((left, right) => right.recentBoost - left.recentBoost)
        .slice(0, 3)
        .map((entry) => entry.item.id)
    );

    return ranked.map((entry) => ({
      ...entry,
      recentLabel: topRecentIds.has(entry.item.id) ? entry.recentLabel : null
    }));
  }, [globalSearchItems, globalSearchQuery, recentSearchUsage]);

  useEffect(() => {
    setSelectedGlobalSearchIndex(0);
  }, [globalSearchQuery, isGlobalSearchOpen]);

  useEffect(() => {
    if (selectedGlobalSearchIndex < filteredGlobalSearchResults.length) return;
    setSelectedGlobalSearchIndex(filteredGlobalSearchResults.length ? filteredGlobalSearchResults.length - 1 : 0);
  }, [filteredGlobalSearchResults, selectedGlobalSearchIndex]);

  function handleClearRecentSearchHistory() {
    setRecentSearchUsage({});
  }

  function handleGlobalSearchSelect(item: GlobalSearchItem) {
    setRecentSearchUsage((current) => ({
      ...current,
      [item.id]: Date.now()
    }));
    item.action();
    setIsGlobalSearchOpen(false);
  }

  function handleGlobalSearchKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (!filteredGlobalSearchResults.length) {
      if (event.key === 'Enter') event.preventDefault();
      return;
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setSelectedGlobalSearchIndex((current) => (current + 1) % filteredGlobalSearchResults.length);
      return;
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault();
      setSelectedGlobalSearchIndex((current) => (current - 1 + filteredGlobalSearchResults.length) % filteredGlobalSearchResults.length);
      return;
    }

    if (event.key === 'Tab') {
      event.preventDefault();
      setSelectedGlobalSearchIndex((current) => {
        if (event.shiftKey) {
          return (current - 1 + filteredGlobalSearchResults.length) % filteredGlobalSearchResults.length;
        }
        return (current + 1) % filteredGlobalSearchResults.length;
      });
      return;
    }

    if (event.key === 'Enter') {
      event.preventDefault();
      handleGlobalSearchSelect(filteredGlobalSearchResults[selectedGlobalSearchIndex].item);
    }
  }

  async function handleSend() {
    const message = prompt.trim();
    if (!message || sending) return;
    if (useKnowledgeBase && !selectedKnowledgeBaseId) {
      setError({ message: '请先在知识库页面创建并选择一个知识库' });
      return;
    }
    setSending(true);
    setError(null);
    try {
      let conversation = currentConversation;
      if (!conversation) {
        conversation = await createConversation();
        setCurrentConversation(conversation);
        setConversations((items) => [conversation!, ...items]);
      }

      let uploadedImageId = imageAsset?.imageAssetId ?? null;
      if (imageFile && !uploadedImageId) {
        const uploaded = await uploadImage(imageFile);
        setImageAsset(uploaded);
        uploadedImageId = uploaded.imageAssetId;
      }

      const run = await createAgentRun({
        conversationId: conversation.conversationId,
        message,
        imageAssetId: uploadedImageId,
        useKnowledgeBase,
        knowledgeBaseId: useKnowledgeBase ? selectedKnowledgeBaseId : null,
        input: {}
      });

      setCurrentRun(run);
      setRunsById((items) => ({ ...items, [run.runId]: run }));
      setPrompt('');
      setImageFile(null);
      setImageAsset(null);

      const [conversationList, messageList] = await Promise.all([
        fetchConversations(),
        fetchConversationMessages(conversation.conversationId)
      ]);
      setConversations(conversationList);
      setMessages(messageList);
      setCurrentConversation(conversationList.find((item) => item.conversationId === conversation!.conversationId) ?? conversation);

    } catch (err) {
      showError(err);
    } finally {
      setSending(false);
    }
  }

  function handleImageChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null;
    setImageFile(file);
    setImageAsset(null);
    event.target.value = '';
  }

  if (!authReady) {
    return (
      <div className="login-shell">
        <ThemeToggle theme={theme} onToggle={() => setTheme((value) => (value === 'dark' ? 'light' : 'dark'))} />
        <div className="login-card">加载中...</div>
      </div>
    );
  }

  if (!user) {
    return (
      <LoginView
        error={error}
        onLogin={handleLogin}
        onCloseError={() => setError(null)}
        theme={theme}
        onToggleTheme={() => setTheme((value) => (value === 'dark' ? 'light' : 'dark'))}
      />
    );
  }

  if (user.role === 'ADMIN' && isAdminConsole) {
    return <AdminConsole user={user} onLogout={handleLogout} onEnterWorkspace={() => setIsAdminConsole(false)} />;
  }

  const selectedKnowledgeBase = knowledgeBases.find((item) => item.knowledgeBaseId === selectedKnowledgeBaseId) ?? null;

  return (
    <div className={view === 'agent' ? 'shell-frame' : 'shell-frame without-history'}>
      <header className="topbar">
        <strong className="brand">超级Agent</strong>
        <button className="new-chat" onClick={handleNewConversation}>+ 新对话</button>
        <span className="top-spacer" />
        <div className="topbar-actions" aria-label="顶部操作">
          <button className="topbar-search-trigger muted" onClick={handleSearchNavigate} type="button" aria-haspopup="dialog" aria-expanded={isGlobalSearchOpen}>
            <span>搜索</span>
            <span className="topbar-search-shortcut">Ctrl K</span>
          </button>
          <button className="topbar-action" onClick={handleHelpNavigate} type="button">帮助</button>
          {user.role === 'ADMIN' && <button className="topbar-action" onClick={() => setIsAdminConsole(true)} type="button">管理台</button>}
        </div>
        <ThemeToggle theme={theme} onToggle={() => setTheme((value) => (value === 'dark' ? 'light' : 'dark'))} />
        <button className="top-link" onClick={handleLogout} type="button">{user.username} 退出</button>
      </header>

      {isGlobalSearchOpen && (
        <GlobalSearchDialog
          query={globalSearchQuery}
          results={filteredGlobalSearchResults}
          hasRecentHistory={Object.keys(recentSearchUsage).length > 0}
          selectedIndex={selectedGlobalSearchIndex}
          inputRef={globalSearchInputRef}
          onChangeQuery={setGlobalSearchQuery}
          onClearHistory={handleClearRecentSearchHistory}
          onClose={() => setIsGlobalSearchOpen(false)}
          onInputKeyDown={handleGlobalSearchKeyDown}
          onHoverIndex={setSelectedGlobalSearchIndex}
          onSelect={handleGlobalSearchSelect}
        />
      )}

      <nav className="sidebar">
        <NavItem active={view === 'agent'} onClick={() => setView('agent')}>超级agent</NavItem>
        <NavItem active={view === 'knowledge'} onClick={() => setView('knowledge')}>知识库</NavItem>
        <NavItem active={view === 'gallery'} onClick={() => setView('gallery')}>图库</NavItem>
        <NavItem active={view === 'settings'} onClick={() => setView('settings')}>设置</NavItem>
      </nav>

      <main className="main-panel">
        {error && <ErrorBar error={error} onClose={() => setError(null)} />}
        {view === 'agent' && (
          <AgentView
            capabilities={capabilities}
            currentRun={currentRun}
            imageFile={imageFile}
            knowledgeBases={knowledgeBases}
            knowledgeBaseName={selectedKnowledgeBase?.name ?? null}
            messages={messages}
            prompt={prompt}
            sending={sending}
            selectedKnowledgeBaseId={selectedKnowledgeBaseId}
            useKnowledgeBase={useKnowledgeBase}
            onImageClick={() => imageInputRef.current?.click()}
            onPromptInputRef={composerInputRef}
            onPromptChange={setPrompt}
            onQuickPrompt={setPrompt}
            onSend={handleSend}
            onDisableKnowledge={() => setUseKnowledgeBase(false)}
            onSelectKnowledgeBase={(knowledgeBaseId) => {
              setSelectedKnowledgeBaseId(knowledgeBaseId);
              setUseKnowledgeBase(true);
            }}
          />
        )}
        {view === 'knowledge' && (
          <KnowledgeView
            knowledgeBases={knowledgeBases}
            onError={showError}
            onReloadKnowledgeBases={reloadKnowledgeBases}
            onSelectKnowledgeBase={setSelectedKnowledgeBaseId}
            selectedKnowledgeBaseId={selectedKnowledgeBaseId}
            useKnowledgeBase={useKnowledgeBase}
            onToggle={() => setUseKnowledgeBase((value) => !value)}
          />
        )}
        {view === 'gallery' && <GalleryView onError={showError} />}
        {view === 'settings' && <SettingsView onError={showError} />}
        <input ref={imageInputRef} hidden type="file" accept="image/*" onChange={handleImageChange} />
      </main>

      {view === 'agent' && (
        <ConversationPanel conversations={conversations} currentConversation={currentConversation} onOpen={openConversation} />
      )}
    </div>
  );
}

function LoginView({
  error,
  onCloseError,
  onLogin,
  onToggleTheme,
  theme
}: {
  error: ErrorState | null;
  onCloseError: () => void;
  onLogin: (username: string, password: string) => Promise<void>;
  onToggleTheme: () => void;
  theme: ThemeMode;
}) {
  const [username, setUsername] = useState('user');
  const [password, setPassword] = useState('user123');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    try {
      await onLogin(username, password);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-shell">
      <ThemeToggle theme={theme} onToggle={onToggleTheme} />
      <form className="login-card login-card-split" onSubmit={submit}>
        <section className="login-brand-panel" aria-label="product">
          <h1>超级Agent</h1>
          <p>智能 · 高效 · 专业</p>
          <span>© 2026 超级Agent</span>
        </section>
        <section className="login-form-panel">
          <h2>登录系统</h2>
          <p>欢迎回来，请登录您的账号</p>
          {error && <ErrorBar error={error} onClose={onCloseError} />}
          <label className="login-field">
            <span className="field-label">用户名</span>
            <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="请输入用户名" />
          </label>
          <label className="login-field">
            <span className="field-label">密码</span>
            <input value={password} onChange={(event) => setPassword(event.target.value)} placeholder="请输入密码" type="password" />
          </label>
          <button disabled={loading} type="submit">{loading ? '登录中...' : '登录'}</button>
          <div className="login-options">
            <label>
              <input type="checkbox" />
              <span>记住我</span>
            </label>
            <span>默认账号 user / user123</span>
          </div>
        </section>
      </form>
    </main>
  );
}

function ThemeToggle({ onToggle, theme }: { onToggle: () => void; theme: ThemeMode }) {
  const nextThemeLabel = theme === 'dark' ? '切换浅色模式' : '切换深色模式';
  return (
    <button className="theme-toggle" onClick={onToggle} type="button" aria-label={nextThemeLabel} title={nextThemeLabel}>
      <svg className="theme-toggle-icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M9 21h6" />
        <path d="M10 17h4" />
        <path d="M8.5 14.5a6 6 0 1 1 7 0c-.7.55-1.1 1.3-1.25 2.1h-4.5c-.15-.8-.55-1.55-1.25-2.1Z" />
        <path d="M12 2v2" />
        <path d="M4.9 4.9l1.4 1.4" />
        <path d="M19.1 4.9l-1.4 1.4" />
      </svg>
    </button>
  );
}

function NavItem({ active, children, onClick }: { active: boolean; children: string; onClick: () => void }) {
  return (
    <button className={active ? 'nav-item active' : 'nav-item'} onClick={onClick}>
      {children}
    </button>
  );
}

function AgentView({
  capabilities,
  currentRun,
  imageFile,
  knowledgeBases,
  knowledgeBaseName,
  messages,
  prompt,
  sending,
  selectedKnowledgeBaseId,
  useKnowledgeBase,
  onDisableKnowledge,
  onImageClick,
  onPromptInputRef,
  onPromptChange,
  onQuickPrompt,
  onSend,
  onSelectKnowledgeBase
}: {
  capabilities: CapabilityDefinition[];
  currentRun: AgentRun | null;
  imageFile: File | null;
  knowledgeBases: KnowledgeBase[];
  knowledgeBaseName: string | null;
  messages: ConversationMessage[];
  prompt: string;
  sending: boolean;
  selectedKnowledgeBaseId: string | null;
  useKnowledgeBase: boolean;
  onDisableKnowledge: () => void;
  onImageClick: () => void;
  onPromptInputRef: React.RefObject<HTMLTextAreaElement | null>;
  onPromptChange: (value: string) => void;
  onQuickPrompt: (value: string) => void;
  onSend: () => void;
  onSelectKnowledgeBase: (knowledgeBaseId: string) => void;
}) {
  const [isKnowledgeSelectorOpen, setIsKnowledgeSelectorOpen] = useState(false);
  const knowledgeSelectorRef = useRef<HTMLDivElement | null>(null);
  const hasConversation = messages.length > 0 || currentRun;
  const hasAssistantMessageForCurrentRun = Boolean(
    currentRun && messages.some((message) => message.role === 'ASSISTANT' && message.runId === currentRun.runId)
  );
  const capabilitySummary = useMemo(() => {
    if (!capabilities.length) return QUICK_ACTIONS;
    return capabilities.map((item) => ({
      label: item.name,
      capability: item.key,
      prompt: item.examplePrompt
    }));
  }, [capabilities]);

  useEffect(() => {
    if (!isKnowledgeSelectorOpen) return;

    function handlePointerDown(event: MouseEvent) {
      if (!knowledgeSelectorRef.current?.contains(event.target as Node)) {
        setIsKnowledgeSelectorOpen(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsKnowledgeSelectorOpen(false);
      }
    }

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isKnowledgeSelectorOpen]);

  function openKnowledgeSelector() {
    setIsKnowledgeSelectorOpen(true);
  }

  function selectKnowledgeBase(knowledgeBaseId: string) {
    onSelectKnowledgeBase(knowledgeBaseId);
    setIsKnowledgeSelectorOpen(false);
  }

  function disableKnowledgeBase() {
    onDisableKnowledge();
    setIsKnowledgeSelectorOpen(false);
  }

  return (
    <section className="agent-view">
      {!hasConversation ? (
        <div className="welcome">
          <h1>你好，我是超级Agent助手</h1>
          <p>你可以和我聊天，也可以生成图片/视频或检索知识库</p>
          <div className="capability-grid">
            {capabilitySummary.map((item) => (
              <button key={item.capability} onClick={() => onQuickPrompt(item.prompt)}>
                {item.label}
              </button>
            ))}
          </div>
        </div>
      ) : (
        <div className="conversation">
          {messages.map((message) => (
            <MessageCard key={message.messageId} message={message} />
          ))}
          {currentRun && !hasAssistantMessageForCurrentRun && <AssistantRunCard run={currentRun} />}
        </div>
      )}

      <div className="composer">
        <label className="visually-hidden" htmlFor="agent-message-input">消息输入框</label>
        <textarea
          id="agent-message-input"
          ref={onPromptInputRef}
          value={prompt}
          placeholder="输入消息"
          aria-label="输入发送给超级Agent的消息"
          onChange={(event) => onPromptChange(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault();
              onSend();
            }
          }}
        />
        <div className="composer-actions">
          <button onClick={onImageClick}>{imageFile ? `已选图片：${imageFile.name}` : '上传图片'}</button>
          <div className="knowledge-selector" ref={knowledgeSelectorRef}>
            <button className={useKnowledgeBase ? 'knowledge-toggle active' : 'knowledge-toggle'} onClick={openKnowledgeSelector}>
              {useKnowledgeBase ? `已引用知识库${knowledgeBaseName ? `：${knowledgeBaseName}` : ''}` : '引用知识库'}
            </button>
            {isKnowledgeSelectorOpen && (
              <div className="knowledge-selector-popover" role="dialog" aria-modal="false" aria-label="选择知识库">
                <div className="knowledge-selector-header">
                  <strong>选择知识库</strong>
                  <button type="button" onClick={() => setIsKnowledgeSelectorOpen(false)}>关闭</button>
                </div>
                {knowledgeBases.length === 0 ? (
                  <div className="knowledge-selector-empty">
                    <p>当前还没有可引用的知识库，请先到知识库页面创建。</p>
                  </div>
                ) : (
                  <div className="knowledge-selector-list" role="listbox" aria-label="知识库列表">
                    {knowledgeBases.map((item) => {
                      const isSelected = item.knowledgeBaseId === selectedKnowledgeBaseId;
                      return (
                        <button
                          key={item.knowledgeBaseId}
                          type="button"
                          className={isSelected ? 'knowledge-selector-item selected' : 'knowledge-selector-item'}
                          onClick={() => selectKnowledgeBase(item.knowledgeBaseId)}
                          role="option"
                          aria-selected={isSelected}
                        >
                          <span className="knowledge-selector-item-main">
                            <strong>{item.name}</strong>
                            <span>{item.description || '无描述'} · {item.status}</span>
                          </span>
                          {isSelected && <span className="knowledge-selector-check">当前</span>}
                        </button>
                      );
                    })}
                  </div>
                )}
                <div className="knowledge-selector-footer">
                  {useKnowledgeBase && (
                    <button type="button" className="knowledge-selector-secondary" onClick={disableKnowledgeBase}>
                      取消引用
                    </button>
                  )}
                  <button type="button" className="knowledge-selector-secondary" onClick={() => setIsKnowledgeSelectorOpen(false)}>
                    关闭
                  </button>
                </div>
              </div>
            )}
          </div>
          <button className="send-button" disabled={!prompt.trim() || sending} onClick={onSend}>
            {sending ? '发送中...' : '发送'}
          </button>
        </div>
      </div>
    </section>
  );
}

function MessageCard({ message }: { message: ConversationMessage }) {
  return (
    <article className="message">
      <span className="message-role">{message.role === 'USER' ? '我' : 'Agent'}</span>
      <div className="message-body">
        {message.capability && <span className="capability-tag">{CAPABILITY_LABELS[message.capability]}</span>}
        <p>{message.content}</p>
      </div>
    </article>
  );
}

function AssistantRunCard({ run }: { run: AgentRun }) {
  return (
    <article className="message">
      <span className="message-role">Agent</span>
      <div className="message-body">
        <span className="capability-tag">{CAPABILITY_LABELS[run.capability]}</span>
        <p>{run.finalResult || '暂无结果'}</p>
      </div>
    </article>
  );
}

function formatKnowledgeStatus(status?: string | null) {
  return (status || 'PENDING').replace(/_/g, ' ');
}

function knowledgeStatusTone(status?: string | null) {
  const normalized = (status || 'PENDING').toUpperCase();
  if (normalized.includes('ACTIVE') || normalized.includes('SUCCESS') || normalized.includes('DONE')) return 'success';
  if (normalized.includes('FAIL') || normalized.includes('ERROR')) return 'danger';
  if (normalized.includes('PROCESS') || normalized.includes('INDEX') || normalized.includes('PENDING')) return 'warning';
  return 'neutral';
}

function documentStatusTone(status?: string | null) {
  const normalized = (status || 'PENDING').toUpperCase();
  if (normalized.includes('FAIL') || normalized.includes('ERROR')) return 'danger';
  if (normalized.includes('SUCCESS') || normalized.includes('DONE') || normalized.includes('ACTIVE') || normalized.includes('READY')) return 'success';
  return 'warning';
}

function summarizeDocumentStatus(status?: string | null) {
  const tone = documentStatusTone(status);
  if (tone === 'success') return '成功';
  if (tone === 'danger') return '失败';
  return '处理中';
}

function formatFileSize(sizeBytes?: number | null) {
  if (sizeBytes == null || Number.isNaN(sizeBytes)) return '-';
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(sizeBytes < 10 * 1024 ? 1 : 0)} KB`;
  if (sizeBytes < 1024 * 1024 * 1024) return `${(sizeBytes / (1024 * 1024)).toFixed(sizeBytes < 10 * 1024 * 1024 ? 1 : 0)} MB`;
  return `${(sizeBytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function summarizeErrorReason(reason?: string | null) {
  if (!reason) return null;
  const normalized = reason.replace(/\s+/g, ' ').trim();
  if (!normalized) return null;
  return normalized.length > 88 ? `${normalized.slice(0, 88)}...` : normalized;
}

function ConversationPanel({
  conversations,
  currentConversation,
  onOpen
}: {
  conversations: Conversation[];
  currentConversation: Conversation | null;
  onOpen: (conversation: Conversation) => void;
}) {
  return (
    <aside className="conversation-panel">
      <h3>对话记录</h3>
      <div className="conversation-list">
        {conversations.length === 0 && <span className="empty-text">暂无对话记录</span>}
        {conversations.map((conversation) => (
          <button
            className={currentConversation?.conversationId === conversation.conversationId ? 'conversation-card active' : 'conversation-card'}
            key={conversation.conversationId}
            onClick={() => onOpen(conversation)}
          >
            <strong>{conversation.title}</strong>
          </button>
        ))}
      </div>
    </aside>
  );
}

function KnowledgeView(props: {
  knowledgeBases: KnowledgeBase[];
  onError: (error: unknown) => void;
  onReloadKnowledgeBases: (nextSelectedId?: string | null) => Promise<void>;
  onSelectKnowledgeBase: (knowledgeBaseId: string | null) => void;
  selectedKnowledgeBaseId: string | null;
  useKnowledgeBase: boolean;
  onToggle: () => void;
}) {
  const {
    knowledgeBases,
    onError,
    onReloadKnowledgeBases,
    onSelectKnowledgeBase,
    selectedKnowledgeBaseId,
    useKnowledgeBase,
    onToggle
  } = props;
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [showCreatePanel, setShowCreatePanel] = useState(false);
  const [deleteSelection, setDeleteSelection] = useState<Set<string>>(new Set());
  const [knowledgeBaseName, setKnowledgeBaseName] = useState('');
  const [knowledgeBaseDescription, setKnowledgeBaseDescription] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchHits, setSearchHits] = useState<KnowledgeSearchHit[]>([]);
  const [previewDocument, setPreviewDocument] = useState<KnowledgeDocument | null>(null);
  const [copiedField, setCopiedField] = useState<'documentId' | 'errorReason' | null>(null);
  const uploadInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!selectedKnowledgeBaseId) {
      setDocuments([]);
      setSearchHits([]);
      setPreviewDocument(null);
      return;
    }
    reloadDocuments(selectedKnowledgeBaseId);
  }, [selectedKnowledgeBaseId]);

  useEffect(() => {
    if (!previewDocument) return;

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setPreviewDocument(null);
        return;
      }
      if (window.innerWidth > 768 && event.key === 'ArrowLeft') {
        event.preventDefault();
        openAdjacentPreview(-1);
        return;
      }
      if (window.innerWidth > 768 && event.key === 'ArrowRight') {
        event.preventDefault();
        openAdjacentPreview(1);
      }
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [previewDocument]);

  useEffect(() => {
    if (!copiedField) return;
    const timeoutId = window.setTimeout(() => setCopiedField(null), 1600);
    return () => window.clearTimeout(timeoutId);
  }, [copiedField]);

  async function reloadDocuments(knowledgeBaseId = selectedKnowledgeBaseId) {
    if (!knowledgeBaseId) {
      setDocuments([]);
      return;
    }
    setLoading(true);
    try {
      setDocuments(await fetchKnowledgeDocuments(knowledgeBaseId));
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  async function submitKnowledgeBase(event: FormEvent) {
    event.preventDefault();
    if (!knowledgeBaseName.trim()) return;
    setCreating(true);
    try {
      const created = await createKnowledgeBase({
        name: knowledgeBaseName.trim(),
        description: knowledgeBaseDescription.trim() || null
      });
      setKnowledgeBaseName('');
      setKnowledgeBaseDescription('');
      setShowCreatePanel(false);
      await onReloadKnowledgeBases(created.knowledgeBaseId);
      onSelectKnowledgeBase(created.knowledgeBaseId);
    } catch (err) {
      onError(err);
    } finally {
      setCreating(false);
    }
  }

  async function uploadSelectedDocument(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || !selectedKnowledgeBaseId) return;
    setLoading(true);
    try {
      await uploadKnowledgeDocument(selectedKnowledgeBaseId, file);
      await reloadDocuments(selectedKnowledgeBaseId);
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  async function submitKnowledgeSearch(event: FormEvent) {
    event.preventDefault();
    if (!selectedKnowledgeBaseId || !searchQuery.trim()) return;
    setLoading(true);
    try {
      const response = await searchKnowledgeBase(selectedKnowledgeBaseId, { query: searchQuery.trim(), topK: 5 });
      setSearchHits(response.hits);
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  function toggleKnowledgeBase(knowledgeBaseId: string) {
    if (selectedKnowledgeBaseId === knowledgeBaseId) {
      onSelectKnowledgeBase(null);
      setDocuments([]);
      setSearchHits([]);
      return;
    }
    onSelectKnowledgeBase(knowledgeBaseId);
  }

  function toggleDeleteSelection(knowledgeBaseId: string, checked: boolean) {
    setDeleteSelection((current) => {
      const next = new Set(current);
      if (checked) next.add(knowledgeBaseId);
      else next.delete(knowledgeBaseId);
      return next;
    });
  }

  async function deleteSelectedKnowledgeBases() {
    if (deleteSelection.size === 0) return;
    const selectedIds = Array.from(deleteSelection);
    const names = knowledgeBases
      .filter((item) => deleteSelection.has(item.knowledgeBaseId))
      .map((item) => item.name)
      .join('、');
    if (!window.confirm(`确认删除 ${selectedIds.length} 个知识库？${names ? `\n${names}` : ''}`)) {
      return;
    }
    setLoading(true);
    try {
      for (const knowledgeBaseId of selectedIds) {
        await deleteKnowledgeBase(knowledgeBaseId);
      }
      const removedExpanded = selectedKnowledgeBaseId ? deleteSelection.has(selectedKnowledgeBaseId) : false;
      if (removedExpanded) {
        onSelectKnowledgeBase(null);
        setDocuments([]);
        setSearchHits([]);
      }
      setDeleteSelection(new Set());
      await onReloadKnowledgeBases(removedExpanded ? null : selectedKnowledgeBaseId);
    } catch (err) {
      onError(err);
      await onReloadKnowledgeBases(selectedKnowledgeBaseId);
    } finally {
      setLoading(false);
    }
  }

  function documentStatus(document: KnowledgeDocument) {
    return document.indexStatus || document.parseStatus || 'PENDING';
  }

  function openDocumentPreview(document: KnowledgeDocument) {
    setPreviewDocument(document);
  }

  const previewDocumentIndex = previewDocument ? documents.findIndex((item) => item.documentId === previewDocument.documentId) : -1;
  const hasPreviousPreview = previewDocumentIndex > 0;
  const hasNextPreview = previewDocumentIndex >= 0 && previewDocumentIndex < documents.length - 1;

  function openAdjacentPreview(direction: -1 | 1) {
    if (previewDocumentIndex < 0) return;
    const target = documents[previewDocumentIndex + direction];
    if (target) setPreviewDocument(target);
  }

  async function copyPreviewValue(kind: 'documentId' | 'errorReason', value: string | null | undefined) {
    if (!value) return;
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(value);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = value;
        textarea.setAttribute('readonly', 'true');
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      setCopiedField(kind);
    } catch {
      setCopiedField(null);
    }
  }

  return (
    <section className="knowledge-page">
      <header className="knowledge-header">
        <h1>知识库</h1>
        <div className="knowledge-actions">
          <button disabled={creating || loading} onClick={() => setShowCreatePanel((value) => !value)} type="button">新建</button>
          <button disabled={deleteSelection.size === 0 || loading} onClick={deleteSelectedKnowledgeBases} type="button">
            删除{deleteSelection.size ? ` ${deleteSelection.size}` : ''}
          </button>
        </div>
      </header>

      {showCreatePanel && (
        <form className="knowledge-create-panel" onSubmit={submitKnowledgeBase}>
          <h2>新建知识库</h2>
          <input value={knowledgeBaseName} onChange={(event) => setKnowledgeBaseName(event.target.value)} placeholder="知识库名称" />
          <input value={knowledgeBaseDescription} onChange={(event) => setKnowledgeBaseDescription(event.target.value)} placeholder="描述（可选）" />
          <div className="knowledge-actions">
            <button disabled={creating} type="submit">{creating ? '创建中...' : '创建知识库'}</button>
            <button disabled={creating} onClick={() => setShowCreatePanel(false)} type="button">取消</button>
          </div>
        </form>
      )}

      <div className="knowledge-list">
        {knowledgeBases.length === 0 && <p className="knowledge-empty">暂无知识库，请先新建。</p>}
        {knowledgeBases.map((item) => {
          const isActive = selectedKnowledgeBaseId === item.knowledgeBaseId;
          return (
            <article className={isActive ? 'knowledge-row active' : 'knowledge-row'} key={item.knowledgeBaseId}>
              <div
                className="knowledge-row-summary"
                onClick={() => toggleKnowledgeBase(item.knowledgeBaseId)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') toggleKnowledgeBase(item.knowledgeBaseId);
                }}
                role="button"
                tabIndex={0}
              >
                <div className="knowledge-row-main">
                  <strong>{item.name}</strong>
                  <span>{item.description || '无描述'}</span>
                  <span className="knowledge-row-preview">最近更新于 {formatTime(item.updatedAt || item.createdAt)}</span>
                </div>
                <div className="knowledge-row-meta">
                  <span className={`knowledge-status-pill ${knowledgeStatusTone(item.status)}`}>{formatKnowledgeStatus(item.status)}</span>
                  <span className="knowledge-doc-count">{isActive ? `文档 ${documents.length}` : '文档 -'}</span>
                </div>
                <label className="knowledge-row-check" onClick={(event) => event.stopPropagation()}>
                  <input
                    checked={deleteSelection.has(item.knowledgeBaseId)}
                    onChange={(event) => toggleDeleteSelection(item.knowledgeBaseId, event.target.checked)}
                    type="checkbox"
                  />
                </label>
              </div>

              {isActive && (
                <div className="knowledge-row-detail">
                  <div className="knowledge-actions">
                    <button disabled={loading} onClick={() => uploadInputRef.current?.click()} type="button">上传文档</button>
                    <button disabled={loading} onClick={() => reloadDocuments(item.knowledgeBaseId)} type="button">刷新文档</button>
                    <button className={useKnowledgeBase ? 'knowledge-toggle active' : 'knowledge-toggle'} onClick={onToggle} type="button">
                      {useKnowledgeBase ? '已引用到对话' : '引用到对话'}
                    </button>
                  </div>

                  <section className="knowledge-panel" aria-labelledby={`knowledge-documents-${item.knowledgeBaseId}`}>
                    <div className="knowledge-panel-header">
                      <div>
                        <h3 id={`knowledge-documents-${item.knowledgeBaseId}`}>文档列表</h3>
                        <p>查看当前知识库中的文档状态和最近更新时间。</p>
                      </div>
                      <span className="knowledge-panel-count">{documents.length} 份文档</span>
                    </div>

                    <div className="knowledge-doc-table-wrap">
                      <table className="knowledge-doc-table">
                        <thead>
                          <tr>
                            <th>文档名称</th>
                            <th>状态</th>
                            <th>大小</th>
                            <th>更新时间</th>
                            <th>操作</th>
                          </tr>
                        </thead>
                        <tbody>
                          {documents.length === 0 && (
                            <tr>
                              <td colSpan={5}>当前知识库暂无文档。</td>
                            </tr>
                          )}
                          {documents.map((document) => (
                            <tr key={document.documentId} className={documentStatusTone(documentStatus(document)) === 'danger' ? 'knowledge-doc-row failed' : undefined}>
                              <td>
                                <div className="knowledge-doc-name-cell">
                                  <strong>{document.fileName}</strong>
                                  {summarizeErrorReason(document.errorReason) && (
                                    <span className="knowledge-doc-error" title={document.errorReason || undefined}>
                                      {summarizeErrorReason(document.errorReason)}
                                    </span>
                                  )}
                                </div>
                              </td>
                              <td>
                                <span
                                  className={`knowledge-doc-status ${documentStatusTone(documentStatus(document))}`}
                                  title={formatKnowledgeStatus(documentStatus(document))}
                                >
                                  {summarizeDocumentStatus(documentStatus(document))}
                                </span>
                              </td>
                              <td>{formatFileSize(document.sizeBytes)}</td>
                              <td>{formatTime(document.updatedAt || document.createdAt)}</td>
                              <td>
                                <button className="knowledge-doc-preview" onClick={() => openDocumentPreview(document)} type="button">
                                  预览
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    <div className="knowledge-doc-cards">
                      {documents.length === 0 && <p className="knowledge-doc-empty">当前知识库暂无文档。</p>}
                      {documents.map((document) => (
                        <article className="knowledge-doc-card" key={`mobile-${document.documentId}`}>
                          <strong>{document.fileName}</strong>
                          {summarizeErrorReason(document.errorReason) && <p className="knowledge-doc-error">{summarizeErrorReason(document.errorReason)}</p>}
                          <span>
                            <b>状态</b>
                            <span
                              className={`knowledge-doc-status ${documentStatusTone(documentStatus(document))}`}
                              title={formatKnowledgeStatus(documentStatus(document))}
                            >
                              {summarizeDocumentStatus(documentStatus(document))}
                            </span>
                          </span>
                          <span><b>大小</b>{formatFileSize(document.sizeBytes)}</span>
                          <span><b>更新时间</b>{formatTime(document.updatedAt || document.createdAt)}</span>
                          <span>
                            <b>操作</b>
                            <button className="knowledge-doc-preview" onClick={() => openDocumentPreview(document)} type="button">
                              预览
                            </button>
                          </span>
                        </article>
                      ))}
                    </div>
                  </section>

                  <section className="knowledge-panel" aria-labelledby={`knowledge-search-${item.knowledgeBaseId}`}>
                    <div className="knowledge-panel-header">
                      <div>
                        <h3 id={`knowledge-search-${item.knowledgeBaseId}`}>内容检索</h3>
                        <p>输入问题，查看命中的文档片段与相似度。</p>
                      </div>
                      <span className="knowledge-panel-count">{searchHits.length} 条结果</span>
                    </div>

                    <form className="knowledge-search-form" onSubmit={submitKnowledgeSearch}>
                      <input value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} placeholder="输入问题，检索命中文档片段" />
                      <button disabled={loading || !selectedKnowledgeBaseId} type="submit">{loading ? '检索中...' : '检索'}</button>
                    </form>

                    <div className="knowledge-search-results">
                      {searchHits.length === 0 && <p>暂无检索结果。</p>}
                      {searchHits.map((hit) => (
                        <article className="knowledge-hit" key={hit.chunkId}>
                          <strong>{hit.fileName || hit.documentId}</strong>
                          <span>相似度：{hit.score.toFixed(4)}</span>
                          <span>{hit.pageNo ? `页码：${hit.pageNo}` : hit.sectionTitle || '正文'}</span>
                          <p>{hit.content}</p>
                        </article>
                      ))}
                    </div>
                  </section>
                </div>
              )}
            </article>
          );
        })}
      </div>
      <input ref={uploadInputRef} hidden type="file" accept=".txt,.md,.markdown,.pdf,.docx,image/*" onChange={uploadSelectedDocument} />
      {previewDocument && (
        <div className="knowledge-doc-drawer-backdrop" onClick={() => setPreviewDocument(null)} role="presentation">
          <aside
            className="knowledge-doc-drawer"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-label="文档详情"
          >
            <div className="knowledge-doc-drawer-header">
              <div>
                <h3>{previewDocument.fileName}</h3>
                <p>查看文档状态、大小、更新时间和处理结果。</p>
              </div>
              <div className="knowledge-doc-drawer-controls">
                <div className="knowledge-doc-drawer-nav">
                  <button className="knowledge-doc-drawer-nav-button" disabled={!hasPreviousPreview} onClick={() => openAdjacentPreview(-1)} type="button">
                    上一条
                  </button>
                  <button className="knowledge-doc-drawer-nav-button" disabled={!hasNextPreview} onClick={() => openAdjacentPreview(1)} type="button">
                    下一条
                  </button>
                </div>
                <button className="knowledge-doc-drawer-close" onClick={() => setPreviewDocument(null)} type="button">关闭</button>
              </div>
            </div>

            <div className="knowledge-doc-drawer-actions">
              <button className="knowledge-doc-drawer-action" onClick={() => copyPreviewValue('documentId', previewDocument.documentId)} type="button">
                {copiedField === 'documentId' ? '已复制文档 ID' : '复制文档 ID'}
              </button>
              {previewDocument.errorReason && (
                <button className="knowledge-doc-drawer-action" onClick={() => copyPreviewValue('errorReason', previewDocument.errorReason)} type="button">
                  {copiedField === 'errorReason' ? '已复制错误原因' : '复制错误原因'}
                </button>
              )}
            </div>

            <div className="knowledge-doc-drawer-grid">
              <div className="knowledge-doc-drawer-stat">
                <span>状态</span>
                <strong>
                  <span className={`knowledge-doc-status ${documentStatusTone(documentStatus(previewDocument))}`}>
                    {summarizeDocumentStatus(documentStatus(previewDocument))}
                  </span>
                </strong>
              </div>
              <div className="knowledge-doc-drawer-stat">
                <span>文件大小</span>
                <strong>{formatFileSize(previewDocument.sizeBytes)}</strong>
              </div>
              <div className="knowledge-doc-drawer-stat">
                <span>更新时间</span>
                <strong>{formatTime(previewDocument.updatedAt || previewDocument.createdAt)}</strong>
              </div>
              <div className="knowledge-doc-drawer-stat">
                <span>分块数量</span>
                <strong>{previewDocument.chunkCount ?? '-'}</strong>
              </div>
              <div className="knowledge-doc-drawer-stat">
                <span>文件类型</span>
                <strong>{previewDocument.fileType || '-'}</strong>
              </div>
              <div className="knowledge-doc-drawer-stat">
                <span>MIME</span>
                <strong>{previewDocument.mimeType || '-'}</strong>
              </div>
            </div>

            {previewDocument.errorReason && (
              <section className="knowledge-doc-drawer-section">
                <h4>失败原因</h4>
                <p>{previewDocument.errorReason}</p>
              </section>
            )}

            <section className="knowledge-doc-drawer-section">
              <h4>处理信息</h4>
              <dl className="knowledge-doc-drawer-meta">
                <div>
                  <dt>解析状态</dt>
                  <dd>{formatKnowledgeStatus(previewDocument.parseStatus)}</dd>
                </div>
                <div>
                  <dt>索引状态</dt>
                  <dd>{formatKnowledgeStatus(previewDocument.indexStatus)}</dd>
                </div>
                <div>
                  <dt>文档 ID</dt>
                  <dd>{previewDocument.documentId}</dd>
                </div>
              </dl>
            </section>
          </aside>
        </div>
      )}
    </section>
  );
}

function LegacyKnowledgeView({
  knowledgeBases,
  onError,
  onReloadKnowledgeBases,
  onSelectKnowledgeBase,
  selectedKnowledgeBaseId,
  useKnowledgeBase,
  onToggle
}: {
  knowledgeBases: KnowledgeBase[];
  onError: (error: unknown) => void;
  onReloadKnowledgeBases: (nextSelectedId?: string | null) => Promise<void>;
  onSelectKnowledgeBase: (knowledgeBaseId: string | null) => void;
  selectedKnowledgeBaseId: string | null;
  useKnowledgeBase: boolean;
  onToggle: () => void;
}) {
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [knowledgeBaseName, setKnowledgeBaseName] = useState('');
  const [knowledgeBaseDescription, setKnowledgeBaseDescription] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchHits, setSearchHits] = useState<KnowledgeSearchHit[]>([]);
  const uploadInputRef = useRef<HTMLInputElement>(null);
  const selectedKnowledgeBase = knowledgeBases.find((item) => item.knowledgeBaseId === selectedKnowledgeBaseId) ?? null;

  useEffect(() => {
    if (!selectedKnowledgeBaseId) {
      setDocuments([]);
      setSearchHits([]);
      return;
    }
    reloadDocuments(selectedKnowledgeBaseId);
  }, [selectedKnowledgeBaseId]);

  async function reloadDocuments(knowledgeBaseId = selectedKnowledgeBaseId) {
    if (!knowledgeBaseId) {
      setDocuments([]);
      return;
    }
    setLoading(true);
    try {
      const list = await fetchKnowledgeDocuments(knowledgeBaseId);
      setDocuments(list);
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  async function submitKnowledgeBase(event: FormEvent) {
    event.preventDefault();
    if (!knowledgeBaseName.trim()) return;
    setCreating(true);
    try {
      const created = await createKnowledgeBase({
        name: knowledgeBaseName.trim(),
        description: knowledgeBaseDescription.trim() || null,
      });
      await onReloadKnowledgeBases(created.knowledgeBaseId);
      onSelectKnowledgeBase(created.knowledgeBaseId);
      setKnowledgeBaseName('');
      setKnowledgeBaseDescription('');
    } catch (err) {
      onError(err);
    } finally {
      setCreating(false);
    }
  }

  async function uploadSelectedDocument(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || !selectedKnowledgeBaseId) return;
    setLoading(true);
    try {
      await uploadKnowledgeDocument(selectedKnowledgeBaseId, file);
      await reloadDocuments(selectedKnowledgeBaseId);
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  async function submitKnowledgeSearch(event: FormEvent) {
    event.preventDefault();
    if (!selectedKnowledgeBaseId || !searchQuery.trim()) return;
    setLoading(true);
    try {
      const response = await searchKnowledgeBase(selectedKnowledgeBaseId, { query: searchQuery.trim(), topK: 5 });
      setSearchHits(response.hits);
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="side-content">
      <h1>知识库</h1>
      <p>当前版本支持创建用户私有知识库、上传文档并进行检索测试。发送消息时，系统会把当前选中的知识库作为问答来源。</p>
      <form className="login-card" onSubmit={submitKnowledgeBase}>
        <h2>新建知识库</h2>
        <input value={knowledgeBaseName} onChange={(event) => setKnowledgeBaseName(event.target.value)} placeholder="知识库名称" />
        <input value={knowledgeBaseDescription} onChange={(event) => setKnowledgeBaseDescription(event.target.value)} placeholder="描述（可选）" />
        <button disabled={creating}>{creating ? '创建中...' : '创建知识库'}</button>
      </form>

      <div className="knowledge-snippets">
        <h2>知识库列表</h2>
        {knowledgeBases.length === 0 && <p>暂无知识库，请先创建。</p>}
        {knowledgeBases.map((item) => (
          <button
            className={selectedKnowledgeBaseId === item.knowledgeBaseId ? 'conversation-card active' : 'conversation-card'}
            key={item.knowledgeBaseId}
            onClick={() => onSelectKnowledgeBase(item.knowledgeBaseId)}
            type="button"
          >
            <strong>{item.name}</strong>
            <span>{item.description || '无描述'}</span>
          </button>
        ))}
      </div>

      <div className="knowledge-snippets">
        <h2>当前知识库</h2>
        <p>{selectedKnowledgeBase ? `${selectedKnowledgeBase.name}（${selectedKnowledgeBase.status}）` : '未选择知识库'}</p>
        <div className="gallery-actions">
          <button className={useKnowledgeBase ? 'knowledge-toggle active' : 'knowledge-toggle'} disabled={!selectedKnowledgeBaseId} onClick={onToggle} type="button">
            {useKnowledgeBase ? '发送时已引用当前知识库' : '发送时引用当前知识库'}
          </button>
          <button disabled={!selectedKnowledgeBaseId || loading} onClick={() => uploadInputRef.current?.click()} type="button">上传文档</button>
          <button disabled={!selectedKnowledgeBaseId || loading} onClick={() => reloadDocuments()} type="button">刷新文档</button>
        </div>
      </div>

      <div className="knowledge-snippets">
        <h2>文档列表</h2>
        {documents.length === 0 && <p>当前知识库暂无文档。</p>}
        {documents.map((item) => (
          <article className="gallery-card" key={item.documentId}>
            <strong>{item.fileName}</strong>
            <span>类型：{item.fileType}</span>
            <span>解析：{item.parseStatus}</span>
            <span>索引：{item.indexStatus || 'PENDING'}</span>
            <span>分块：{item.chunkCount ?? 0}</span>
            {item.errorReason && <span>错误：{item.errorReason}</span>}
          </article>
        ))}
      </div>

      <form className="login-card" onSubmit={submitKnowledgeSearch}>
        <h2>检索测试</h2>
        <input value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} placeholder="输入问题，查看命中片段" />
        <button disabled={!selectedKnowledgeBaseId || loading}>{loading ? '检索中...' : '检索当前知识库'}</button>
      </form>

      <div className="knowledge-snippets">
        <h2>检索结果</h2>
        {searchHits.length === 0 && <p>暂无检索结果。</p>}
        {!!selectedKnowledgeBaseId && !loading && searchQuery.trim() && searchHits.length === 0 && (
          <p>当前知识库没有命中片段。可以换一个问题，或先上传更相关的文档。</p>
        )}
        {searchHits.map((item) => (
          <article className="gallery-card" key={item.chunkId}>
            <strong>{item.fileName || item.documentId}</strong>
            <span>相似度：{item.score.toFixed(4)}</span>
            <span>{item.pageNo ? `页码：${item.pageNo}` : item.sectionTitle || '正文'}</span>
            <p>{item.content}</p>
          </article>
        ))}
      </div>
      <input ref={uploadInputRef} hidden type="file" accept=".txt,.md,.markdown,.pdf,.docx,image/*" onChange={uploadSelectedDocument} />
    </section>
  );
}

function GalleryView({ onError }: { onError: (error: unknown) => void }) {
  const [images, setImages] = useState<GalleryImage[]>([]);
  const [videos, setVideos] = useState<GalleryVideo[]>([]);
  const [loading, setLoading] = useState(false);
  const imageInputRef = useRef<HTMLInputElement>(null);
  const videoInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    reload();
  }, []);

  async function reload() {
    setLoading(true);
    try {
      const [imageList, videoList] = await Promise.all([fetchImages(), fetchVideos()]);
      setImages(imageList);
      setVideos(videoList);
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  async function uploadSelectedImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setLoading(true);
    try {
      await uploadImage(file);
      await reload();
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  async function uploadSelectedVideo(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setLoading(true);
    try {
      await uploadVideo(file);
      await reload();
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  async function openAsset(fileAssetId?: string | null) {
    if (!fileAssetId) return;
    try {
      const blob = await fetchAssetContentBlob(fileAssetId);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (err) {
      onError(err);
    }
  }

  return (
    <section className="gallery-view">
      <header className="gallery-header">
        <div>
          <h1>图库</h1>
          <p>保存自己上传或超级Agent生成的图片、视频记录，按创建时间倒序展示。</p>
        </div>
        <div className="gallery-actions">
          <button disabled={loading} onClick={() => imageInputRef.current?.click()}>上传图片</button>
          <button disabled={loading} onClick={() => videoInputRef.current?.click()}>上传视频</button>
          <button disabled={loading} onClick={reload}>刷新</button>
        </div>
      </header>
      <GallerySection title="图片库" emptyText="暂无图片">
        {images.map((item) => (
          <GalleryCard key={item.imageId} title={item.prompt || item.fileAssetId} source={item.source || 'UPLOAD'} createdAt={item.createdAt} onOpen={item.source === 'UPLOAD' ? () => openAsset(item.fileAssetId) : undefined} />
        ))}
      </GallerySection>
      <GallerySection title="视频库" emptyText="暂无视频">
        {videos.map((item) => (
          <GalleryCard key={item.videoId} title={item.prompt || item.fileAssetId || item.videoId} source={item.status} createdAt={item.createdAt} onOpen={item.status === 'UPLOADED' && item.fileAssetId ? () => openAsset(item.fileAssetId) : undefined} />
        ))}
      </GallerySection>
      <input ref={imageInputRef} hidden type="file" accept="image/*" onChange={uploadSelectedImage} />
      <input ref={videoInputRef} hidden type="file" accept="video/*" onChange={uploadSelectedVideo} />
    </section>
  );
}

function GallerySection({ children, emptyText, title }: { children: ReactNode; emptyText: string; title: string }) {
  const items = Array.isArray(children) ? children.filter(Boolean) : children;
  const isEmpty = Array.isArray(items) ? items.length === 0 : !items;
  return (
    <section className="gallery-section">
      <h2>{title}</h2>
      {isEmpty ? <p className="gallery-empty">{emptyText}</p> : <div className="gallery-grid">{items}</div>}
    </section>
  );
}

function GalleryCard({ createdAt, onOpen, source, title }: { createdAt: string; onOpen?: () => void; source: string; title: string }) {
  return (
    <article className="gallery-card">
      <strong>{title}</strong>
      {onOpen && <button onClick={onOpen}>打开文件</button>}
      <span>来源：{source}</span>
      <span>时间：{formatTime(createdAt)}</span>
    </article>
  );
}

function SettingsView({ onError }: { onError: (error: unknown) => void }) {
  return (
    <section className="side-content">
      <h1>设置</h1>
      <p>当前为本地原型模式，文字沟通、视频创作、图片创作和知识库检索均由本地 Ollama qwen3:4b 生成文本产物。</p>
      <p>前端只调用 Java 后端，Java 再调用 Python Agent 服务；前端不会直接访问 Python 或 Ollama。</p>
      <div className="settings-grid">
        <article className="settings-card">
          <strong>本地模型模式</strong>
          <span>Ollama qwen3:4b 负责文本产物。</span>
        </article>
        <article className="settings-card">
          <strong>Java 后端</strong>
          <span>统一鉴权、会话、文件和知识库接口。</span>
        </article>
        <article className="settings-card">
          <strong>Python Agent</strong>
          <span>处理 Agent 运行、知识库索引和检索。</span>
        </article>
        <article className="settings-card">
          <strong>上传与存储</strong>
          <span>限制上传大小并保存真实文件资产。</span>
        </article>
      </div>
      <AiProviderSettings onError={onError} />
    </section>
  );
}

function GlobalSearchDialog({
  hasRecentHistory,
  inputRef,
  onChangeQuery,
  onClearHistory,
  onClose,
  onHoverIndex,
  onInputKeyDown,
  onSelect,
  query,
  results,
  selectedIndex
}: {
  hasRecentHistory: boolean;
  inputRef: React.RefObject<HTMLInputElement | null>;
  onChangeQuery: (value: string) => void;
  onClearHistory: () => void;
  onClose: () => void;
  onHoverIndex: (index: number) => void;
  onInputKeyDown: (event: React.KeyboardEvent<HTMLInputElement>) => void;
  onSelect: (item: GlobalSearchItem) => void;
  query: string;
  results: GlobalSearchResultEntry[];
  selectedIndex: number;
}) {
  const resultsRef = useRef<HTMLDivElement>(null);
  const [isConfirmingClear, setIsConfirmingClear] = useState(false);
  const [clearCountdownMs, setClearCountdownMs] = useState(0);
  const groupedResults = useMemo(() => {
    const groups = new Map<GlobalSearchItem['type'], { type: GlobalSearchItem['type']; label: string; items: Array<{ result: GlobalSearchResultEntry; index: number }> }>();

    results.forEach((result, index) => {
      const existing = groups.get(result.item.type);
      if (existing) {
        existing.items.push({ result, index });
        return;
      }

      groups.set(result.item.type, {
        type: result.item.type,
        label: globalSearchGroupLabel(result.item.type),
        items: [{ result, index }]
      });
    });

    return Array.from(groups.values()).map((group) => ({
      ...group,
      items: [...group.items].sort((left, right) => {
        if (right.result.groupScore !== left.result.groupScore) {
          return right.result.groupScore - left.result.groupScore;
        }
        return left.index - right.index;
      })
    }));
  }, [results]);

  useEffect(() => {
    const active = resultsRef.current?.querySelector<HTMLElement>(`[data-search-index="${selectedIndex}"]`);
    active?.scrollIntoView({ block: 'nearest' });
  }, [selectedIndex, results.length]);

  useEffect(() => {
    setIsConfirmingClear(false);
  }, [query, results.length]);

  useEffect(() => {
    if (!isConfirmingClear) return;
    const start = Date.now();
    const timeoutMs = 2500;
    setClearCountdownMs(timeoutMs);

    const intervalId = window.setInterval(() => {
      const remaining = Math.max(0, timeoutMs - (Date.now() - start));
      setClearCountdownMs(remaining);
      if (remaining === 0) {
        setIsConfirmingClear(false);
      }
    }, 100);

    return () => window.clearInterval(intervalId);
  }, [isConfirmingClear]);

  useEffect(() => {
    if (isConfirmingClear) return;
    setClearCountdownMs(0);
  }, [isConfirmingClear]);

  function handleClearClick() {
    if (!isConfirmingClear) {
      setIsConfirmingClear(true);
      return;
    }
    onClearHistory();
    setIsConfirmingClear(false);
  }

  const clearConfirmProgress = isConfirmingClear ? Math.max(0, Math.min(100, (clearCountdownMs / 2500) * 100)) : 0;

  return (
    <div className="global-search-backdrop" onClick={onClose} role="presentation">
      <section className="global-search-dialog" onClick={(event) => event.stopPropagation()} role="dialog" aria-modal="true" aria-label="全局搜索">
        <div className="global-search-header">
          <input
            ref={inputRef}
            className="global-search-input"
            value={query}
            onChange={(event) => onChangeQuery(event.target.value)}
            onKeyDown={onInputKeyDown}
            placeholder="搜索页面、知识库、会话和快捷动作"
            aria-label="全局搜索输入框"
            aria-activedescendant={results[selectedIndex] ? `global-search-option-${results[selectedIndex].item.id}` : undefined}
          />
          <button className="global-search-close" onClick={onClose} type="button">关闭</button>
        </div>
        <div className="global-search-meta">
          <div className="global-search-hint">可搜索：导航、快捷动作、知识库、历史对话</div>
          {hasRecentHistory && (
            <div className="global-search-clear-wrap">
              <button
                className={isConfirmingClear ? 'global-search-clear confirming' : 'global-search-clear'}
                onClick={handleClearClick}
                type="button"
                style={isConfirmingClear ? { background: `linear-gradient(90deg, #fecaca ${clearConfirmProgress}%, #fee2e2 ${clearConfirmProgress}%)` } : undefined}
              >
                {isConfirmingClear ? '确认清除' : '清除历史'}
              </button>
            </div>
          )}
        </div>
        <div className="global-search-results" ref={resultsRef} role="listbox" aria-label="全局搜索结果">
          {results.length === 0 && <p className="global-search-empty">没有匹配结果，换个关键词试试。</p>}
          {groupedResults.map((group) => (
            <section key={group.type} className="global-search-group" aria-label={group.label}>
              <div className="global-search-group-title">{group.label}</div>
              <div className="global-search-group-list">
                {group.items.map(({ result, index }) => (
                  (() => {
                    const labelMatch = findBestTextMatch(result.item.label, query);
                    const hintMatch = labelMatch ? null : findBestTextMatch(result.item.hint, query);

                    return (
                  <button
                    key={result.item.id}
                    id={`global-search-option-${result.item.id}`}
                    data-search-index={index}
                    className={index === selectedIndex ? 'global-search-result selected' : 'global-search-result'}
                    onClick={() => onSelect(result.item)}
                    onMouseEnter={() => onHoverIndex(index)}
                    type="button"
                    role="option"
                    aria-selected={index === selectedIndex}
                  >
                    <span className={`global-search-type ${result.item.type}`}>{globalSearchTypeLabel(result.item.type)}</span>
                    <span className="global-search-copy">
                      <span className="global-search-copy-top">
                        <strong>{renderHighlightedText(result.item.label, labelMatch)}</strong>
                        {result.matchKind && result.matchKind !== 'recent' && <span className={`global-search-match-kind ${result.matchKind}`}>{globalSearchMatchKindLabel(result.matchKind)}</span>}
                      </span>
                      <span>
                        {renderHighlightedText(result.item.hint, hintMatch)}
                        {result.recentLabel && <em className="global-search-recency"> · {result.recentLabel}</em>}
                      </span>
                    </span>
                  </button>
                    );
                  })()
                ))}
              </div>
            </section>
          ))}
        </div>
      </section>
    </div>
  );
}

function globalSearchGroupLabel(type: GlobalSearchItem['type']) {
  switch (type) {
    case 'view':
      return '页面';
    case 'quick':
      return '快捷动作';
    case 'knowledge':
      return '知识库';
    case 'conversation':
      return '对话';
    default:
      return '结果';
  }
}

function renderHighlightedText(text: string, match: SearchMatch | null): ReactNode {
  if (!match) return text;

  const start = match.index;
  const end = start + match.length;
  return (
    <>
      {start > 0 && <span>{text.slice(0, start)}</span>}
      <mark className="global-search-highlight">{text.slice(start, end)}</mark>
      {end < text.length && <span>{text.slice(end)}</span>}
    </>
  );
}

function escapeForRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

interface SearchMatch {
  index: number;
  length: number;
  score: number;
  kind: Exclude<GlobalSearchMatchKind, 'recent' | null>;
}

function findBestTextMatch(text: string, query: string): SearchMatch | null {
  const normalizedQuery = query.trim();
  if (!normalizedQuery) return null;

  const normalizedText = text.toLowerCase();
  const terms = Array.from(new Set(normalizedQuery.toLowerCase().split(/\s+/).map((term) => term.trim()).filter(Boolean)));

  return terms.reduce<SearchMatch | null>((best, term) => {
    const index = normalizedText.indexOf(term);
    if (index < 0) return best;

    const kind = normalizedText === term ? 'exact' : normalizedText.startsWith(term) ? 'prefix' : 'contains';
    const kindBoost = kind === 'exact' ? 3000 : kind === 'prefix' ? 2000 : 1000;
    const score = kindBoost + (1000 - index) + term.length * 8;
    if (!best || score > best.score) {
      return { index, length: term.length, score, kind };
    }
    return best;
  }, null);
}

function scoreGlobalSearchItem(item: GlobalSearchItem, query: string, recentUsage: Record<string, number>) {
  const normalizedQuery = query.trim();
  const recentTimestamp = recentUsage[item.id];
  const recentBoost = getRecentUsageBoost(recentTimestamp);
  const recentLabel = getRecentUsageLabel(recentTimestamp);

  if (!normalizedQuery) {
    return {
      score: recentBoost || defaultTypePriority(item.type),
      groupScore: defaultTypePriority(item.type) + recentBoost,
      matchKind: null as GlobalSearchMatchKind,
      recentLabel,
      recentBoost
    };
  }

  const labelMatch = findBestTextMatch(item.label, normalizedQuery);
  const hintMatch = findBestTextMatch(item.hint, normalizedQuery);
  const keywordMatch = item.keywords
    .map((keyword) => findBestTextMatch(keyword, normalizedQuery))
    .find(Boolean) ?? null;

  if (!labelMatch && !hintMatch && !keywordMatch) {
    return {
      score: Number.NEGATIVE_INFINITY,
      groupScore: Number.NEGATIVE_INFINITY,
      matchKind: null as GlobalSearchMatchKind,
      recentLabel,
      recentBoost
    };
  }

  const baseScore = (labelMatch ? 1200 + labelMatch.score : 0)
    + (hintMatch ? 500 + hintMatch.score : 0)
    + (keywordMatch ? 240 + keywordMatch.score : 0)
    + defaultTypePriority(item.type);

  return {
    score: baseScore,
    groupScore: baseScore + recentBoost,
    matchKind: labelMatch?.kind ?? hintMatch?.kind ?? keywordMatch?.kind ?? null,
    recentLabel,
    recentBoost
  };
}

function getRecentUsageBoost(timestamp?: number) {
  if (!timestamp) return 0;
  const ageHours = (Date.now() - timestamp) / 3600000;
  return Math.max(0, 180 - ageHours * 6);
}

function getRecentUsageLabel(timestamp?: number) {
  if (!timestamp) return null;
  const ageMs = Date.now() - timestamp;
  if (ageMs < 30 * 60 * 1000) return '刚刚使用';
  if (ageMs < 24 * 60 * 60 * 1000) return '今天使用';
  if (ageMs < 7 * 24 * 60 * 60 * 1000) return '近期使用';
  return '较早使用';
}

function defaultTypePriority(type: GlobalSearchItem['type']) {
  switch (type) {
    case 'view':
      return 40;
    case 'quick':
      return 30;
    case 'knowledge':
      return 20;
    case 'conversation':
      return 10;
    default:
      return 0;
  }
}

function globalSearchMatchKindLabel(kind: Exclude<GlobalSearchMatchKind, null>) {
  switch (kind) {
    case 'exact':
      return '精确命中';
    case 'prefix':
      return '前缀命中';
    case 'contains':
      return '包含命中';
    case 'recent':
      return '最近使用';
    default:
      return '';
  }
}

function globalSearchTypeLabel(type: GlobalSearchItem['type']) {
  switch (type) {
    case 'view':
      return '页面';
    case 'conversation':
      return '对话';
    case 'knowledge':
      return '知识库';
    case 'quick':
      return '快捷';
    default:
      return '结果';
  }
}

function ErrorBar({ error, onClose }: { error: ErrorState; onClose: () => void }) {
  return (
    <div className="error-bar">
      <strong>{error.message}</strong>
      {error.detail && <span>{error.detail}</span>}
      <button onClick={onClose}>关闭</button>
    </div>
  );
}

function formatTime(value: string) {
  if (!value) return '--';
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

createRoot(document.getElementById('root')!).render(<App />);
