$Host.UI.RawUI.WindowTitle = "SuperAgent Python Runtime :8000"
$ErrorActionPreference = "Stop"
cd "D:\超级进步\项目\my-super-project/agent-python"
$env:OLLAMA_MODELS = "D:\Ollama\models"
$env:OLLAMA_NUM_CTX = "4096"
$env:PYTHONIOENCODING = "utf-8"
if (-not $env:AGENT_INTERNAL_TOKEN) { $env:AGENT_INTERNAL_TOKEN = "local-dev-internal-token" }
if (-not $env:AGENT_MAX_IMAGE_BYTES) { $env:AGENT_MAX_IMAGE_BYTES = "10485760" }
if (-not $env:AGENT_MAX_DOCUMENT_BYTES) { $env:AGENT_MAX_DOCUMENT_BYTES = "20971520" }
$env:KNOWLEDGE_MILVUS_HOST = "127.0.0.1"
$env:KNOWLEDGE_MILVUS_PORT = "19530"
if (-not (Test-Path .venv/Scripts/Activate.ps1)) { python -m venv .venv }
. .venv/Scripts/Activate.ps1
python -m pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000