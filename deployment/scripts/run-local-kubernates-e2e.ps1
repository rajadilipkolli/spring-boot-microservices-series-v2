param(
    [switch]$SkipCluster,
    [switch]$Teardown,
    [switch]$TestOnly
)

# Use Continue so that native CLI tools writing informational messages to stderr
# (kind, kubectl) do not abort the script. We check $LASTEXITCODE explicitly.
$ErrorActionPreference = "Continue"
$CLUSTER_NAME = "kind"
$NAMESPACE    = "retailstore"
$HOSTS_FILE   = "C:\Windows\System32\drivers\etc\hosts"
$HOSTS_MARKER = "# run-local-kubernates-e2e.ps1"
$HOSTS_ENTRY  = "127.0.0.1 retailstore.local api.retailstore.local keycloak.local jobrunr.local $HOSTS_MARKER"

# Script now lives in deployment/scripts/, so resolve repo root and run from there
# so relative paths (deployment/k8s/..., test-em-all.sh) keep working.
$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Push-Location $RepoRoot

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function OK($msg)   { Write-Host "OK: $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "WARN: $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "FAIL: $msg" -ForegroundColor Red; Pop-Location; exit 1 }

function Ensure-Jq {
    if (Get-Command jq -ErrorAction SilentlyContinue) {
        OK "jq is available."
        return
    }

    Step "Installing jq"
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        winget install --id jqlang.jq --exact --source winget --accept-source-agreements --accept-package-agreements
        if ($LASTEXITCODE -ne 0) { Fail "winget could not install jq." }
    } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
        choco install jq --yes --no-progress
        if ($LASTEXITCODE -ne 0) { Fail "Chocolatey could not install jq." }
    } else {
        Fail "jq is required, but neither winget nor Chocolatey is available to install it."
    }

    # Package managers may update PATH outside the current PowerShell process.
    $machinePath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = "$machinePath;$userPath;$env:Path"
    if (-not (Get-Command jq -ErrorAction SilentlyContinue)) {
        Fail "jq was installed, but is not available on PATH. Restart PowerShell and rerun this script."
    }
    OK "jq installed."
}

function Add-HostsEntry {
    $ok = $true
    $current = Get-Content $HOSTS_FILE -Raw
    if ($current -notmatch [regex]::Escape($HOSTS_MARKER)) {
        $addCmd = "try { Add-Content -Path '$HOSTS_FILE' -Value '$HOSTS_ENTRY'; exit 0 } catch { exit 1 }"
        $proc = Start-Process powershell -Verb RunAs -ArgumentList "-Command", $addCmd -Wait -PassThru
        if ($proc.ExitCode -ne 0) {
            Warn "Failed to add hosts entries (elevation declined or write failed, exit code $($proc.ExitCode))."
            $ok = $false
        } else {
            OK "Added hosts entries"
        }
    } else {
        Warn "Hosts entries already present - skipping."
    }
    return (Add-WslHostsEntry) -and $ok
}

function Remove-HostsEntry {
    $ok = $true
    $current = Get-Content $HOSTS_FILE -Raw
    if ($current -notmatch [regex]::Escape($HOSTS_MARKER)) {
        Warn "No hosts entries to remove - skipping."
    } else {
        # Writing to the hosts file requires admin rights, same as Add-HostsEntry.
        # Only lines carrying our marker are removed, so unrelated hosts entries are preserved.
        $removeCmd = "try { (Get-Content '$HOSTS_FILE') | Where-Object { `$_ -notmatch [regex]::Escape('$HOSTS_MARKER') } | Set-Content -Path '$HOSTS_FILE'; exit 0 } catch { exit 1 }"
        $proc = Start-Process powershell -Verb RunAs -ArgumentList "-Command", $removeCmd -Wait -PassThru
        if ($proc.ExitCode -ne 0) {
            Warn "Failed to remove hosts entries (elevation declined or write failed, exit code $($proc.ExitCode))."
            $ok = $false
        } else {
            OK "Removed hosts entries."
        }
    }
    return (Remove-WslHostsEntry) -and $ok
}

