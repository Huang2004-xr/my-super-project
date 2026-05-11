$Host.UI.RawUI.WindowTitle = "SuperAgent Java API :8620"
$ErrorActionPreference = "Stop"
cd "D:\超级进步\项目\my-super-project/backend-java"
if (-not $env:AGENT_INTERNAL_TOKEN) { $env:AGENT_INTERNAL_TOKEN = "local-dev-internal-token" }
mvn spring-boot:run