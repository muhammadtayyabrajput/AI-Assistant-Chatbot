$root = $PSScriptRoot

if (-not $env:OPENROUTER_API_KEY) {
    Write-Warning "OPENROUTER_API_KEY is not set - the chat will answer with an error until you set it."
}

docker compose -f "$root\docker-compose.yml" up -d

$mvn = if ($env:MVN_CMD) { $env:MVN_CMD } else { "mvn" }

foreach ($svc in "auth-service", "banking-service", "chat-service") {
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\$svc'; & '$mvn' spring-boot:run"
}

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\frontend'; npm run dev"

Write-Host "Open http://localhost:3000 once the services finish starting (chat-service takes ~1.5 min the first time)."
