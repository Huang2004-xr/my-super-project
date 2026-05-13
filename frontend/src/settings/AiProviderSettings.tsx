import { FormEvent, ReactNode, useEffect, useMemo, useState } from 'react';
import {
  createAiProvider,
  deleteAiProvider,
  fetchAiProviders,
  fetchEffectiveAiProviders,
  testAiProvider,
  updateAiProvider
} from '../api';
import type {
  AiProvider,
  AiProviderApiFormat,
  AiProviderCapability,
  AiProviderRequest
} from '../types';

const CAPABILITY_OPTIONS: Array<{ value: Exclude<AiProviderCapability, 'ALL'>; label: string }> = [
  { value: 'TEXT_CHAT', label: '聊天' },
  { value: 'IMAGE_CREATION', label: '图片创作' },
  { value: 'VIDEO_CREATION', label: '视频创作' },
  { value: 'KNOWLEDGE_RETRIEVAL', label: '知识库问答' }
];

const API_FORMAT_LABELS: Record<AiProviderApiFormat, string> = {
  openai_chat_completions: 'OpenAI Chat Completions',
  openai_responses: 'OpenAI Responses API',
  anthropic_messages: 'Anthropic Messages'
};

interface ProviderFormState {
  providerId?: string;
  name: string;
  remark: string;
  officialUrl: string;
  apiKey: string;
  baseUrl: string;
  apiFormat: AiProviderApiFormat;
  authHeaderName: string;
  useAllCapabilities: boolean;
  capabilities: Array<Exclude<AiProviderCapability, 'ALL'>>;
  defaultModel: string;
  chatModel: string;
  imageModel: string;
  videoModel: string;
  knowledgeModel: string;
  configJson: string;
  testModel: string;
  testPrompt: string;
  testTimeoutSeconds: string;
  testDegradeMs: string;
  proxyUrl: string;
  proxyUsername: string;
  proxyPassword: string;
  costMultiplier: string;
  billingMode: string;
  enabled: boolean;
  apiKeySet: boolean;
}

const EMPTY_FORM: ProviderFormState = {
  name: '',
  remark: '',
  officialUrl: '',
  apiKey: '',
  baseUrl: '',
  apiFormat: 'openai_chat_completions',
  authHeaderName: 'Authorization',
  useAllCapabilities: true,
  capabilities: ['TEXT_CHAT', 'IMAGE_CREATION', 'VIDEO_CREATION', 'KNOWLEDGE_RETRIEVAL'],
  defaultModel: '',
  chatModel: '',
  imageModel: '',
  videoModel: '',
  knowledgeModel: '',
  configJson: '{\n  "env": {},\n  "includeCoAuthoredBy": false\n}',
  testModel: '',
  testPrompt: 'Who are you?',
  testTimeoutSeconds: '45',
  testDegradeMs: '6000',
  proxyUrl: '',
  proxyUsername: '',
  proxyPassword: '',
  costMultiplier: '',
  billingMode: 'inherit',
  enabled: true,
  apiKeySet: false
};

