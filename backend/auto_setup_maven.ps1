$ErrorActionPreference = "Stop"
$MavenVersion = "3.9.6"
$MavenZipUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MavenVersion/apache-maven-$MavenVersion-bin.zip"
$BackendDir = $PSScriptRoot
$MavenParentDir = Join-Path $BackendDir ".maven"
$MavenDir = Join-Path $MavenParentDir "apache-maven-$MavenVersion"
$MavenZipPath = Join-Path $MavenParentDir "maven.zip"

If (-Not (Test-Path $MavenDir)) {
    Write-Host "Maven is missing. Automatically downloading Maven $MavenVersion..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $MavenParentDir | Out-Null
    Write-Host "Downloading from Apache..."
    Invoke-WebRequest -Uri $MavenZipUrl -OutFile $MavenZipPath
    Write-Host "Extracting files..."
    Expand-Archive -Path $MavenZipPath -DestinationPath $MavenParentDir -Force
    Remove-Item $MavenZipPath
    Write-Host "Maven downloaded successfully!" -ForegroundColor Green
}

Write-Host "Starting Spring Boot Backend..." -ForegroundColor Cyan
$MvnCmd = Join-Path $MavenDir "bin\mvn.cmd"
Set-Location -Path $BackendDir
& $MvnCmd spring-boot:run