function Add-WslHostsEntry {
    # WSL auto-generates its own /etc/hosts (separate from Windows), and
    # test-em-all.sh runs inside bash/WSL, so it needs its own entry too.
    # Matched by marker (not the hostname) so unrelated aliases are untouched.
    if (bash -lc "grep -qF '$HOSTS_MARKER' /etc/hosts" 2>$null) {
        Warn "WSL hosts entries already present - skipping."
        return $true
    }
    bash -lc "echo '$HOSTS_ENTRY' | sudo tee -a /etc/hosts > /dev/null"
    if ($LASTEXITCODE -ne 0) {
        Warn "Could not add hosts entries inside WSL (sudo required). test-em-all.sh may fail to resolve retailstore.local."
        return $false
    }
    OK "Added hosts entries inside WSL."
    return $true
}

function Remove-WslHostsEntry {
    if (-not (bash -lc "grep -qF '$HOSTS_MARKER' /etc/hosts" 2>$null)) {
        Warn "No WSL hosts entries to remove - skipping."
        return $true
    }
    # Filter by marker (fixed-string) rather than sed regex, so unrelated aliases are preserved.
    bash -lc "grep -vF '$HOSTS_MARKER' /etc/hosts | sudo tee /etc/hosts.tmp > /dev/null && sudo mv /etc/hosts.tmp /etc/hosts"
    if ($LASTEXITCODE -ne 0) {
        Warn "Could not remove hosts entries inside WSL (sudo required)."
        return $false
    }
    OK "Removed hosts entries inside WSL."
    return $true
}

if ($Teardown) {
    Step "Tearing down Kind cluster"
    kind delete cluster --name $CLUSTER_NAME 2>$null
    $clusterOk = ($LASTEXITCODE -eq 0)
    if (-not $clusterOk) { Warn "kind delete cluster exited with code $LASTEXITCODE." }
    $hostsOk = Remove-HostsEntry
    if ($clusterOk -and $hostsOk) {
        OK "Teardown complete."
        exit 0
    }
    Write-Host "FAIL: Teardown did not complete cleanly." -ForegroundColor Red
    exit 1
}

function Ensure-Jq-InBash {
    if (bash -lc "command -v jq" 2>$null) {
        OK "jq is available inside bash (used by test-em-all.sh)."
        return
    }

    Step "Installing jq inside bash environment"
    bash -lc "sudo -n apt-get update -qq && sudo -n apt-get install -y -qq jq" 2>$null
    if ($LASTEXITCODE -ne 0 -or -not (bash -lc "command -v jq" 2>$null)) {
        Fail "jq is missing inside the bash environment that runs test-em-all.sh, and passwordless sudo is unavailable to install it automatically. Run 'sudo apt-get install -y jq' inside bash/WSL manually, then rerun this script."
    }
    OK "jq installed inside bash."
}

Step "Verifying required tools"
foreach ($tool in @("docker","kind","kubectl","bash")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Fail "Required tool '$tool' not found on PATH."
    }
}
OK "All tools found."
Ensure-Jq
Ensure-Jq-InBash

function Assert-KindContext {
    $expectedContext = "kind-$CLUSTER_NAME"
    $currentContext = kubectl config current-context 2>$null
    if ($LASTEXITCODE -ne 0 -or $currentContext -ne $expectedContext) {
        Fail "Active kube context is '$currentContext', expected '$expectedContext'. Run the script once without -TestOnly/-SkipCluster first."
    }
}

