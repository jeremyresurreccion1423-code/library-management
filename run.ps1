Set-Location $PSScriptRoot

Write-Host "========================================"
Write-Host " EduLibrary (Smart Library)"
Write-Host "========================================"
Write-Host ""
Write-Host "Starting server at http://localhost:8080"
Write-Host "Admin login: admin / admin123"
Write-Host "Press Ctrl+C to stop."
Write-Host ""

mvn spring-boot:run "-Djava.net.preferIPv4Stack=true"
