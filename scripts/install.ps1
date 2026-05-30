# Testara Agent installer for Windows
# Usage:
#   iwr -useb https://github.com/ygrip/testara/releases/latest/download/install.ps1 | iex
#   $env:TESTARA_AGENT_VERSION="2.0.0"; iwr -useb .../install.ps1 | iex

param(
    [string]$Version = $env:TESTARA_AGENT_VERSION,
    [string]$InstallDir = "$env:USERPROFILE\.testara",
    [string]$BinDir = "$env:LOCALAPPDATA\Programs\testara-agent",
    [switch]$Uninstall
)

$GithubRepo = "ygrip/testara"
$ErrorActionPreference = "Stop"

function Write-Info  { Write-Host "[testara-agent] $args" -ForegroundColor Green }
function Write-Warn  { Write-Host "[testara-agent] WARNING: $args" -ForegroundColor Yellow }

# ── Uninstall ─────────────────────────────────────────────────────
if ($Uninstall) {
    Write-Info "Uninstalling testara-agent..."
    Remove-Item -Force -ErrorAction SilentlyContinue "$BinDir\testara-agent.bat"
    Remove-Item -Force -ErrorAction SilentlyContinue "$InstallDir\testara-agent.jar"
    Write-Info "Uninstall complete."
    exit 0
}

# ── Java check ────────────────────────────────────────────────────
try {
    $javaVer = (java -version 2>&1 | Select-String '(\d+)' | ForEach-Object { $_.Matches[0].Value }) | Select-Object -First 1
    if ([int]$javaVer -lt 21) {
        Write-Warn "Java $javaVer found — testara-agent requires Java 21+."
        Write-Warn "Install Temurin: https://adoptium.net/temurin/releases/"
    } else {
        Write-Info "Java $javaVer found."
    }
} catch {
    Write-Warn "Java not found. testara-agent requires Java 21+."
    Write-Warn "Install Temurin: https://adoptium.net/temurin/releases/"
}

# ── Resolve URL ───────────────────────────────────────────────────
if ([string]::IsNullOrEmpty($Version)) {
    $JarUrl = "https://github.com/$GithubRepo/releases/latest/download/testara-agent.jar"
} else {
    $JarUrl = "https://github.com/$GithubRepo/releases/download/v$Version/testara-agent.jar"
}

# ── Download ──────────────────────────────────────────────────────
Write-Info "Installing testara-agent..."
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
New-Item -ItemType Directory -Force -Path $BinDir | Out-Null

Write-Info "Downloading from $JarUrl"
$JarPath = "$InstallDir\testara-agent.jar"
Invoke-WebRequest -Uri $JarUrl -OutFile $JarPath
Write-Info "Saved to: $JarPath"

# ── Wrapper .bat ──────────────────────────────────────────────────
$WrapperPath = "$BinDir\testara-agent.bat"
@"
@echo off
java -jar "$JarPath" %*
"@ | Set-Content -Path $WrapperPath
Write-Info "Wrapper installed: $WrapperPath"

# ── Add to PATH ───────────────────────────────────────────────────
$userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
if ($userPath -notlike "*$BinDir*") {
    [System.Environment]::SetEnvironmentVariable("Path", "$BinDir;$userPath", "User")
    Write-Info "Added $BinDir to user PATH. Restart your terminal to use testara-agent."
} else {
    Write-Info "$BinDir already in PATH."
}

# ── Verify ────────────────────────────────────────────────────────
Write-Info ""
Write-Info "Quick start:"
Write-Info "  testara-agent /test-overview ."
Write-Info "  testara-agent --help"
Write-Info ""
Write-Info "Docs: https://github.com/$GithubRepo/blob/main/docs/agentic-skills.md"
