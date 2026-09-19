# Copies the LowTalk Dialogue workspace into Hytale's standalone Node Editor.
#
# The PowerShell twin of install.sh, because Windows has no sh and the workspace is no use to a creator who
# cannot install it. The two do the same thing and should be changed together.
#
# The editor only reads workspaces from inside the client install, and every client update replaces that
# install, so run this again whenever the editor stops offering "LowTalk - Dialogue".
#
#   powershell -ExecutionPolicy Bypass -File tools\nodeeditor\install.ps1
#   powershell -ExecutionPolicy Bypass -File tools\nodeeditor\install.ps1 "D:\path\to\NodeEditor\Workspaces"
#
# With no argument it installs into every Hytale client it can find, release and pre-release.

param([string]$Workspaces)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $here 'LowTalk Dialogue'

if (-not (Test-Path -LiteralPath $src)) {
    Write-Error "The workspace is missing from $here. Run this from inside a checkout of the mod."
    exit 1
}

function Install-Into([string]$dest) {
    $target = Join-Path $dest 'LowTalk Dialogue'
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
    Copy-Item -LiteralPath $src -Destination $target -Recurse
    Write-Host "Installed the LowTalk Dialogue workspace into $dest"
}

if ($Workspaces) {
    if (-not (Test-Path -LiteralPath $Workspaces -PathType Container)) {
        Write-Host "Not a folder: $Workspaces"
        Write-Host "Pass the path to the NodeEditor\Workspaces folder of your Hytale client."
        exit 1
    }
    Install-Into $Workspaces
    Write-Host "Open the Node Editor and pick 'LowTalk - Dialogue' when creating a file; saved files remember it."
    exit 0
}

$root = Join-Path $env:APPDATA 'Hytale\install'
$found = $false
foreach ($line in @('release', 'pre-release')) {
    $candidate = Join-Path $root "$line\package\game\latest\Client\NodeEditor\Workspaces"
    if (Test-Path -LiteralPath $candidate -PathType Container) {
        Install-Into $candidate
        $found = $true
    }
}

if (-not $found) {
    Write-Host "No Hytale client found under $root"
    Write-Host "Pass the path to your client's NodeEditor\Workspaces folder as the first argument."
    exit 1
}

Write-Host "Open the Node Editor and pick 'LowTalk - Dialogue' when creating a file; saved files remember it."
