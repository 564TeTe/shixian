param([switch]$Build, [switch]$Restart)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$runtimeFolder = Join-Path $projectRoot 'database/generated'
New-Item -ItemType Directory -Path $runtimeFolder -Force | Out-Null
$jarPath = Join-Path $projectRoot 'back/target/springboote51e2-0.0.1-SNAPSHOT.jar'
$readerConfig = Join-Path $runtimeFolder 'ai-database.local.json'
if (Test-Path -LiteralPath $readerConfig) {
    $config = Get-Content -LiteralPath $readerConfig -Raw | ConvertFrom-Json
    $env:TEACHING_AI_DB_USER = $config.TEACHING_AI_DB_USER
    $env:TEACHING_AI_DB_PASSWORD = $config.TEACHING_AI_DB_PASSWORD
}
$pidPath = Join-Path $runtimeFolder 'backend.pid'
if ($Restart -and (Test-Path -LiteralPath $pidPath)) {
    $savedProcessId = [int](Get-Content -LiteralPath $pidPath)
    $savedProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$savedProcessId"
    if ($savedProcess -and $savedProcess.CommandLine -like '*springboote51e2-0.0.1-SNAPSHOT.jar*') {
        Stop-Process -Id $savedProcessId
    }
}
if ($Build) {
    Push-Location (Join-Path $projectRoot 'back')
    try { & mvn.cmd -q package; if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' } }
    finally { Pop-Location }
}
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Run .\start-teaching.ps1 -Build first.' }
$backend = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if (-not $backend) {
    $java = (Get-Command java.exe).Source
    $process = Start-Process -FilePath $java -ArgumentList @('-Dfile.encoding=UTF-8','-jar','target/springboote51e2-0.0.1-SNAPSHOT.jar') `
        -WorkingDirectory (Join-Path $projectRoot 'back') -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $runtimeFolder 'backend.log') `
        -RedirectStandardError (Join-Path $runtimeFolder 'backend-error.log') -PassThru
    $process.Id | Set-Content -LiteralPath $pidPath
} else { Write-Output 'Port 8080 already has a service. Existing process was not replaced.' }
$frontend = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
if (-not $frontend) {
    $env:NODE_OPTIONS = '--openssl-legacy-provider'
    $node = (Get-Command node.exe).Source
    $process = Start-Process -FilePath $node -ArgumentList @('node_modules/@vue/cli-service/bin/vue-cli-service.js','serve','--host','127.0.0.1','--port','8081') `
        -WorkingDirectory (Join-Path $projectRoot 'front') -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $runtimeFolder 'frontend.log') `
        -RedirectStandardError (Join-Path $runtimeFolder 'frontend-error.log') -PassThru
    $process.Id | Set-Content -LiteralPath (Join-Path $runtimeFolder 'frontend.pid')
}
Write-Output 'Teaching system: http://localhost:8081'
Write-Output 'Local initial accounts: database/generated/teaching-accounts.local.json'
Write-Output 'Startup is asynchronous. Check database/generated/*.log if the page is not ready.'
