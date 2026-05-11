$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$milvusHost = if ($env:KNOWLEDGE_MILVUS_HOST) { $env:KNOWLEDGE_MILVUS_HOST } else { "127.0.0.1" }
$milvusPort = if ($env:KNOWLEDGE_MILVUS_PORT) { [int]$env:KNOWLEDGE_MILVUS_PORT } else { 19530 }
$launcherDir = Join-Path $root ".dev-launchers"

function Test-ServicePort {
  param(
    [string]$HostName,
    [int]$Port,
    [string]$Label
  )

  try {
    $result = Test-NetConnection -ComputerName $HostName -Port $Port -WarningAction SilentlyContinue
    if ($result.TcpTestSucceeded) {
      Write-Host "[OK] $Label reachable: ${HostName}:$Port"
      return $true
    }
    Write-Warning "$Label is not reachable: ${HostName}:$Port"
    return $false
  } catch {
    Write-Warning "$Label check failed: ${HostName}:$Port - $($_.Exception.Message)"
    return $false
  }
}

function Test-LocalPortListening {
  param([int]$Port)
  $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  return $null -ne $connection
}

function Write-Launcher {
  param(
    [string]$Path,
    [string[]]$Lines
  )
  $content = $Lines -join [Environment]::NewLine
  [System.IO.File]::WriteAllText($Path, $content, [System.Text.UTF8Encoding]::new($true))
}

Write-Host "Running preflight checks..."
[void](Test-ServicePort -HostName "127.0.0.1" -Port 3306 -Label "MySQL")
[void](Test-ServicePort -HostName "127.0.0.1" -Port 11434 -Label "Ollama")
[void](Test-ServicePort -HostName $milvusHost -Port $milvusPort -Label "Milvus")
if (Get-Command tesseract -ErrorAction SilentlyContinue) {
  Write-Host "[OK] Tesseract OCR available"
} else {
  Write-Warning "Tesseract OCR is not installed or not in PATH. Image OCR and scanned PDF parsing will be unavailable."
}
Write-Host "Milvus must be started separately before knowledge indexing/search will work."

New-Item -ItemType Directory -Force -Path $launcherDir | Out-Null

$pythonLauncher = Join-Path $launcherDir "start-python.ps1"
$javaLauncher = Join-Path $launcherDir "start-java.ps1"
$frontendLauncher = Join-Path $launcherDir "start-frontend.ps1"

Write-Launcher -Path $pythonLauncher -Lines @(
  '$Host.UI.RawUI.WindowTitle = "SuperAgent Python Runtime :8000"',
  "`$ErrorActionPreference = `"Stop`"",
  "cd `"$root/agent-python`"",
  '$env:OLLAMA_MODELS = "D:\Ollama\models"',
  '$env:OLLAMA_NUM_CTX = "4096"',
  '$env:PYTHONIOENCODING = "utf-8"',
  'if (-not $env:AGENT_INTERNAL_TOKEN) { $env:AGENT_INTERNAL_TOKEN = "local-dev-internal-token" }',
  'if (-not $env:AGENT_MAX_IMAGE_BYTES) { $env:AGENT_MAX_IMAGE_BYTES = "10485760" }',
  'if (-not $env:AGENT_MAX_DOCUMENT_BYTES) { $env:AGENT_MAX_DOCUMENT_BYTES = "20971520" }',
  "`$env:KNOWLEDGE_MILVUS_HOST = `"$milvusHost`"",
  "`$env:KNOWLEDGE_MILVUS_PORT = `"$milvusPort`"",
  'if (-not (Test-Path .venv/Scripts/Activate.ps1)) { python -m venv .venv }',
  '. .venv/Scripts/Activate.ps1',
  'python -m pip install -r requirements.txt',
  'uvicorn app.main:app --host 127.0.0.1 --port 8000'
)

Write-Launcher -Path $javaLauncher -Lines @(
  '$Host.UI.RawUI.WindowTitle = "SuperAgent Java API :8620"',
  "`$ErrorActionPreference = `"Stop`"",
  "cd `"$root/backend-java`"",
  'if (-not $env:AGENT_INTERNAL_TOKEN) { $env:AGENT_INTERNAL_TOKEN = "local-dev-internal-token" }',
  'mvn spring-boot:run'
)

Write-Launcher -Path $frontendLauncher -Lines @(
  '$Host.UI.RawUI.WindowTitle = "SuperAgent Frontend :5173"',
  "`$ErrorActionPreference = `"Stop`"",
  "cd `"$root/frontend`"",
  'if (-not (Test-Path node_modules)) { npm install }',
  'npm run dev'
)

if (Test-LocalPortListening -Port 8000) {
  Write-Host "[SKIP] Python :8000 is already running."
} else {
  Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", $pythonLauncher)
}

if (Test-LocalPortListening -Port 8620) {
  Write-Host "[SKIP] Java :8620 is already running."
} else {
  Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", $javaLauncher)
}

if (Test-LocalPortListening -Port 5173) {
  Write-Host "[SKIP] Frontend :5173 is already running."
} else {
  Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", $frontendLauncher)
}

Write-Host "Startup command completed."
Write-Host "Frontend: http://localhost:5173"
Write-Host "Java health: http://localhost:8620/api/health"
Write-Host "Python health: http://localhost:8000/health"
