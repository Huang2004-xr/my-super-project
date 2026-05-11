# 管理端前端路由与后台接口草案

## 1. 目标边界

这份草案基于当前项目的实际结构展开：

- 前端仍是单页原型，核心逻辑集中在 `frontend/src/main.tsx`
- 后端已有普通用户域接口，以及一个最小管理员入口 `/api/admin/users`
- 管理端应建立在同一套登录态和数据模型之上，不单独起新服务

管理端第一期只解决三件事：

1. 管理员登录后有独立入口和导航
2. 管理员可以跨用户查看平台核心资源
3. 管理员可以排查运行、知识库、文件和账号问题

---

## 2. 前端路由结构

建议从当前基于本地 `view` 状态切换，升级为两套路由区：

- 普通用户区：保留现有 Agent 工作台
- 管理区：新增 `/admin/*`

建议路由：

```text
/
/login
/app
/app/agent
/app/knowledge
/app/gallery
/app/settings
/admin
/admin/dashboard
/admin/users
/admin/users/:userId
/admin/runs
/admin/runs/:runId
/admin/knowledge-bases
/admin/knowledge-bases/:knowledgeBaseId
/admin/assets
/admin/providers
/admin/conversations
/admin/conversations/:conversationId
/admin/audits
```

路由守卫规则：

- 未登录：统一跳转 `/login`
- 已登录且 `role=USER`：只能访问 `/app/*`
- 已登录且 `role=ADMIN`：允许访问 `/app/*` 和 `/admin/*`
- 访问无权限页面时跳回各自首页

建议跳转策略：

- 登录成功后如果 `role=ADMIN`，默认进入 `/admin/dashboard`
- 登录成功后如果 `role=USER`，默认进入 `/app/agent`

---

## 3. 前端目录结构

建议先把当前 `frontend/src/main.tsx` 逐步拆成下面的目录结构：

```text
frontend/src/
  app/
    App.tsx
    router.tsx
    providers/
      AuthProvider.tsx
      AppBootstrapProvider.tsx
  api/
    client.ts
    auth.ts
    agent.ts
    resources.ts
    admin.ts
  components/
    layout/
      AppLayout.tsx
      AdminLayout.tsx
      Sidebar.tsx
      Header.tsx
    feedback/
      ErrorBanner.tsx
      EmptyState.tsx
      LoadingBlock.tsx
    tables/
      DataTable.tsx
      StatusTag.tsx
    forms/
      SearchForm.tsx
      FilterBar.tsx
  features/
    auth/
      pages/
        LoginPage.tsx
      hooks/
        useAuth.ts
      guards/
        RequireAuth.tsx
        RequireAdmin.tsx
    workspace/
      pages/
        AgentPage.tsx
        KnowledgePage.tsx
        GalleryPage.tsx
        SettingsPage.tsx
      components/
        ConversationList.tsx
        PromptComposer.tsx
        RunResultPanel.tsx
    admin/
      pages/
        DashboardPage.tsx
        UserListPage.tsx
        UserDetailPage.tsx
        RunListPage.tsx
        RunDetailPage.tsx
        KnowledgeBaseListPage.tsx
        KnowledgeBaseDetailPage.tsx
        AssetListPage.tsx
        ProviderListPage.tsx
        ConversationListPage.tsx
        ConversationDetailPage.tsx
        AuditLogPage.tsx
      components/
        DashboardSummaryCards.tsx
        UserOverviewPanel.tsx
        RunTracePanel.tsx
        KnowledgeDocumentTable.tsx
        AssetPreviewDrawer.tsx
      hooks/
        useAdminDashboard.ts
        useAdminUsers.ts
        useAdminRuns.ts
        useAdminKnowledgeBases.ts
        useAdminAssets.ts
        useAdminProviders.ts
        useAdminConversations.ts
        useAdminAudits.ts
  types/
    common.ts
    auth.ts
    workspace.ts
    admin.ts
  styles/
    tokens.css
    base.css
    layout.css
    admin.css
  main.tsx
```

### 3.1 拆分原则

- `app/` 只放入口、全局 provider、路由
- `api/` 只放请求封装，不放页面逻辑
- `features/workspace/` 保留现有用户工作台功能
- `features/admin/` 单独承载后台页面和 hooks
- `components/` 只放跨模块复用组件
- `types/admin.ts` 专门放管理端聚合 DTO

### 3.2 第一阶段最小页面集

如果你先做最小可用后台，建议只先落这 6 个页面：

