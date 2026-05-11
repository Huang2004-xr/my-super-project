import type {
  AgentRun,
  AuthResponse,
  CapabilityDefinition,
  Conversation,
  ConversationMessage,
  CreateAgentRunRequest,
  FileAsset,
  GalleryImage,
  GalleryVideo,
  ImageAsset,
  KnowledgeBase,
  KnowledgeDocument,
  KnowledgeSearchResponse,
  TraceEvent,
  User
} from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8620';
const TOKEN_KEY = 'super_agent_token';

export interface ApiErrorDetail {
  status?: number;
  url?: string;
  rawMessage?: string;
  stack?: string;
}

export class ApiError extends Error {
  code: string;
  detail: ApiErrorDetail;
  severe: boolean;

  constructor(message: string, code: string, detail: ApiErrorDetail = {}, severe = false) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.detail = detail;
    this.severe = severe;
  }
}

function isPythonAgentUnavailable(rawMessage: string) {
  return rawMessage.includes('Python Agent 服务未启动')
    || rawMessage.includes('Connection refused');
}

function isPythonAgentTimeout(rawMessage: string) {
  return rawMessage.includes('Python Agent 执行超时')
    || rawMessage.includes('Read timed out')
    || rawMessage.includes('SocketTimeoutException');
}

function isKnowledgeInfraUnavailable(rawMessage: string) {
  return rawMessage.includes('知识库向量服务不可用')
    || rawMessage.includes('Milvus')
    || rawMessage.includes('vectorStore');
}

function isOcrUnavailable(rawMessage: string) {
  return rawMessage.includes('图片 OCR 不可用')
    || rawMessage.includes('Tesseract');
}

function isScannedPdfUnsupported(rawMessage: string) {
  return rawMessage.includes('扫描版 PDF')
    || rawMessage.includes('pdf text extraction returned empty result');
}

function httpMessage(status: number, rawMessage = '') {
  if (isKnowledgeInfraUnavailable(rawMessage)) {
    return '知识库向量服务不可用，请先检查独立 Milvus 服务和 Python Agent 健康状态';
  }
  if (isOcrUnavailable(rawMessage)) {
    return '图片 OCR 不可用，请先安装 Tesseract 并加入 PATH';
  }
  if (isScannedPdfUnsupported(rawMessage)) {
    return '当前原型暂不支持扫描版 PDF 自动 OCR，请先上传可复制文本的 PDF，或先转成图片/文本后再上传';
  }
  if (isPythonAgentTimeout(rawMessage)) {
    return 'Python Agent 执行超时，请稍后重试或检查本地 Ollama 模型是否仍在生成';
  }
  if (isPythonAgentUnavailable(rawMessage)) {
    return 'Python Agent 服务未启动，请先启动 agent-python 的 uvicorn 服务';
  }
  if (status === 404) return '请求的接口不存在，请检查 API 路径';
  if (status >= 500) return '服务器内部错误，请稍后重试或查看后端日志';
  return '请求失败，请稍后重试';
}

export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (error instanceof DOMException && error.name === 'AbortError') {
    return new ApiError('请求超时，请检查网络或稍后重试', 'TIMEOUT', { rawMessage: error.message, stack: error.stack }, true);
  }
  if (error instanceof TypeError) {
    const rawMessage = error.message || '';
    const looksLikeCors = rawMessage.toLowerCase().includes('cors');
    return new ApiError(
      looksLikeCors ? '跨域请求被阻止，请检查后端 CORS 配置' : '网络请求失败，请检查后端服务是否正常运行',
      looksLikeCors ? 'CORS' : 'NETWORK',
      { rawMessage, stack: error.stack },
      true
    );
  }
  if (error instanceof Error) {
    return new ApiError(error.message || '请求失败，请稍后重试', 'UNKNOWN', { rawMessage: error.message, stack: error.stack });
  }
  return new ApiError('请求失败，请稍后重试', 'UNKNOWN', { rawMessage: String(error) });
}

