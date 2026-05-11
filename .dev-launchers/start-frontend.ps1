$Host.UI.RawUI.WindowTitle = "SuperAgent Frontend :5173"
$ErrorActionPreference = "Stop"
cd "D:\超级进步\项目\my-super-project/frontend"
if (-not (Test-Path node_modules)) { npm install }
npm run dev