1. `DashboardPage.tsx`
2. `UserListPage.tsx`
3. `RunListPage.tsx`
4. `RunDetailPage.tsx`
5. `KnowledgeBaseListPage.tsx`
6. `AssetListPage.tsx`

这 6 个页面足够支撑管理员日常排查。

---

## 4. 前端页面职责清单

### 4.1 DashboardPage

用途：展示平台级总览。

页面模块：

- 统计卡片：用户数、知识库数、文档数、运行数、失败数
- 服务状态：Java 健康状态、Python Agent 健康状态
- 最近失败运行
- 最近新增用户
- 最近上传文档

### 4.2 UserListPage

用途：管理用户账号。

列表列建议：

- 用户名
- 角色
- 状态
- 邮箱
- 电话
- 创建时间
- 最近登录时间

支持：

- 关键字搜索
- 角色筛选
- 状态筛选
- 查看详情

### 4.3 UserDetailPage

用途：查看用户综合概览。

模块建议：

- 基本信息
- 运行统计
- 知识库统计
- 文件统计
- 最近运行
- 最近知识库

### 4.4 RunListPage

用途：全局查看 Agent 运行记录。

列表列建议：

- runId
- username
- capability
- status
- useKnowledgeBase
- knowledgeBaseName
- createdAt
- updatedAt

支持：

- 按用户筛选
- 按能力筛选
- 按状态筛选
- 按时间范围筛选

### 4.5 RunDetailPage

用途：排查一次运行的完整上下文。

标签建议：

- 基本信息
- 步骤
- Tool Calls
- Traces
- Final Result

### 4.6 KnowledgeBaseListPage

用途：全局查看所有知识库。

列表列建议：

- knowledgeBaseId
- name
- username
- status
- documentCount
- createdAt
- updatedAt

### 4.7 KnowledgeBaseDetailPage

用途：查看某个知识库与文档状态。

模块建议：

- 基础信息
- 文档列表
- 索引状态统计
- 失败文档清单

### 4.8 AssetListPage

用途：统一查看图片、视频、文档资产。

页签建议：

- Images
- Videos
- Files

### 4.9 ProviderListPage

用途：查看所有用户的 AI Provider 配置。

列表列建议：

- providerId
- name
- username
- baseUrl
- modelName
- enabled
- apiKeySet
- updatedAt

### 4.10 ConversationListPage / ConversationDetailPage

用途：查看会话与消息，定位上下文问题。

### 4.11 AuditLogPage

用途：查看后台操作留痕。

---

## 5. 管理端前端类型定义建议

建议在 `frontend/src/types/admin.ts` 中增加：

```ts
export interface AdminDashboardSummary {
  userCount: number;
  adminCount: number;
  knowledgeBaseCount: number;
  documentCount: number;
  runCount24h: number;
  failedRunCount24h: number;
  imageCount: number;
  videoCount: number;
}

export interface AdminHealthStatus {
  status: string;
  service: string;
  agent?: {
    status: string;
    message?: string;
  };
}

export interface AdminDashboardResponse {
  summary: AdminDashboardSummary;
  health: AdminHealthStatus;
  recentFailedRuns: AdminRunListItem[];
  recentUsers: AdminUserListItem[];
  recentDocuments: AdminKnowledgeDocumentListItem[];
  recentAudits: AdminAuditLogItem[];
}

export interface AdminUserListItem {
  userId: string;
  username: string;
  email?: string | null;
  phone?: string | null;
  role: 'USER' | 'ADMIN';
  status: string;
  createdAt: string;
  updatedAt: string;
  lastLoginAt?: string | null;
}

export interface AdminUserOverview {
  user: AdminUserListItem;
  conversationCount: number;
  runCount: number;
  knowledgeBaseCount: number;
  documentCount: number;
  imageCount: number;
  videoCount: number;
  providerCount: number;
  lastRunAt?: string | null;
}

export interface AdminRunListItem {
  runId: string;
  userId: string;
  username: string;
  conversationId?: string | null;
  capability: string;
  status: string;
  useKnowledgeBase: boolean;
  knowledgeBaseId?: string | null;
  knowledgeBaseName?: string | null;
  message: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminKnowledgeBaseListItem {
  knowledgeBaseId: string;
  userId: string;
  username: string;
  name: string;
  description?: string | null;
  status: string;
  documentCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminKnowledgeDocumentListItem {
  documentId: string;
  knowledgeBaseId: string;
  userId: string;
  username: string;
  fileAssetId?: string | null;
  fileName: string;
  fileType: string;
  mimeType?: string | null;
  parseStatus: string;
  indexStatus?: string | null;
  chunkCount?: number | null;
  sizeBytes?: number | null;
  errorReason?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface AdminFileAssetListItem {
  fileAssetId: string;
  userId: string;
  username: string;
  assetType: 'IMAGE' | 'VIDEO' | 'DOCUMENT' | 'OTHER';
  fileName: string;
  contentType?: string | null;
  storagePath?: string | null;
  sizeBytes?: number | null;
  createdAt: string;
}

export interface AdminAiProviderListItem {
  providerId: string;
  userId: string;
  username: string;
  name: string;
  baseUrl: string;
  modelName?: string | null;
  enabled: boolean;
  apiKeySet: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminConversationListItem {
  conversationId: string;
  userId: string;
  username: string;
  title: string;
  firstMessage?: string | null;
  lastRunId?: string | null;
  memorySummary?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminAuditLogItem {
  auditId: string;
  userId?: string | null;
  username?: string | null;
  action: string;
  targetType?: string | null;
  targetId?: string | null;
  ipAddress?: string | null;
  createdAt: string;
}
```