export async function request<T>(path: string, options: RequestInit = {}, timeoutMs = 15000): Promise<T> {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs);
  const url = `${API_BASE}${path}`;
  const isFormData = options.body instanceof FormData;

  try {
    const token = localStorage.getItem(TOKEN_KEY);
    const headers = isFormData
      ? { ...(options.headers || {}) }
      : { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (token) {
      (headers as Record<string, string>).Authorization = `Bearer ${token}`;
    }
    const response = await fetch(url, {
      headers,
      ...options,
      signal: controller.signal
    });

    if (!response.ok) {
      const text = await response.text();
      let rawMessage = text || response.statusText;
      try {
        const parsed = JSON.parse(text);
        rawMessage = parsed.message || parsed.error || parsed.detail || rawMessage;
      } catch {
        // Keep raw body for developer detail.
      }
      throw new ApiError(httpMessage(response.status, rawMessage), `HTTP_${response.status}`, {
        status: response.status,
        url,
        rawMessage
      }, response.status >= 500);
    }

    const responseText = await response.text();
    return (responseText ? JSON.parse(responseText) : undefined) as T;
  } catch (error) {
    const normalized = normalizeApiError(error);
    if (!normalized.detail.url) normalized.detail.url = url;
    throw normalized;
  } finally {
    window.clearTimeout(timeoutId);
  }
}

export function setAuthToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

export const login = (username: string, password: string) =>
  request<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  });

export const logout = () =>
  request<void>('/api/auth/logout', {
    method: 'POST'
  });

export const fetchMe = () => request<User>('/api/auth/me');

export const fetchCapabilities = () => request<CapabilityDefinition[]>('/api/capabilities');

export const createAgentRun = (payload: CreateAgentRunRequest) =>
  request<AgentRun>('/api/agent-runs', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, 130000);

export const fetchAgentRuns = () => request<AgentRun[]>('/api/agent-runs');

export const fetchAgentRun = (runId: string) => request<AgentRun>(`/api/agent-runs/${encodeURIComponent(runId)}`);

export const fetchTraces = (runId: string) => request<TraceEvent[]>(`/api/traces/${encodeURIComponent(runId)}`);

export const createConversation = () =>
  request<Conversation>('/api/conversations', {
    method: 'POST'
  });

export const fetchConversations = () => request<Conversation[]>('/api/conversations');

export const fetchConversation = (conversationId: string) =>
  request<Conversation>(`/api/conversations/${encodeURIComponent(conversationId)}`);

export const fetchConversationMessages = (conversationId: string) =>
  request<ConversationMessage[]>(`/api/conversations/${encodeURIComponent(conversationId)}/messages`);

export const uploadImage = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return request<ImageAsset>('/api/assets/images', {
    method: 'POST',
    body: form
  }, 30000);
};

export const fetchImages = () => request<GalleryImage[]>('/api/assets/images');

export const fetchVideos = () => request<GalleryVideo[]>('/api/assets/videos');

export const fetchFiles = (assetType: 'IMAGE' | 'VIDEO') =>
  request<FileAsset[]>(`/api/assets/files/${encodeURIComponent(assetType)}`);

export async function fetchAssetContentBlob(fileAssetId: string): Promise<Blob> {
  const response = await fetch(`${API_BASE}/api/assets/files/${encodeURIComponent(fileAssetId)}/content`, {
    headers: localStorage.getItem(TOKEN_KEY)
      ? { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` }
      : {}
  });
  if (!response.ok) {
    throw new ApiError(httpMessage(response.status, response.statusText), `HTTP_${response.status}`, {
      status: response.status,
      rawMessage: response.statusText
    }, response.status >= 500);
  }
  return response.blob();
}

export const fetchKnowledgeBases = () => request<KnowledgeBase[]>('/api/knowledge-bases');

export const createKnowledgeBase = (payload: { name: string; description?: string | null }) =>
  request<KnowledgeBase>('/api/knowledge-bases', {
    method: 'POST',
    body: JSON.stringify(payload)
  });

export const deleteKnowledgeBase = (knowledgeBaseId: string) =>
  request<void>(`/api/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}`, {
    method: 'DELETE'
  });

export const fetchKnowledgeDocuments = (knowledgeBaseId: string) =>
  request<KnowledgeDocument[]>(`/api/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/documents`);

export const uploadKnowledgeDocument = (knowledgeBaseId: string, file: File) => {
  const form = new FormData();
  form.append('file', file);
  return request<KnowledgeDocument>(`/api/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/documents/upload`, {
    method: 'POST',
    body: form
  }, 180000);
};

export const searchKnowledgeBase = (knowledgeBaseId: string, payload: { query: string; topK?: number }) =>
  request<KnowledgeSearchResponse>(`/api/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/search`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, 30000);

export const uploadVideo = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return request<void>('/api/assets/videos/upload', {
    method: 'POST',
    body: form
  }, 30000);
};
