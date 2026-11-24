<# Loads backend\.env (if present) into the process environment, then starts the backend with Maven.
   Usage (from the backend folder):  .\scripts\run-dev.ps1        #>
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

if (Test-Path .env) {
    Get-Content .env | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $name, $value = $line.Split('=', 2)
            [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
        }
    }
    Write-Host "Loaded .env"
} else {
    Write-Host "No .env found: using development defaults (see application-dev.yml)."
}

# Java: use JAVA_HOME if set, otherwise a JDK downloaded by IntelliJ (prefers 21, the version this project targets).
if (-not $env:JAVA_HOME) {
    $jdks = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue
    $jdk = ($jdks | Where-Object { $_.Name -match '(^|-)21(\.|$)' } | Select-Object -First 1)
    if (-not $jdk) { $jdk = $jdks | Select-Object -First 1 }
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName; Write-Host "JAVA_HOME = $($env:JAVA_HOME)" }
}

# Maven: wrapper, then mvn on PATH, then the copy bundled with IntelliJ IDEA.
if (Test-Path .\mvnw.cmd) {
    $mvn = '.\mvnw.cmd'
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvn = 'mvn'
} else {
    $bundled = Get-ChildItem 'C:\Program Files\JetBrains\*\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $bundled) { throw 'Maven not found. Install Maven or use the Maven tool window in IntelliJ.' }
    $mvn = $bundled.FullName
}
Write-Host "Using Maven: $mvn"
& $mvn spring-boot:run