---

## 6. 后端 DTO 草案

建议新增目录：

```text
backend-java/src/main/java/com/example/agentplatform/dto/admin/
  AdminDashboardResponse.java
  AdminSummaryDto.java
  AdminUserListItemDto.java
  AdminUserOverviewDto.java
  AdminRunListItemDto.java
  AdminRunQuery.java
  AdminKnowledgeBaseListItemDto.java
  AdminKnowledgeDocumentListItemDto.java
  AdminFileAssetListItemDto.java
  AdminAiProviderListItemDto.java
  AdminConversationListItemDto.java
  AdminAuditLogItemDto.java
  AdminPageResponse.java
  UpdateUserStatusRequest.java
  UpdateUserRoleRequest.java
```

### 6.1 通用分页 DTO

```java
package com.example.agentplatform.dto.admin;

import java.util.List;

public class AdminPageResponse<T> {
    public List<T> items;
    public long total;
    public int page;
    public int size;
}
```

### 6.2 仪表盘汇总 DTO

```java
package com.example.agentplatform.dto.admin;

public class AdminSummaryDto {
    public long userCount;
    public long adminCount;
    public long knowledgeBaseCount;
    public long documentCount;
    public long runCount24h;
    public long failedRunCount24h;
    public long imageCount;
    public long videoCount;
}
```

```java
package com.example.agentplatform.dto.admin;

import java.util.List;
import java.util.Map;

public class AdminDashboardResponse {
    public AdminSummaryDto summary;
    public Map<String, Object> health;
    public List<AdminRunListItemDto> recentFailedRuns;
    public List<AdminUserListItemDto> recentUsers;
    public List<AdminKnowledgeDocumentListItemDto> recentDocuments;
    public List<AdminAuditLogItemDto> recentAudits;
}
```

### 6.3 用户 DTO

```java
package com.example.agentplatform.dto.admin;

import java.time.Instant;

public class AdminUserListItemDto {
    public String userId;
    public String username;
    public String email;
    public String phone;
    public String role;
    public String status;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant lastLoginAt;
}
```

```java
package com.example.agentplatform.dto.admin;

import java.time.Instant;

public class AdminUserOverviewDto {
    public String userId;
    public String username;
    public String email;
    public String phone;
    public String role;
    public String status;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant lastLoginAt;
    public long conversationCount;
    public long runCount;
    public long knowledgeBaseCount;
    public long documentCount;
    public long imageCount;
    public long videoCount;
    public long providerCount;
    public Instant lastRunAt;
}
```

```java
package com.example.agentplatform.dto.admin;

public class UpdateUserStatusRequest {
    public String status;
}
```

```java
package com.example.agentplatform.dto.admin;

public class UpdateUserRoleRequest {
    public String role;
}
```

### 6.4 运行记录 DTO

```java
package com.example.agentplatform.dto.admin;

import java.time.Instant;

public class AdminRunListItemDto {
    public String runId;
    public String userId;
    public String username;
    public String conversationId;
    public String capability;
    public String status;
    public boolean useKnowledgeBase;
    public String knowledgeBaseId;
    public String knowledgeBaseName;
    public String message;
    public String routeReason;
    public Instant createdAt;
    public Instant updatedAt;
}
```

```java
package com.example.agentplatform.dto.admin;

public class AdminRunQuery {
    public String keyword;
    public String userId;
    public String capability;
    public String status;
    public Boolean useKnowledgeBase;
    public Integer page;
    public Integer size;
}
```

