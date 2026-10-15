# Run all backend tests against their guarded, pre-migrated local PostgreSQL databases.
# This script never creates, migrates, truncates, or drops a database.
param([switch]$SkipUnit)

$ErrorActionPreference = 'Stop'
$originalLocation = Get-Location
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
$originalDbUrl = $env:DB_URL
try {
    $files = @(Get-ChildItem 'src/test/java' -Recurse -Filter '*Test.java')
    $groups = [ordered]@{
        'smartlab_chat_it' = @('RealtimeChatConcurrencyPostgresIntegrationTest', 'RealtimeChatWebSocketPostgresIntegrationTest', 'ProjectLeadershipD3PostgresIntegrationTest')
        'smartlab_d1_auth_it' = @('AdminAccountInvitationPostgresIntegrationTest', 'UserSessionPostgresIntegrationTest')
        'smartlab_rich_editor_deploy_rehearsal' = @('PostWorkflowDatabaseRbacSecurityIntegrationTest')
        'smartlab_rich_editor_it' = @($files | Where-Object { (Get-Content $_.FullName -Raw) -match 'smartlab_rich_editor_it' } | ForEach-Object BaseName)
    }
    $assigned = @($groups.Values | ForEach-Object { $_ })
    if ($assigned.Count -ne (@($assigned | Select-Object -Unique)).Count) {
        throw 'Duplicate test class in the database matrix.'
    }
    $available = @($files | ForEach-Object BaseName)
    foreach ($name in $assigned) {
        if ($name -notin $available) { throw "Missing test class: $name" }
    }
    $unit = @($available | Where-Object { $_ -notin $assigned })
    if (-not $SkipUnit) {
        $allGroups = [ordered]@{ 'smartlab_chat_it (unit/non-DB)' = $unit }
        foreach ($entry in $groups.GetEnumerator()) { $allGroups[$entry.Key] = $entry.Value }
        $groups = $allGroups
    }

    foreach ($entry in $groups.GetEnumerator()) {
        $database = ($entry.Key -split ' ')[0]
        $names = @($entry.Value)
        if ($names.Count -eq 0) { continue }
        $env:DB_URL = "jdbc:postgresql://localhost:5432/$database"
        Write-Host "Running $($names.Count) classes on $database ($($entry.Key))..."
        $poolSize = if ($database -eq 'smartlab_d1_auth_it') { 16 } else { 7 }
        & mvn.cmd -q "-Dtest=$($names -join ',')" '-Dspring.test.context.cache.maxSize=3' "-Dspring.datasource.hikari.maximum-pool-size=$poolSize" '-Dspring.datasource.hikari.minimum-idle=0' test
        if ($LASTEXITCODE -ne 0) { throw "Test group failed: $($entry.Key)" }
        $count = 0
        $skipped = 0
        foreach ($name in $names) {
            $report = Get-ChildItem 'target/surefire-reports' -Filter "TEST-*.$name.xml" | Select-Object -First 1
            if ($null -eq $report) { throw "Surefire report missing: $name" }
            [xml]$xml = Get-Content $report.FullName -Raw
            if ([int]$xml.testsuite.failures -ne 0 -or [int]$xml.testsuite.errors -ne 0) { throw "Test failures in $name" }
            $count += [int]$xml.testsuite.tests
            $skipped += [int]$xml.testsuite.skipped
        }
        Write-Host "PASS $($entry.Key): $count tests, $skipped skipped"
    }
} finally {
    $env:DB_URL = $originalDbUrl
    Set-Location $originalLocation
}
