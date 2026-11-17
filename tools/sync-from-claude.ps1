<#
.SYNOPSIS
    Copies the newest NEURAL//DECK work out of the Claude Code session's temp folders into this repo.

.DESCRIPTION
    The Claude Code session that set up this project worked in a temporary copy of the app
    ("scratchpad\b"), wrote test suites in parallel copies ("scratchpad\b_<suite>", each started
    from "b_base"), and kept design notes next to them, all outside this repo. Run this script
    once, after that session has stopped and before starting new work, so the repo holds the latest
    version of what it produced.

    It only adds or updates files; it never deletes anything, never replaces a file that is newer
    in the repo, and never replaces an existing reference screenshot with one from a suite folder.
    It does not touch the app's own code (app\src\main, app\module.toml) unless you pass
    -IncludeAppSources. Without that switch it only reports whether the Claude copy differs.

.PARAMETER SessionDir
    The Claude session folder (the one that contains "scratchpad" and "tasks").
    Found automatically when omitted.

.PARAMETER IncludeAppSources
    Also copy app\src\main and app\module.toml from the Claude working copy (scratchpad\b).
    Only do this after its tests pass: a session cut off mid-change can leave that code half-edited.

.PARAMETER DryRun
    List what would be copied without copying anything.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\sync-from-claude.ps1 -DryRun

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\sync-from-claude.ps1
#>
[CmdletBinding()]
param(
    [string]$SessionDir,
    [switch]$IncludeAppSources,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$Repo = Split-Path -Parent $PSScriptRoot
$ProjectSlug = $Repo -replace '[:\\/_]', '-'
$MemoryDir = Join-Path $env:USERPROFILE (Join-Path '.claude\projects' (Join-Path $ProjectSlug 'memory'))
$script:Copied = 0

function Find-SessionDir {
    $claudeTemp = Join-Path $env:LOCALAPPDATA 'Temp\claude'
    if (-not (Test-Path -LiteralPath $claudeTemp)) { return $null }
    $best = $null
    $bestTime = [datetime]::MinValue
    foreach ($group in Get-ChildItem -LiteralPath $claudeTemp -Directory -ErrorAction SilentlyContinue) {
        foreach ($session in Get-ChildItem -LiteralPath $group.FullName -Directory -ErrorAction SilentlyContinue) {
            $settings = Join-Path $session.FullName 'scratchpad\b\settings.gradle.kts'
            if (-not (Test-Path -LiteralPath $settings)) { continue }
            if (-not (Select-String -LiteralPath $settings -Pattern 'NeuralDeck' -Quiet)) { continue }
            $newest = Get-ChildItem -LiteralPath (Join-Path $session.FullName 'scratchpad') -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object { $_.FullName -notmatch '\\(build|\.gradle|tools)\\' } |
                Sort-Object LastWriteTime -Descending |
                Select-Object -First 1
            if ($newest -and $newest.LastWriteTime -gt $bestTime) {
                $best = $session.FullName
                $bestTime = $newest.LastWriteTime
            }
        }
    }
    return $best
}

function Test-HasFiles([string]$Dir, [string[]]$Patterns) {
    if (-not (Test-Path -LiteralPath $Dir)) { return $false }
    foreach ($p in $Patterns) {
        if (Get-ChildItem -LiteralPath $Dir -Filter $p -File -ErrorAction SilentlyContinue) { return $true }
    }
    return $false
}

# Copies files with robocopy and lists each file it copies.
# /XO: a file that is newer in the repo is never replaced.
function Sync-Dir([string]$From, [string]$To, [string[]]$Files = @('*'), [switch]$Recurse) {
    if (-not (Test-Path -LiteralPath $From)) {
        Write-Host "    (not found, skipped) $From"
        return
    }
    if (-not $Recurse -and -not (Test-HasFiles $From $Files)) {
        Write-Host "    (nothing to copy from $From)"
        return
    }
    $rc = @($From, $To) + $Files + @('/XO', '/R:1', '/W:1', '/NDL', '/NJH', '/NJS', '/NP', '/XX', '/XD', 'build', '.gradle')
    if ($Recurse) { $rc += '/E' }
    if ($DryRun) { $rc += '/L' }
    $out = & robocopy @rc
    if ($LASTEXITCODE -ge 8) { throw "robocopy failed with exit code $LASTEXITCODE while copying $From" }
    $lines = @($out | Where-Object { $_ -and $_.Trim() -ne '' })
    foreach ($line in $lines) { Write-Host ('    ' + ($line.Trim() -replace '\s+', ' ')) }
    $script:Copied += $lines.Count
}

function Get-TreeHashes([string]$Root) {
    $map = @{}
    if (-not (Test-Path -LiteralPath $Root)) { return $map }
    foreach ($f in Get-ChildItem -LiteralPath $Root -Recurse -File) {
        $rel = $f.FullName.Substring($Root.Length).TrimStart('\')
        $map[$rel] = (Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256).Hash
    }
    return $map
}

# Brings in every test file a parallel suite folder (scratchpad\b_<suite>) added or changed relative
# to b_base. Never replaces an existing reference screenshot or a newer repo file, and warns when two
# suites changed the same file differently.
function Import-SuiteTests {
    $suites = @(Get-ChildItem -LiteralPath $Scratch -Directory -Filter 'b_*' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne 'b_base' })
    if ($suites.Count -eq 0) {
        Write-Host '    No parallel suite folders (b_<suite>).'
        return
    }
    Write-Host ('    Parallel suite folders: ' + (($suites | ForEach-Object { $_.Name }) -join ', '))
    $baseHashes = Get-TreeHashes (Join-Path $Scratch 'b_base\app\src\test')
    $repoTest = Join-Path $Repo 'app\src\test'
    $repoMain = Get-TreeHashes (Join-Path $Repo 'app\src\main')
    $picked = @{}
    foreach ($s in $suites) {
        $root = Join-Path $s.FullName 'app\src\test'
        if (Test-Path -LiteralPath $root) {
            foreach ($f in Get-ChildItem -LiteralPath $root -Recurse -File) {
                $rel = $f.FullName.Substring($root.Length).TrimStart('\')
                $hash = (Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256).Hash
                if ($baseHashes.ContainsKey($rel) -and $baseHashes[$rel] -eq $hash) { continue }
                $target = Join-Path $repoTest $rel
                if (Test-Path -LiteralPath $target) {
                    if ($rel -like 'snapshots\*') { continue }
                    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $hash) { continue }
                    if ((Get-Item -LiteralPath $target).LastWriteTime -ge $f.LastWriteTime) { continue }
                }
                if ($picked.ContainsKey($rel)) {
                    $prev = $picked[$rel]
                    if ($prev.Hash -eq $hash) { continue }
                    Write-Host "    ! $rel was changed differently in $($prev.Suite) and $($s.Name); taking the newer one. Check it by hand."
                    if ($f.LastWriteTime -le $prev.File.LastWriteTime) { continue }
                }
                $picked[$rel] = [pscustomobject]@{ Suite = $s.Name; File = $f; Hash = $hash }
            }
        }
        $suiteMain = Join-Path $s.FullName 'app\src\main'
        if (Test-Path -LiteralPath $suiteMain) {
            $theirMain = Get-TreeHashes $suiteMain
            $changed = @($theirMain.Keys | Where-Object { -not $repoMain.ContainsKey($_) -or $repoMain[$_] -ne $theirMain[$_] })
            if ($changed.Count -gt 0) {
                Write-Host "    ! $($s.Name) also changed $($changed.Count) app source file(s) under app\src\main. Not copied."
            }
        }
    }
    foreach ($rel in ($picked.Keys | Sort-Object)) {
        $p = $picked[$rel]
        $target = Join-Path $repoTest $rel
        $verb = 'New'
        if (Test-Path -LiteralPath $target) { $verb = 'Updated' }
        Write-Host "    $verb from $($p.Suite): app\src\test\$rel"
        if (-not $DryRun) {
            $dir = Split-Path -Parent $target
            if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
            Copy-Item -LiteralPath $p.File.FullName -Destination $target -Force
        }
        $script:Copied++
    }
    if ($picked.Count -gt 0) {
        Write-Host '    Suites copied mid-session may be unfinished. Run the tests; finish or remove any suite that does not compile.'
    }
}

# DefectTest pins confirmed bugs and fails until they are fixed, so normal runs leave it out.
# Re-applies that switch if a newer app\build.gradle.kts from the session replaced the repo's.
function Set-DefectSwitch {
    $file = Join-Path $Repo 'app\build.gradle.kts'
    if ($DryRun -or -not (Test-Path -LiteralPath $file)) { return }
    $text = [System.IO.File]::ReadAllText($file)
    if ($text.Contains('DefectTest')) { return }
    $anchor = 'all { test ->'
    $i = $text.IndexOf($anchor)
    if ($i -lt 0) {
        Write-Host '    ! Could not add the DefectTest switch to app\build.gradle.kts. Add it by hand (see AGENTS.md).'
        return
    }
    $nl = "`n"
    if ($text.Contains("`r`n")) { $nl = "`r`n" }
    $insert = $nl +
        '                // DefectTest pins confirmed bugs, so it fails until they are fixed. Normal runs leave it out;' + $nl +
        '                // run it on purpose with: -Pdefects=true --tests com.example.wtfff.DefectTest' + $nl +
        '                if (project.findProperty("defects")?.toString() != "true") {' + $nl +
        '                    test.exclude("**/DefectTest*")' + $nl +
        '                }'
    $at = $i + $anchor.Length
    $text = $text.Substring(0, $at) + $insert + $text.Substring($at)
    [System.IO.File]::WriteAllText($file, $text, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host '    Re-applied the DefectTest switch to app\build.gradle.kts.'
}

# Rewrites this PC's absolute paths inside the copied notes and logs to repo-relative ones, so they
# read the same on any machine and do not carry the Windows user folder into the repo.
function Convert-LocalPaths([string]$Dir) {
    if ($DryRun -or -not (Test-Path -LiteralPath $Dir)) { return }
    $pairs = @(
        @(($Scratch + '\b\'), ''),
        @(($Scratch + '\design\'), 'docs\architecture\design-panel\'),
        @(($Scratch + '\'), 'docs\claude-session\scratchpad\'),
        @($Scratch, '<claude-scratchpad>'),
        @(($Repo + '\'), ''),
        @((($Repo -replace '\\', '/') + '/'), ''),
        @($Repo, '<repo>'),
        @($env:LOCALAPPDATA, '%LOCALAPPDATA%'),
        @(($env:LOCALAPPDATA -replace '\\', '/'), '%LOCALAPPDATA%'),
        @($env:USERPROFILE, '%USERPROFILE%'),
        @(($env:USERPROFILE -replace '\\', '/'), '%USERPROFILE%'),
        @(('/' + $env:USERPROFILE.Substring(0, 1).ToLower() + ($env:USERPROFILE.Substring(2) -replace '\\', '/')), '%USERPROFILE%')
    )
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    foreach ($f in Get-ChildItem -LiteralPath $Dir -Recurse -File -Include *.md, *.output) {
        $text = [System.IO.File]::ReadAllText($f.FullName)
        $new = $text
        foreach ($p in $pairs) {
            $new = $new.Replace($p[0], $p[1])
            # the same path as it appears inside JSON strings, with escaped backslashes
            $new = $new.Replace($p[0].Replace('\', '\\'), $p[1].Replace('\', '\\'))
        }
        if ($new -ne $text) { [System.IO.File]::WriteAllText($f.FullName, $new, $utf8) }
    }
}

if (-not $SessionDir) { $SessionDir = Find-SessionDir }
if (-not $SessionDir -or -not (Test-Path -LiteralPath (Join-Path $SessionDir 'scratchpad\b'))) {
    Write-Host 'Could not find the Claude session working copy (a folder with scratchpad\b inside).'
    Write-Host 'It may have been cleaned up, or it lives elsewhere. Pass it explicitly, for example:'
    Write-Host "  -SessionDir `"$env:LOCALAPPDATA\Temp\claude\<id>\<session-id>`""
    exit 1
}

$Scratch = Join-Path $SessionDir 'scratchpad'
$B = Join-Path $Scratch 'b'
Write-Host "Claude session: $SessionDir"
Write-Host "Repo:           $Repo"
if ($DryRun) { Write-Host 'DRY RUN: listing only, nothing is copied.' }

Write-Host ''
Write-Host '[1/5] Gradle build files'
Sync-Dir $B $Repo @('settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'local.properties')
Sync-Dir (Join-Path $B 'gradle') (Join-Path $Repo 'gradle') -Recurse
Sync-Dir (Join-Path $B 'app') (Join-Path $Repo 'app') @('build.gradle.kts')
Set-DefectSwitch

Write-Host ''
Write-Host '[2/5] Tests and reference screenshots (app\src\test)'
Sync-Dir (Join-Path $B 'app\src\test') (Join-Path $Repo 'app\src\test') -Recurse
Import-SuiteTests

Write-Host ''
Write-Host '[3/5] App code (app\src\main, app\module.toml)'
$theirs = Get-TreeHashes (Join-Path $B 'app\src\main')
$ours = Get-TreeHashes (Join-Path $Repo 'app\src\main')
$different = @($theirs.Keys | Where-Object { -not $ours.ContainsKey($_) -or $ours[$_] -ne $theirs[$_] } | Sort-Object)
$onlyInRepo = @($ours.Keys | Where-Object { -not $theirs.ContainsKey($_) } | Sort-Object)
$tomlDiffers = $false
$theirToml = Join-Path $B 'app\module.toml'
$ourToml = Join-Path $Repo 'app\module.toml'
if ((Test-Path -LiteralPath $theirToml) -and (Test-Path -LiteralPath $ourToml)) {
    $tomlDiffers = (Get-FileHash -LiteralPath $theirToml).Hash -ne (Get-FileHash -LiteralPath $ourToml).Hash
}
if ($different.Count -eq 0 -and $onlyInRepo.Count -eq 0 -and -not $tomlDiffers) {
    Write-Host '    Same as the repo. Nothing to copy.'
} else {
    Write-Host "    The Claude copy differs from the repo: $($different.Count) changed or new file(s), $($onlyInRepo.Count) only in the repo, module.toml differs: $tomlDiffers"
    foreach ($f in ($different | Select-Object -First 40)) { Write-Host "      ~ app\src\main\$f" }
    foreach ($f in ($onlyInRepo | Select-Object -First 20)) { Write-Host "      - app\src\main\$f (not in the Claude copy)" }
    if ($IncludeAppSources) {
        Sync-Dir (Join-Path $B 'app\src\main') (Join-Path $Repo 'app\src\main') -Recurse
        Sync-Dir (Join-Path $B 'app') (Join-Path $Repo 'app') @('module.toml')
        if ($onlyInRepo.Count -gt 0) {
            Write-Host '    Files that exist only in the repo were left in place. Delete them by hand if the Claude version removed them on purpose.'
        }
    } else {
        Write-Host '    Not copied. Re-run with -IncludeAppSources to copy it (only once its tests pass).'
    }
}

Write-Host ''
Write-Host '[4/5] Design documents and reports'
Sync-Dir (Join-Path $Scratch 'design') (Join-Path $Repo 'docs\architecture\design-panel') -Recurse
Sync-Dir $Scratch (Join-Path $Repo 'docs\architecture') @('architecture-review-*.html')
Sync-Dir $Scratch (Join-Path $Repo 'docs\claude-session\scratchpad') @('*.md')
Sync-Dir $B (Join-Path $Repo 'docs\claude-session\working-copy-notes') @('*.md')
Sync-Dir (Join-Path $SessionDir 'tasks') (Join-Path $Repo 'docs\claude-session\workflow-results') @('*.output')

Write-Host ''
Write-Host "[5/5] Claude's project memory"
Sync-Dir $MemoryDir (Join-Path $Repo 'docs\claude-session\memory') @('*.md')

Convert-LocalPaths (Join-Path $Repo 'docs\architecture\design-panel')
Convert-LocalPaths (Join-Path $Repo 'docs\claude-session')

Write-Host ''
if ($DryRun) {
    Write-Host "Dry run finished: $script:Copied file(s) would be copied or updated."
} else {
    Write-Host "Done: $script:Copied file(s) copied or updated."
    Write-Host 'Next: tools\gw.cmd :app:testDebugUnitTest   (every test should pass)'
}
