param(
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not $SkipBuild) {
    Write-Host "[build-release-ubuntu] mvn -q -DskipTests package"
    mvn -q -DskipTests package
}

$jar = Get-ChildItem -Path (Join-Path $projectRoot "target") -Filter "proxy-hub-*.jar" |
    Where-Object { $_.Name -notlike "*.original" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "No runnable jar found in target/. Please run mvn package first."
}

$version = "unknown"
if ($jar.Name -match "^proxy-hub-(.+)\.jar$") {
    $version = $Matches[1]
}

$releaseName = "proxy-hub-$version-ubuntu"
$distRoot = Join-Path $projectRoot "dist"
$releaseRoot = Join-Path $distRoot $releaseName

if (Test-Path $releaseRoot) {
    Remove-Item -Recurse -Force $releaseRoot
}

New-Item -ItemType Directory -Path $releaseRoot | Out-Null
New-Item -ItemType Directory -Path (Join-Path $releaseRoot "bin") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $releaseRoot "conf") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $releaseRoot "target") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $releaseRoot "logs") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $releaseRoot "run") | Out-Null

Copy-Item $jar.FullName (Join-Path (Join-Path $releaseRoot "target") $jar.Name) -Force
Copy-Item (Join-Path $projectRoot "conf\application-prod.yml") (Join-Path $releaseRoot "conf\application-prod.yml") -Force
Copy-Item (Join-Path $projectRoot "README.md") (Join-Path $releaseRoot "README.md") -Force

$binFiles = @(
    "start.sh",
    "stop.sh",
    "restart.sh",
    "status.sh",
    "install-ubuntu.sh",
    "proxy-hub-ubuntu.service"
)
foreach ($file in $binFiles) {
    Copy-Item (Join-Path $projectRoot "bin\$file") (Join-Path $releaseRoot "bin\$file") -Force
}

$deployNote = @"
Proxy Hub Ubuntu release package

1. Upload and extract:
   tar -xzf $releaseName.tar.gz -C /opt
   cd /opt/$releaseName

2. Configure:
   vi conf/application-prod.yml

3. Install service (recommended):
   chmod +x bin/*.sh
   sudo bash bin/install-ubuntu.sh

4. Runtime operations:
   systemctl status proxy-hub
   systemctl restart proxy-hub
   journalctl -u proxy-hub -f

5. Optional no-start install:
   sudo bash bin/install-ubuntu.sh --no-start
"@
$deployNotePath = Join-Path $releaseRoot "DEPLOY-UBUNTU.txt"
[System.IO.File]::WriteAllText($deployNotePath, $deployNote, (New-Object System.Text.UTF8Encoding($false)))

$textFiles = @(
    "conf\application-prod.yml",
    "README.md",
    "DEPLOY-UBUNTU.txt",
    "bin\start.sh",
    "bin\stop.sh",
    "bin\restart.sh",
    "bin\status.sh",
    "bin\install-ubuntu.sh",
    "bin\proxy-hub-ubuntu.service"
) | ForEach-Object { Join-Path $releaseRoot $_ }

foreach ($file in $textFiles) {
    $content = Get-Content -Raw $file
    $content = $content -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText($file, $content, (New-Object System.Text.UTF8Encoding($false)))
}

$archive = Join-Path $distRoot "$releaseName.tar.gz"
if (Test-Path $archive) {
    Remove-Item -Force $archive
}

tar -czf $archive -C $distRoot $releaseName

Write-Host "[build-release-ubuntu] package ready:"
Write-Host "  $archive"