### 6.5 知识库 DTO

```java
package com.example.agentplatform.dto.admin;

import java.time.Instant;

public class AdminKnowledgeBaseListItemDto {
    public String knowledgeBaseId;
    public String userId;
    public String username;
    public String name;
    public String description;
    public String status;
    public long documentCount;
    public Instant createdAt;
    public Instant updatedAt;
}
```

```java
package com.example.agentplatform.dto.admin;

import java.time.Instant;

public class AdminKnowledgeDocumentListItemDto {
    public String documentId;
    public String knowledgeBaseId;
    public String userId;
    public String username;
    public String fileAssetId;
    public String fileName;
    public String fileType;
    public String mimeType;
    public String parseStatus;
    public String indexStatus;
    public Integer chunkCount;
    public Long sizeBytes;
    public String errorReason;
    public Instant createdAt;
    public Instant updatedAt;
}
```

### 6.6 文件与 Provider DTO

```java
package com.example.agentplatform.dto.admin;

import java.time.Instant;

public class AdminFileAssetListItemDto {
    public String fileAssetId;
    public String userId;
    public String username;
    public String assetType;
    public String fileName;
    public String contentType;
    public String storagePath;
    public Long sizeBytes;
    public Instant createdAt;
}
```

```java
package com.example.agentplatform.dto.admin;

import java.time.Instant;

public class AdminAiProviderListItemDto {
    public String providerId;
    public String userId;
    public String username;
    public String name;
    public String baseUrl;
    public String modelName;
    public boolean enabled;
    public boolean apiKeySet;
    public Instant createdAt;
    public Instant updatedAt;
}
```

### 6.7 会话与审计 DTO

```java
package com.example.agentplatform.dto.admin;

import java.time.Instant;

public class AdminConversationListItemDto {
    public String conversationId;
    public String userId;
    public String username;
    public String title;
    public String firstMessage;
    public String lastRunId;
    public String memorySummary;
    public Instant createdAt;
    public Instant updatedAt;
}
```

```java
package com.example.agentplatform.dto.admin;

import java.time.Instant;

public class AdminAuditLogItemDto {
    public String auditId;
    public String userId;
    public String username;
    public String action;
    public String targetType;
    public String targetId;
    public String ipAddress;
    public Instant createdAt;
}
```

---

## 7. Controller 草案

建议不要把所有管理员逻辑继续塞进现有 `AdminController`，而是拆成多个资源型 Controller。

建议目录：

```text
backend-java/src/main/java/com/example/agentplatform/controller/admin/
  AdminDashboardController.java
  AdminUserController.java
  AdminRunController.java
  AdminKnowledgeBaseController.java
  AdminAssetController.java
  AdminProviderController.java
  AdminConversationController.java
  AdminAuditController.java
```

### 7.1 AdminDashboardController

```java
package com.example.agentplatform.controller.admin;

import com.example.agentplatform.dto.admin.AdminDashboardResponse;
import com.example.agentplatform.service.AdminQueryService;
import com.example.agentplatform.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AuthService authService;
    private final AdminQueryService adminQueryService;

    public AdminDashboardController(AuthService authService, AdminQueryService adminQueryService) {
        this.authService = authService;
        this.adminQueryService = adminQueryService;
    }

    @GetMapping
    public AdminDashboardResponse dashboard(HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminQueryService.getDashboard();
    }
}
```

### 7.2 AdminUserController

```java
package com.example.agentplatform.controller.admin;

import com.example.agentplatform.dto.admin.AdminPageResponse;
import com.example.agentplatform.dto.admin.AdminUserListItemDto;
import com.example.agentplatform.dto.admin.AdminUserOverviewDto;
import com.example.agentplatform.dto.admin.UpdateUserRoleRequest;
import com.example.agentplatform.dto.admin.UpdateUserStatusRequest;
import com.example.agentplatform.service.AdminUserService;
import com.example.agentplatform.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AuthService authService;
    private final AdminUserService adminUserService;

    public AdminUserController(AuthService authService, AdminUserService adminUserService) {
        this.authService = authService;
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public AdminPageResponse<AdminUserListItemDto> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminUserService.list(keyword, role, status, page, size);
    }

    @GetMapping("/{userId}")
    public AdminUserOverviewDto detail(@PathVariable String userId, HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminUserService.getOverview(userId);
    }

    @PutMapping("/{userId}/status")
    public AdminUserOverviewDto updateStatus(
            @PathVariable String userId,
            @RequestBody UpdateUserStatusRequest body,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminUserService.updateStatus(userId, body.status);
    }

    @PutMapping("/{userId}/role")
    public AdminUserOverviewDto updateRole(
            @PathVariable String userId,
            @RequestBody UpdateUserRoleRequest body,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminUserService.updateRole(userId, body.role);
    }
}
```

