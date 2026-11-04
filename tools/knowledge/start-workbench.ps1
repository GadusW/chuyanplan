$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '../..')
$runtimeCandidates = @(
    (Join-Path (Get-Location) ".venv/Scripts/python.exe"),
    "$env:USERPROFILE/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe",
    "$env:USERPROFILE/AppData/Local/Programs/Python/Python314/python.exe",
    "$env:USERPROFILE/AppData/Local/Programs/Python/Python313/python.exe"
)
$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
if ($pythonCommand) { $runtimeCandidates += $pythonCommand.Source }
$pythonExecutable = $null
foreach ($candidate in $runtimeCandidates) {
    if (Test-Path -LiteralPath $candidate) {
        & $candidate -c "import sys; sys.exit(0 if sys.version_info >= (3,10) else 1)" 2>$null
        if ($LASTEXITCODE -eq 0) { $pythonExecutable = $candidate; break }
    }
}
if (-not $pythonExecutable) {
    Write-Host '需要 Python 3.10 或更高版本。请把 docs/knowledge-workflow/使用说明.md 交给你的 agent 完成首次准备。'
    Read-Host '按回车关闭'
    exit 1
}
& $pythonExecutable -X utf8 tools/knowledge/workflow.py serve --open