export function AiProviderSettings({ onError }: { onError: (error: unknown) => void }) {
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [effective, setEffective] = useState<Record<string, { providerType: string; name: string; model?: string }> | null>(null);
  const [loading, setLoading] = useState(false);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [form, setForm] = useState<ProviderFormState | null>(null);
  const [jsonManuallyEdited, setJsonManuallyEdited] = useState(false);
  const [openAdvanced, setOpenAdvanced] = useState<Record<string, boolean>>({
    json: true,
    test: false,
    proxy: false,
    billing: false
  });

  useEffect(() => {
    void reload();
  }, []);

  useEffect(() => {
    if (!form || jsonManuallyEdited) return;
    setForm((current) => current ? { ...current, configJson: buildAutoConfigJson(current) } : current);
  }, [
    form?.name,
    form?.remark,
    form?.officialUrl,
    form?.baseUrl,
    form?.apiFormat,
    form?.authHeaderName,
    form?.useAllCapabilities,
    form?.capabilities.join(','),
    form?.defaultModel,
    form?.chatModel,
    form?.imageModel,
    form?.videoModel,
    form?.knowledgeModel,
    form?.testModel,
    form?.testPrompt,
    form?.testTimeoutSeconds,
    form?.testDegradeMs,
    form?.proxyUrl,
    form?.proxyUsername,
    form?.proxyPassword,
    form?.costMultiplier,
    form?.billingMode,
    jsonManuallyEdited
  ]);

  const modeLabel = useMemo(() => {
    if (!providers.some((item) => item.enabled)) return '本地 Ollama';
    const enabled = providers.filter((item) => item.enabled);
    const hasAll = enabled.some((item) => item.capabilities.includes('ALL'));
    return hasAll && enabled.length === 1 ? '外部 API' : '混合配置';
  }, [providers]);

  async function reload() {
    setLoading(true);
    try {
      const [providerList, effectiveMap] = await Promise.all([
        fetchAiProviders(),
        fetchEffectiveAiProviders().catch(() => null)
      ]);
      setProviders(providerList);
      setEffective(effectiveMap);
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  function openCreate() {
    setJsonManuallyEdited(false);
    setForm({ ...EMPTY_FORM, configJson: buildAutoConfigJson(EMPTY_FORM) });
    setOpenAdvanced({ json: true, test: false, proxy: false, billing: false });
  }

  function openEdit(provider: AiProvider) {
    const providerCapabilities = provider.capabilities.filter((item) => item !== 'ALL') as Array<Exclude<AiProviderCapability, 'ALL'>>;
    setJsonManuallyEdited(Boolean(provider.configJson));
    const nextForm = {
      ...EMPTY_FORM,
      providerId: provider.providerId,
      name: provider.name,
      remark: provider.remark || '',
      officialUrl: provider.officialUrl || '',
      baseUrl: provider.baseUrl,
      apiFormat: provider.apiFormat || 'openai_chat_completions',
      authHeaderName: provider.authHeaderName || defaultAuthHeader(provider.apiFormat),
      useAllCapabilities: provider.capabilities.includes('ALL'),
      capabilities: providerCapabilities.length ? providerCapabilities : EMPTY_FORM.capabilities,
      defaultModel: provider.defaultModel || provider.modelName || '',
      chatModel: provider.chatModel || '',
      imageModel: provider.imageModel || '',
      videoModel: provider.videoModel || '',
      knowledgeModel: provider.knowledgeModel || '',
      configJson: provider.configJson || EMPTY_FORM.configJson,
      enabled: provider.enabled,
      apiKeySet: provider.apiKeySet
    };
    setForm({
      ...nextForm,
      configJson: provider.configJson || buildAutoConfigJson(nextForm)
    });
    setOpenAdvanced({ json: true, test: false, proxy: false, billing: false });
  }

  async function submitForm(event: FormEvent) {
    event.preventDefault();
    if (!form) return;
    try {
      const payload = toPayload(form);
      if (form.providerId) {
        await updateAiProvider(form.providerId, payload);
      } else {
        await createAiProvider(payload);
      }
      setForm(null);
      await reload();
    } catch (err) {
      onError(err);
    }
  }

  async function handleDelete(provider: AiProvider) {
    if (!window.confirm(`删除供应商「${provider.name}」？`)) return;
    try {
      await deleteAiProvider(provider.providerId);
      await reload();
    } catch (err) {
      onError(err);
    }
  }

  async function handleToggle(provider: AiProvider) {
    try {
      await updateAiProvider(provider.providerId, {
        ...providerToRequest(provider),
        enabled: !provider.enabled
      });
      await reload();
    } catch (err) {
      onError(err);
    }
  }

  async function handleTest(provider: AiProvider) {
    setTestingId(provider.providerId);
    try {
      await testAiProvider(provider.providerId);
      await reload();
    } catch (err) {
      onError(err);
    } finally {
      setTestingId(null);
    }
  }

  return (
    <section className="ai-provider-settings">
      <header className="ai-provider-head">
        <div>
          <p className="settings-kicker">AI Provider</p>
          <h2>外部 API Key</h2>
          <p>前端只保存配置到 Java 后端，API Key 加密写入数据库。未配置外部供应商时继续使用本地 Ollama。</p>
        </div>
        <div className="ai-provider-actions">
          <span className={`ai-mode-pill ${modeLabel === '本地 Ollama' ? 'local' : 'external'}`}>{modeLabel}</span>
          <button type="button" onClick={openCreate}>添加供应商</button>
        </div>
      </header>

      <div className="effective-grid">
        {CAPABILITY_OPTIONS.map((item) => (
          <article key={item.value} className="effective-tile">
            <span>{item.label}</span>
            <strong>{effective?.[item.value]?.name || 'Local Ollama'}</strong>
            <small>{effective?.[item.value]?.model || 'qwen3:4b / fallback'}</small>
          </article>
        ))}
      </div>

      <div className="provider-list">
        {providers.length === 0 && (
          <div className="provider-empty">
            <strong>还没有外部供应商</strong>
            <span>添加一个 OpenAI-compatible API 后，聊天、图片提示词、视频脚本和知识库问答就可以不依赖本地 Ollama。</span>
          </div>
        )}
        {providers.map((provider) => (
          <article className="provider-row" key={provider.providerId}>
            <div>
              <div className="provider-title-line">
                <strong>{provider.name}</strong>
                <span className={provider.enabled ? 'status-pill on' : 'status-pill off'}>{provider.enabled ? '启用' : '禁用'}</span>
                {provider.apiKeySet && <span className="status-pill key">Key 已保存</span>}
              </div>
              <span>{API_FORMAT_LABELS[provider.apiFormat] || provider.apiFormat}</span>
              <small>{provider.baseUrl}</small>
              <div className="provider-tags">{capabilityLabels(provider.capabilities).map((label) => <em key={label}>{label}</em>)}</div>
              {provider.lastTestMessage && <p className="provider-test-message">{provider.lastTestStatus}: {provider.lastTestMessage}</p>}
            </div>
            <div className="provider-row-actions">
              <button type="button" onClick={() => handleTest(provider)} disabled={testingId === provider.providerId}>{testingId === provider.providerId ? '测试中' : '测试'}</button>
              <button type="button" onClick={() => openEdit(provider)}>编辑</button>
              <button type="button" onClick={() => handleToggle(provider)}>{provider.enabled ? '禁用' : '启用'}</button>
              <button type="button" className="danger" onClick={() => handleDelete(provider)}>删除</button>
            </div>
          </article>
        ))}
      </div>

      {loading && <p className="provider-loading">加载配置中...</p>}

      {form && (
        <div className="provider-editor-shell" role="dialog" aria-modal="true" aria-label={form.providerId ? '编辑供应商' : '添加供应商'}>
          <form className="provider-editor" onSubmit={submitForm}>
            <header className="provider-editor-top">
              <button type="button" className="provider-back" onClick={() => setForm(null)} aria-label="返回">‹</button>
              <h2>{form.providerId ? '编辑供应商' : '添加新供应商'}</h2>
            </header>

            <div className="provider-editor-body">
              <div className="provider-form-grid two">
                <Field label="供应商名称">
                  <input value={form.name} onChange={(event) => setFormValue('name', event.target.value)} placeholder="例如：OpenAI 官方" required />
                </Field>
                <Field label="备注">
                  <input value={form.remark} onChange={(event) => setFormValue('remark', event.target.value)} placeholder="例如：公司专用账号" />
                </Field>
              </div>

              <Field label="官网链接">
                <input value={form.officialUrl} onChange={(event) => setFormValue('officialUrl', event.target.value)} placeholder="https://example.com（可选）" />
              </Field>

              <Field label="API Key">
                <input
                  value={form.apiKey}
                  onChange={(event) => setFormValue('apiKey', event.target.value)}
                  placeholder={form.apiKeySet ? '已保存；留空则不修改' : '只需要填这里，后端会加密保存'}
                  type="password"
                  required={!form.providerId && !form.apiKeySet}
                />
              </Field>

              <Field label="请求地址" sideLabel="管理与测试">
                <input value={form.baseUrl} onChange={(event) => setFormValue('baseUrl', event.target.value)} placeholder="https://your-api-endpoint.com" required />
              </Field>
              <div className="provider-warning">填写兼容 OpenAI 的服务端点，不要以斜杠结尾；如果已经包含 /v1 也可以直接填写。</div>

              <div className="provider-form-grid two">
                <Field label="API 格式">
                  <select value={form.apiFormat} onChange={(event) => handleApiFormatChange(event.target.value as AiProviderApiFormat)}>
                    <option value="openai_chat_completions">OpenAI Chat Completions</option>
                    <option value="openai_responses">OpenAI Responses API</option>
                    <option value="anthropic_messages">Anthropic Messages</option>
                  </select>
                </Field>
                <Field label="认证字段">
                  <select value={form.authHeaderName} onChange={(event) => setFormValue('authHeaderName', event.target.value)}>
                    <option value="Authorization">Authorization: Bearer</option>
                    <option value="x-api-key">x-api-key</option>
                  </select>
                </Field>
              </div>

              <section className="capability-picker">
                <div className="capability-picker-head">
                  <strong>适用能力</strong>
                  <label className="switch-line">
                    <input type="checkbox" checked={form.useAllCapabilities} onChange={(event) => setFormValue('useAllCapabilities', event.target.checked)} />
                    <span>用于全部能力</span>
                  </label>
                </div>
                {!form.useAllCapabilities && (
                  <div className="capability-checks">
                    {CAPABILITY_OPTIONS.map((item) => (
                      <label key={item.value}>
                        <input
                          type="checkbox"
                          checked={form.capabilities.includes(item.value)}
                          onChange={() => toggleCapability(item.value)}
                        />
                        <span>{item.label}</span>
                      </label>
                    ))}
                  </div>
                )}
              </section>

              <div className="provider-form-grid two">
                <Field label="默认模型">
                  <input value={form.defaultModel} onChange={(event) => setFormValue('defaultModel', event.target.value)} placeholder="例如：gpt-4o-mini" />
                </Field>
                <Field label="聊天模型">
                  <input value={form.chatModel} onChange={(event) => setFormValue('chatModel', event.target.value)} placeholder="留空使用默认模型" />
                </Field>
                <Field label="图片模型">
                  <input value={form.imageModel} onChange={(event) => setFormValue('imageModel', event.target.value)} placeholder="用于图片提示词生成" />
                </Field>
                <Field label="视频模型">
                  <input value={form.videoModel} onChange={(event) => setFormValue('videoModel', event.target.value)} placeholder="用于视频脚本生成" />
                </Field>
                <Field label="知识库问答模型">
                  <input value={form.knowledgeModel} onChange={(event) => setFormValue('knowledgeModel', event.target.value)} placeholder="留空使用默认模型" />
                </Field>
              </div>

              <AdvancedSection title="配置 JSON" open={openAdvanced.json} onToggle={() => toggleAdvanced('json')}>
                <div className="json-tools">
                  <span>{jsonManuallyEdited ? '已手动修改，自动同步已暂停。' : '根据上方表单自动生成，仍可手动编辑。'}</span>
                  <button type="button" onClick={refreshConfigJson}>根据表单刷新 JSON</button>
                </div>
                <textarea value={form.configJson} onChange={(event) => handleConfigJsonChange(event.target.value)} spellCheck={false} />
              </AdvancedSection>
              <AdvancedSection title="模型测试配置" open={openAdvanced.test} onToggle={() => toggleAdvanced('test')}>
                <div className="provider-form-grid two">
                  <Field label="测试模型"><input value={form.testModel} onChange={(event) => setFormValue('testModel', event.target.value)} placeholder="留空使用默认配置" /></Field>
                  <Field label="超时时间（秒）"><input value={form.testTimeoutSeconds} onChange={(event) => setFormValue('testTimeoutSeconds', event.target.value)} /></Field>
                  <Field label="测试提示词"><input value={form.testPrompt} onChange={(event) => setFormValue('testPrompt', event.target.value)} /></Field>
                  <Field label="降级阈值（毫秒）"><input value={form.testDegradeMs} onChange={(event) => setFormValue('testDegradeMs', event.target.value)} /></Field>
                </div>
              </AdvancedSection>
              <AdvancedSection title="代理配置" open={openAdvanced.proxy} onToggle={() => toggleAdvanced('proxy')}>
                <div className="provider-form-grid two">
                  <Field label="代理地址"><input value={form.proxyUrl} onChange={(event) => setFormValue('proxyUrl', event.target.value)} placeholder="http://127.0.0.1:7890 / socks5://127.0.0.1:1080" /></Field>
                  <Field label="用户名"><input value={form.proxyUsername} onChange={(event) => setFormValue('proxyUsername', event.target.value)} placeholder="可选" /></Field>
                  <Field label="密码"><input value={form.proxyPassword} onChange={(event) => setFormValue('proxyPassword', event.target.value)} type="password" placeholder="可选" /></Field>
                </div>
              </AdvancedSection>
              <AdvancedSection title="计费配置" open={openAdvanced.billing} onToggle={() => toggleAdvanced('billing')}>
                <div className="provider-form-grid two">
                  <Field label="成本倍率"><input value={form.costMultiplier} onChange={(event) => setFormValue('costMultiplier', event.target.value)} placeholder="留空使用全局默认（1）" /></Field>
                  <Field label="计费模式">
                    <select value={form.billingMode} onChange={(event) => setFormValue('billingMode', event.target.value)}>
                      <option value="inherit">继承全局默认</option>
                      <option value="request_model">按请求模型匹配</option>
                      <option value="response_model">按返回模型匹配</option>
                    </select>
                  </Field>
                </div>
              </AdvancedSection>
            </div>

            <footer className="provider-editor-footer">
              <button type="button" className="ghost" onClick={() => setForm(null)}>取消</button>
              <button type="submit">{form.providerId ? '保存' : '添加'}</button>
            </footer>
          </form>
        </div>
      )}
    </section>
  );

  function setFormValue<K extends keyof ProviderFormState>(key: K, value: ProviderFormState[K]) {
    setForm((current) => current ? { ...current, [key]: value } : current);
  }

  function handleConfigJsonChange(value: string) {
    setJsonManuallyEdited(true);
    setFormValue('configJson', value);
  }

  function refreshConfigJson() {
    setForm((current) => current ? { ...current, configJson: buildAutoConfigJson(current) } : current);
    setJsonManuallyEdited(false);
  }

  function handleApiFormatChange(apiFormat: AiProviderApiFormat) {
    setForm((current) => current ? {
      ...current,
      apiFormat,
      authHeaderName: current.authHeaderName || defaultAuthHeader(apiFormat)
    } : current);
  }

  function toggleCapability(capability: Exclude<AiProviderCapability, 'ALL'>) {
    setForm((current) => {
      if (!current) return current;
      const exists = current.capabilities.includes(capability);
      const next = exists
        ? current.capabilities.filter((item) => item !== capability)
        : [...current.capabilities, capability];
      return { ...current, capabilities: next.length ? next : [capability] };
    });
  }

  function toggleAdvanced(key: string) {
    setOpenAdvanced((current) => ({ ...current, [key]: !current[key] }));
  }
}

function Field({ children, label, sideLabel }: { children: ReactNode; label: string; sideLabel?: string }) {
  return (
    <label className="provider-field">
      <span>{label}{sideLabel && <em>{sideLabel}</em>}</span>
      {children}
    </label>
  );
}

function AdvancedSection({ children, onToggle, open, title }: { children: ReactNode; onToggle: () => void; open: boolean; title: string }) {
  return (
    <section className={open ? 'advanced-section open' : 'advanced-section'}>
      <button type="button" onClick={onToggle}>
        <span>{title}</span>
        <strong>{open ? 'v' : '>'}</strong>
      </button>
      {open && <div className="advanced-section-body">{children}</div>}
    </section>
  );
}

function toPayload(form: ProviderFormState): AiProviderRequest {
  const config = mergeConfigJson(form);
  return {
    name: form.name,
    providerType: 'external',
    apiFormat: form.apiFormat,
    authHeaderName: form.authHeaderName,
    capabilities: form.useAllCapabilities ? ['ALL'] : form.capabilities,
    baseUrl: form.baseUrl,
    apiKey: form.apiKey || undefined,
    modelName: form.defaultModel || null,
    defaultModel: form.defaultModel || null,
    chatModel: form.chatModel || null,
    imageModel: form.imageModel || null,
    videoModel: form.videoModel || null,
    knowledgeModel: form.knowledgeModel || null,
    officialUrl: form.officialUrl || null,
    remark: form.remark || null,
    configJson: JSON.stringify(config, null, 2),
    enabled: form.enabled
  };
}

function providerToRequest(provider: AiProvider): AiProviderRequest {
  return {
    name: provider.name,
    providerType: provider.providerType || 'external',
    apiFormat: provider.apiFormat,
    authHeaderName: provider.authHeaderName || defaultAuthHeader(provider.apiFormat),
    capabilities: provider.capabilities,
    baseUrl: provider.baseUrl,
    modelName: provider.modelName || provider.defaultModel || null,
    defaultModel: provider.defaultModel || provider.modelName || null,
    chatModel: provider.chatModel || null,
    imageModel: provider.imageModel || null,
    videoModel: provider.videoModel || null,
    knowledgeModel: provider.knowledgeModel || null,
    officialUrl: provider.officialUrl || null,
    remark: provider.remark || null,
    configJson: provider.configJson || null,
    enabled: provider.enabled
  };
}

function mergeConfigJson(form: ProviderFormState) {
  let parsed: Record<string, unknown> = {};
  try {
    parsed = form.configJson.trim() ? JSON.parse(form.configJson) : {};
  } catch {
    parsed = { rawConfig: form.configJson };
  }
  return {
    ...parsed,
    test: {
      model: form.testModel || undefined,
      prompt: form.testPrompt || undefined,
      timeoutSeconds: numberOrUndefined(form.testTimeoutSeconds),
      degradeThresholdMs: numberOrUndefined(form.testDegradeMs)
    },
    proxy: {
      url: form.proxyUrl || undefined,
      username: form.proxyUsername || undefined,
      passwordSet: Boolean(form.proxyPassword)
    },
    billing: {
      costMultiplier: numberOrUndefined(form.costMultiplier),
      mode: form.billingMode
    }
  };
}

function buildAutoConfigJson(form: ProviderFormState) {
  return JSON.stringify({
    provider: {
      name: form.name || undefined,
      remark: form.remark || undefined,
      officialUrl: form.officialUrl || undefined,
      baseUrl: form.baseUrl || undefined,
      apiFormat: form.apiFormat,
      authHeaderName: form.authHeaderName,
      capabilities: form.useAllCapabilities ? ['ALL'] : form.capabilities
    },
    models: {
      default: form.defaultModel || undefined,
      chat: form.chatModel || form.defaultModel || undefined,
      image: form.imageModel || form.defaultModel || undefined,
      video: form.videoModel || form.defaultModel || undefined,
      knowledge: form.knowledgeModel || form.defaultModel || undefined
    },
    test: {
      model: form.testModel || form.chatModel || form.defaultModel || undefined,
      prompt: form.testPrompt || undefined,
      timeoutSeconds: numberOrUndefined(form.testTimeoutSeconds),
      degradeThresholdMs: numberOrUndefined(form.testDegradeMs)
    },
    proxy: {
      url: form.proxyUrl || undefined,
      username: form.proxyUsername || undefined,
      passwordSet: Boolean(form.proxyPassword)
    },
    billing: {
      costMultiplier: numberOrUndefined(form.costMultiplier),
      mode: form.billingMode
    },
    env: {},
    includeCoAuthoredBy: false
  }, null, 2);
}

function numberOrUndefined(value: string) {
  const numeric = Number(value);
  return Number.isFinite(numeric) && value.trim() ? numeric : undefined;
}

function defaultAuthHeader(apiFormat?: AiProviderApiFormat | null) {
  return apiFormat === 'anthropic_messages' ? 'x-api-key' : 'Authorization';
}

function capabilityLabels(capabilities: AiProviderCapability[]) {
  if (capabilities.includes('ALL')) return ['全部能力'];
  return capabilities.map((capability) => CAPABILITY_OPTIONS.find((item) => item.value === capability)?.label || capability);
}