### 7.3 AdminRunController

```java
package com.example.agentplatform.controller.admin;

import com.example.agentplatform.dto.admin.AdminPageResponse;
import com.example.agentplatform.dto.admin.AdminRunListItemDto;
import com.example.agentplatform.model.AgentRun;
import com.example.agentplatform.model.TraceEvent;
import com.example.agentplatform.service.AdminRunService;
import com.example.agentplatform.service.AuthService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agent-runs")
public class AdminRunController {
    private final AuthService authService;
    private final AdminRunService adminRunService;

    public AdminRunController(AuthService authService, AdminRunService adminRunService) {
        this.authService = authService;
        this.adminRunService = adminRunService;
    }

    @GetMapping
    public AdminPageResponse<AdminRunListItemDto> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String capability,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean useKnowledgeBase,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminRunService.list(keyword, userId, capability, status, useKnowledgeBase, page, size);
    }

    @GetMapping("/{runId}")
    public AgentRun detail(@PathVariable String runId, HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminRunService.getRun(runId);
    }

    @GetMapping("/{runId}/traces")
    public List<TraceEvent> traces(@PathVariable String runId, HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminRunService.getTraces(runId);
    }
}
```

### 7.4 AdminKnowledgeBaseController

```java
package com.example.agentplatform.controller.admin;

import com.example.agentplatform.dto.admin.AdminKnowledgeBaseListItemDto;
import com.example.agentplatform.dto.admin.AdminKnowledgeDocumentListItemDto;
import com.example.agentplatform.dto.admin.AdminPageResponse;
import com.example.agentplatform.service.AdminKnowledgeBaseService;
import com.example.agentplatform.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge-bases")
public class AdminKnowledgeBaseController {
    private final AuthService authService;
    private final AdminKnowledgeBaseService adminKnowledgeBaseService;

    public AdminKnowledgeBaseController(AuthService authService, AdminKnowledgeBaseService adminKnowledgeBaseService) {
        this.authService = authService;
        this.adminKnowledgeBaseService = adminKnowledgeBaseService;
    }

    @GetMapping
    public AdminPageResponse<AdminKnowledgeBaseListItemDto> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminKnowledgeBaseService.list(keyword, status, userId, page, size);
    }

    @GetMapping("/{knowledgeBaseId}/documents")
    public AdminPageResponse<AdminKnowledgeDocumentListItemDto> documents(
            @PathVariable String knowledgeBaseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminKnowledgeBaseService.documents(knowledgeBaseId, page, size);
    }

    @PostMapping("/{knowledgeBaseId}/reindex")
    public void reindex(@PathVariable String knowledgeBaseId, HttpServletRequest request) {
        authService.requireAdmin(request);
        adminKnowledgeBaseService.reindex(knowledgeBaseId);
    }
}
```

### 7.5 AdminAssetController

```java
package com.example.agentplatform.controller.admin;

import com.example.agentplatform.dto.admin.AdminFileAssetListItemDto;
import com.example.agentplatform.dto.admin.AdminPageResponse;
import com.example.agentplatform.service.AdminAssetService;
import com.example.agentplatform.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/assets")
public class AdminAssetController {
    private final AuthService authService;
    private final AdminAssetService adminAssetService;

    public AdminAssetController(AuthService authService, AdminAssetService adminAssetService) {
        this.authService = authService;
        this.adminAssetService = adminAssetService;
    }

    @GetMapping("/files")
    public AdminPageResponse<AdminFileAssetListItemDto> files(
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminAssetService.listFiles(assetType, userId, page, size);
    }

    @GetMapping("/files/{fileAssetId}/content")
    public ResponseEntity<Resource> content(@PathVariable String fileAssetId, HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminAssetService.fileContent(fileAssetId);
    }

    @DeleteMapping("/files/{fileAssetId}")
    public void delete(@PathVariable String fileAssetId, HttpServletRequest request) {
        authService.requireAdmin(request);
        adminAssetService.deleteFile(fileAssetId);
    }
}
```

### 7.6 AdminProviderController