if ($TestOnly) {
    # Skip cluster create/pull/load/apply/rollout - reuse what's already deployed
    # so iterating on test-em-all.sh doesn't pay the ~full setup cost each time.
    Step "Test-only mode: reusing existing cluster and deployments"
    Assert-KindContext
    kubectl get namespace $NAMESPACE 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "Namespace '$NAMESPACE' not found. Run the script once without -TestOnly first." }
    OK "Reusing existing '$CLUSTER_NAME' cluster and '$NAMESPACE' deployments."
} else {
    if (-not $SkipCluster) {
        Step "Creating Kind cluster '$CLUSTER_NAME' with port-mapping config"
        kind delete cluster --name $CLUSTER_NAME 2>$null
        kind create cluster --name $CLUSTER_NAME --config deployment/k8s/kind-config.yaml --wait 120s
        if ($LASTEXITCODE -ne 0) { Fail "kind create cluster failed." }
        OK "Cluster '$CLUSTER_NAME' is up."
    } else {
        Step "Reusing existing Kind cluster (-SkipCluster)"
        Assert-KindContext
        Warn "Skipping cluster creation (-SkipCluster)."
    }

    Step "Installing NGINX Ingress controller"
    # Pinned to the same version used in .github/workflows/k8s-e2e.yml for reproducibility.
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.12.1/deploy/static/provider/kind/deploy.yaml
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

    Step "Waiting for webapp hostAliases patch"
    # This Job patches retail-store-webapp's pod template (hostAliases for
    # keycloak.local), triggering a new rollout. Wait for it here, before any
    # rollout/readiness checks, so those checks see the final pod spec instead
    # of racing a mid-patch rollout.
    kubectl wait --namespace $NAMESPACE --for=condition=complete job/patch-webapp-hostaliases --timeout=120s
    if ($LASTEXITCODE -ne 0) { Fail "patch-webapp-hostaliases job did not complete." }
    OK "Webapp hostAliases patch applied."

    Step "Waiting for infrastructure rollouts"
    kubectl rollout status statefulset/postgresql -n $NAMESPACE --timeout=300s
    if ($LASTEXITCODE -ne 0) { Fail "postgresql rollout failed or timed out." }
    kubectl rollout status deployment/redis -n $NAMESPACE --timeout=300s
    if ($LASTEXITCODE -ne 0) { Fail "redis rollout failed or timed out." }
    kubectl rollout status statefulset/kafka -n $NAMESPACE --timeout=300s
    if ($LASTEXITCODE -ne 0) { Fail "kafka rollout failed or timed out." }
    kubectl rollout status deployment/keycloak -n $NAMESPACE --timeout=300s
    if ($LASTEXITCODE -ne 0) { Fail "keycloak rollout failed or timed out." }
    OK "Infrastructure is ready."

    Step "Waiting for Platform Core rollouts"
    kubectl rollout status deployment/config-server -n $NAMESPACE --timeout=300s
    if ($LASTEXITCODE -ne 0) { Fail "config-server rollout failed or timed out." }
    kubectl rollout status deployment/service-registry -n $NAMESPACE --timeout=300s
    if ($LASTEXITCODE -ne 0) { Fail "service-registry rollout failed or timed out." }
    OK "Platform core is ready."

    Step "Waiting for Application rollouts"
    foreach ($svc in @("catalog-service","inventory-service","order-service","payment-service","api-gateway","retail-store-webapp")) {
        kubectl rollout status deployment/$svc -n $NAMESPACE --timeout=300s
        if ($LASTEXITCODE -ne 0) { Fail "$svc rollout failed or timed out." }
    }
    OK "All application services are ready."
}

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

    # Keycloak token - fetch client secret from the cluster Secret rather than hard-coding it.
    $clientSecretB64 = kubectl get secret webapp-oauth2-credentials -n $NAMESPACE -o jsonpath='{.data.OAUTH2_CLIENT_SECRET}' 2>$null
    $clientSecret = $null
    if ($LASTEXITCODE -eq 0 -and $clientSecretB64) {
        $clientSecret = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($clientSecretB64))
    }
    if (-not $clientSecret) {
        Warn "Could not retrieve Keycloak client secret from Secret 'webapp-oauth2-credentials' - skipping Keycloak smoke check."
    } else {
        $tokenResp = bash -c @"
curl -s -X POST http://keycloak.local/realms/retailstore/protocol/openid-connect/token \
  -d 'client_id=retailstore-webapp' \
  -d 'client_secret=$clientSecret' \
  -d 'grant_type=password' \
  -d 'username=retail' \
  -d 'password=retail1234' | grep -c access_token
"@
        if ($tokenResp -ge 1) { OK "Keycloak token endpoint returned access_token." }
        else                  { Warn "Keycloak token test skipped or non-fatal." }
    }
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
