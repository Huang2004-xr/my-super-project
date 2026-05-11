export type Capability = 'TEXT_CHAT' | 'VIDEO_CREATION' | 'IMAGE_CREATION' | 'KNOWLEDGE_RETRIEVAL';

export interface CapabilityDefinition {
  key: Capability;
  name: string;
  description: string;
  examplePrompt: string;
}

export interface CreateAgentRunRequest {
  conversationId?: string | null;
  message: string;
  imageAssetId?: string | null;
  useKnowledgeBase: boolean;
  knowledgeBaseId?: string | null;
  capabilityHint?: Capability | null;
  input: Record<string, unknown>;
}

export interface ImageAsset {
  imageAssetId: string;
  fileName: string;
  contentType: string;
}

export interface FileAsset {
  fileAssetId: string;
  userId: string;
  assetType: 'IMAGE' | 'VIDEO' | 'DOCUMENT' | 'OTHER';
  fileName: string;
  contentType?: string | null;
  storagePath?: string | null;
  sizeBytes?: number | null;
  createdAt: string;
}

export interface KnowledgeBase {
  knowledgeBaseId: string;
  userId: string;
  name: string;
  description?: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeDocument {
  documentId: string;
  knowledgeBaseId: string;
  userId: string;
  fileAssetId?: string | null;
  fileName: string;
  sizeBytes?: number | null;
  fileType: string;
  mimeType?: string | null;
  parseStatus: string;
  indexStatus?: string | null;
  chunkCount?: number | null;
  contentDigest?: string | null;
  errorReason?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface KnowledgeSearchHit {
  chunkId: string;
  documentId: string;
  chunkIndex: number;
  content: string;
  score: number;
  fileName?: string | null;
  pageNo?: number | null;
  sectionTitle?: string | null;
  metadata?: Record<string, unknown>;
}

export interface KnowledgeSearchResponse {
  userId: string;
  knowledgeBaseId: string;
  query: string;
  hits: KnowledgeSearchHit[];
}

export interface GalleryImage {
  imageId: string;
  userId: string;
  fileAssetId: string;
  prompt?: string | null;
  source?: 'UPLOAD' | 'GENERATED' | string | null;
  width?: number | null;
  height?: number | null;
  createdAt: string;
}

export interface GalleryVideo {
  videoId: string;
  userId: string;
  fileAssetId?: string | null;
  prompt?: string | null;
  script?: string | null;
  durationSeconds?: number | null;
  status: string;
  createdAt: string;
}

export interface AgentStep {
  stepId: string;
  name: string;
  status: string;
  summary: string;
}

export interface ToolCall {
  toolName: string;
  input: Record<string, unknown>;
  output: Record<string, unknown>;
  status: string;
  error?: string;
}

export interface TraceEvent {
  eventType: string;
  message: string;
  timestamp: string;
}

export interface Artifact {
  type: string;
  title: string;
  data: Record<string, unknown>;
}

export interface AgentRun {
  runId: string;
  capability: Capability;
  message: string;
  imageAssetId?: string | null;
  useKnowledgeBase: boolean;
  knowledgeBaseId?: string | null;
  routeReason: string;
  status: string;
  steps: AgentStep[];
  toolCalls: ToolCall[];
  traces: TraceEvent[];
  artifacts: Artifact[];
  finalResult: string;
  createdAt: string;
  updatedAt: string;
}

export interface Conversation {
  conversationId: string;
  title: string;
  firstMessage?: string | null;
  lastRunId?: string | null;
  memorySummary?: string | null;
  memoryUpdatedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface User {
  userId: string;
  username: string;
  email?: string | null;
  phone?: string | null;
  role: 'USER' | 'ADMIN';
  status: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface ConversationMessage {
  messageId: string;
  conversationId: string;
  userId: string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  capability?: Capability | null;
  runId?: string | null;
  createdAt: string;
}