```java
package com.example.agentplatform.controller.admin;

import com.example.agentplatform.dto.admin.AdminAiProviderListItemDto;
import com.example.agentplatform.dto.admin.AdminPageResponse;
import com.example.agentplatform.service.AdminProviderService;
import com.example.agentplatform.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-providers")
public class AdminProviderController {
    private final AuthService authService;
    private final AdminProviderService adminProviderService;

    public AdminProviderController(AuthService authService, AdminProviderService adminProviderService) {
        this.authService = authService;
        this.adminProviderService = adminProviderService;
    }

    @GetMapping
    public AdminPageResponse<AdminAiProviderListItemDto> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminProviderService.list(keyword, userId, enabled, page, size);
    }
}
```

### 7.7 AdminConversationController

```java
package com.example.agentplatform.controller.admin;

import com.example.agentplatform.dto.admin.AdminConversationListItemDto;
import com.example.agentplatform.dto.admin.AdminPageResponse;
import com.example.agentplatform.model.ConversationMessageDto;
import com.example.agentplatform.service.AdminConversationService;
import com.example.agentplatform.service.AuthService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/conversations")
public class AdminConversationController {
    private final AuthService authService;
    private final AdminConversationService adminConversationService;

    public AdminConversationController(AuthService authService, AdminConversationService adminConversationService) {
        this.authService = authService;
        this.adminConversationService = adminConversationService;
    }

    @GetMapping
    public AdminPageResponse<AdminConversationListItemDto> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminConversationService.list(keyword, userId, page, size);
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessageDto> messages(@PathVariable String conversationId, HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminConversationService.messages(conversationId);
    }
}
```

### 7.8 AdminAuditController

```java
package com.example.agentplatform.controller.admin;

import com.example.agentplatform.dto.admin.AdminAuditLogItemDto;
import com.example.agentplatform.dto.admin.AdminPageResponse;
import com.example.agentplatform.service.AdminAuditService;
import com.example.agentplatform.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {
    private final AuthService authService;
    private final AdminAuditService adminAuditService;

    public AdminAuditController(AuthService authService, AdminAuditService adminAuditService) {
        this.authService = authService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public AdminPageResponse<AdminAuditLogItemDto> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminAuditService.list(action, userId, targetType, page, size);
    }
}
```

---

## 8. 推荐服务层拆分

为避免 Controller 直接堆查询逻辑，建议同时补一个管理域 service 层：

```text
backend-java/src/main/java/com/example/agentplatform/service/
  AdminQueryService.java
  AdminUserService.java
  AdminRunService.java
  AdminKnowledgeBaseService.java
  AdminAssetService.java
  AdminProviderService.java
  AdminConversationService.java
  AdminAuditService.java
```

职责建议：

- `AdminQueryService`：仪表盘聚合
- `AdminUserService`：用户管理和聚合统计
- `AdminRunService`：全局 run 查询与 trace 查询
- `AdminKnowledgeBaseService`：知识库和文档查询、重建索引
- `AdminAssetService`：文件、图片、视频查询与删除
- `AdminProviderService`：平台级 Provider 查询
- `AdminConversationService`：全局会话与消息查询
- `AdminAuditService`：审计日志查询和写入

---

## 9. 第一阶段实现顺序

建议按下面顺序落地，成本最低：

1. 前端先引入路由和登录态守卫
2. 后端新增 `AdminDashboardController`
3. 后端新增 `AdminRunController`
4. 后端新增 `AdminKnowledgeBaseController`
5. 前端落 `DashboardPage`、`RunListPage`、`RunDetailPage`
6. 前端补 `UserListPage`、`KnowledgeBaseListPage`、`AssetListPage`

这样可以最快拿到一个能用的管理端，而不是先做一套很重的用户编辑和审计系统。

---

## 10. 与当前项目直接对应的改造点

当前代码中的直接对应关系：

- 认证与管理员鉴权：`AuthService.requireAdmin`
- 管理员现有入口：`/api/admin/users`
- 普通用户运行记录：`AgentController`
- 普通用户知识库、文件、Provider：`ResourceController`
- 普通用户会话：`ConversationController`

因此管理端不是新造概念，而是对现有域做管理员视角扩展。

下一步如果要正式开工，建议先做：

1. 新增 `frontend/src/app/router.tsx`
2. 新增 `frontend/src/features/admin/pages/DashboardPage.tsx`
3. 新增 `backend-java/src/main/java/com/example/agentplatform/controller/admin/AdminDashboardController.java`
4. 新增 `backend-java/src/main/java/com/example/agentplatform/dto/admin/*`
