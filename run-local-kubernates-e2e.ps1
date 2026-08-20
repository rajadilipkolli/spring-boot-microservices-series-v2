param(
    [switch]$SkipCluster,
    [switch]$Teardown
)

# Use Continue so that native CLI tools writing informational messages to stderr
# (kind, kubectl) do not abort the script. We check $LASTEXITCODE explicitly.
$ErrorActionPreference = "Continue"
$CLUSTER_NAME = "kind"
$NAMESPACE    = "retailstore"
$HOSTS_FILE   = "C:\Windows\System32\drivers\etc\hosts"
$HOSTS_ENTRY  = "127.0.0.1 retailstore.local api.retailstore.local keycloak.local jobrunr.local"

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function OK($msg)   { Write-Host "OK: $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "WARN: $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "FAIL: $msg" -ForegroundColor Red; exit 1 }

function Add-HostsEntry {
    $current = Get-Content $HOSTS_FILE -Raw
    if ($current -notmatch "retailstore\.local") {
        $addCmd = "Add-Content -Path '$HOSTS_FILE' -Value '$HOSTS_ENTRY'"
        Start-Process powershell -Verb RunAs -ArgumentList "-Command", $addCmd -Wait
        OK "Added hosts entries"
    } else {
        Warn "Hosts entries already present - skipping."
    }
}

function Remove-HostsEntry {
    $lines = Get-Content $HOSTS_FILE | Where-Object { $_ -notmatch "retailstore\.local" }
    Set-Content -Path $HOSTS_FILE -Value $lines
    OK "Removed hosts entries."
}

if ($Teardown) {
    Step "Tearing down Kind cluster"
    kind delete cluster --name $CLUSTER_NAME 2>$null
    Remove-HostsEntry
    OK "Teardown complete."
    exit 0
}

Step "Verifying required tools"
foreach ($tool in @("docker","kind","kubectl","bash")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Fail "Required tool '$tool' not found on PATH."
    }
}
OK "All tools found."

if (-not $SkipCluster) {
    Step "Creating Kind cluster '$CLUSTER_NAME' with port-mapping config"
    kind delete cluster --name $CLUSTER_NAME 2>$null
    kind create cluster --name $CLUSTER_NAME --config deployment/k8s/kind-config.yaml --wait 120s
    if ($LASTEXITCODE -ne 0) { Fail "kind create cluster failed." }
    OK "Cluster '$CLUSTER_NAME' is up."
} else {
    Warn "Skipping cluster creation (-SkipCluster)."
}

Step "Installing NGINX Ingress controller"
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=120s
if ($LASTEXITCODE -ne 0) { Fail "NGINX Ingress controller did not become ready." }
OK "NGINX Ingress controller is ready."

Step "Pulling Docker images"
$IMAGES = @(
    "dockertmt/mmv2-config-server:0.0.1-SNAPSHOT",
    "dockertmt/mmv2-service-registry-25:0.0.1-SNAPSHOT",
    "dockertmt/mmv2-api-gateway:0.0.1-SNAPSHOT",
    "dockertmt/mmv2-catalog-service:0.0.1-SNAPSHOT",
    "dockertmt/mmv2-inventory-service:0.0.1-SNAPSHOT",
    "dockertmt/mmv2-order-service:0.0.1-SNAPSHOT",
    "dockertmt/mmv2-payment-service:0.0.1-SNAPSHOT",
    "dockertmt/mmv2-retail-store-webapp:0.0.1-SNAPSHOT"
)

foreach ($img in $IMAGES) {
    Write-Host "  pulling $img ..."
    docker pull $img
    if ($LASTEXITCODE -ne 0) { Fail "Failed to pull $img" }
}

Step "Loading images into Kind cluster"
foreach ($img in $IMAGES) {
    Write-Host "  loading $img ..."
    kind load docker-image $img --name $CLUSTER_NAME
    if ($LASTEXITCODE -ne 0) { Fail "Failed to load $img into Kind" }
}
OK "All images loaded."

Step "Applying Kustomize CI overlay"
kubectl apply -k deployment/k8s/overlays/ci/
if ($LASTEXITCODE -ne 0) { Fail "kubectl apply -k failed." }
OK "CI overlay applied."

Step "Waiting for infrastructure rollouts"
kubectl rollout status statefulset/postgresql -n $NAMESPACE --timeout=300s
kubectl rollout status deployment/redis -n $NAMESPACE --timeout=300s
kubectl rollout status statefulset/kafka -n $NAMESPACE --timeout=300s
kubectl rollout status deployment/keycloak -n $NAMESPACE --timeout=300s
OK "Infrastructure is ready."

Step "Waiting for Platform Core rollouts"
kubectl rollout status deployment/config-server -n $NAMESPACE --timeout=300s
kubectl rollout status deployment/service-registry -n $NAMESPACE --timeout=300s
OK "Platform core is ready."

Step "Waiting for Application rollouts"
foreach ($svc in @("catalog-service","inventory-service","order-service","payment-service","api-gateway","retail-store-webapp")) {
    kubectl rollout status deployment/$svc -n $NAMESPACE --timeout=300s
}
OK "All application services are ready."

# ── step 9 – add hosts entry ─────────────────────────────────────────────────
Step "Adding hosts file entries (requires UAC prompt)"
Add-HostsEntry

# ── step 10 – run E2E test suite ──────────────────────────────────────────────
Step "Running end-to-end test suite (test-em-all.sh)"
# Pass HOST and PORT inline inside bash so they are guaranteed to be set
# regardless of how PowerShell exports env vars to child processes.
bash -c "export HOST='api.retailstore.local'; export PORT='80'; ./test-em-all.sh --no-cb-strict"
$testExit = $LASTEXITCODE

# ── step 11 – optional smoke checks ──────────────────────────────────────────
if ($testExit -eq 0) {
    Step "Running webapp + Keycloak smoke checks"

    # webapp
    $webResp = curl -s -o /dev/null -w "%{http_code}" http://retailstore.local
    if ($webResp -eq "200") { OK "retail-store-webapp returned 200." }
    else                    { Warn "retail-store-webapp returned $webResp (non-fatal)." }

    # Keycloak token
    $tokenResp = bash -c @"
curl -s -X POST http://keycloak.local/realms/retailstore/protocol/openid-connect/token \
  -d 'client_id=retailstore-webapp' \
  -d 'grant_type=password' \
  -d 'username=alice' \
  -d 'password=alice' | grep -c access_token
"@
    if ($tokenResp -ge 1) { OK "Keycloak token endpoint returned access_token." }
    else                  { Warn "Keycloak token test skipped or non-fatal." }
}

# ── summary ───────────────────────────────────────────────────────────────────
Write-Host ""
if ($testExit -eq 0) {
    OK "All E2E tests PASSED! 🎉"
} else {
    Write-Host ""
    Warn "Some tests FAILED. Collecting diagnostics..."
    $diag = "k8s-diagnostics"
    New-Item -ItemType Directory -Force -Path $diag | Out-Null
    kubectl get pods    -A                                        > "$diag/pods.txt"
    kubectl get events  -n $NAMESPACE                            > "$diag/events.txt"
    kubectl describe pods -n $NAMESPACE                          > "$diag/pods-describe.txt"
    foreach ($pod in (kubectl get pods -n $NAMESPACE -o name)) {
        $clean = $pod -replace "/","_"
        kubectl logs $pod -n $NAMESPACE --all-containers         > "$diag/$clean.log" 2>$null
    }
    Warn "Diagnostics written to ./$diag/"
    exit 1
}
