#!/usr/bin/env bash
# Read-only verification for a full application overlay. Set KUBE_CONTEXT to
# verify a cluster other than the current kubectl context.
set -euo pipefail

overlay="${1:-dev}"
case "$overlay" in
  dev|ci|staging|prod) ;;
  *) echo "Usage: $0 [dev|ci|staging|prod]" >&2; exit 1 ;;
esac

context=()
if [[ -n "${KUBE_CONTEXT:-}" ]]; then
  context=(--context "$KUBE_CONTEXT")
fi

wait_for_resource() {
  local resource="$1" required="$2" scope="$3" section="${4:-}"
  local -a ns=()
  if [[ "$resource" != gatewayclass/* ]]; then
    ns=(--namespace retailstore)
  fi
  local deadline=$((SECONDS + 60)) remaining snapshot last_result
  echo "Checking $resource: $required"
  while (( (remaining = deadline - SECONDS) > 0 )); do
    # Keep each attempt bounded and preserve API errors for timeout diagnostics.
    if (( remaining > 10 )); then remaining=10; fi
    if snapshot=$(kubectl "${context[@]}" get "$resource" "${ns[@]}" \
      --request-timeout="${remaining}s" -o json); then
      last_result="$snapshot"
      if jq -e --arg required "$required" --arg scope "$scope" --arg section "$section" '
        .metadata.generation as $generation |
        def ready($types):
          . as $conditions |
          all($types | split(" ")[]; . as $type |
            any($conditions[]?; .type == $type and .status == "True"
              and .observedGeneration == $generation));
        def intended_gateway:
          .name == "retailstore-gateway" and (.namespace // "retailstore") == "retailstore"
          and (.group // "gateway.networking.k8s.io") == "gateway.networking.k8s.io"
          and (.kind // "Gateway") == "Gateway";
        def envoy: .controllerName == "gateway.envoyproxy.io/gatewayclass-controller";
        ($generation | type == "number") and (
          if $scope == "route" then
            any(.status.parents[]?; envoy and (.parentRef | intended_gateway
              and (.sectionName // "") == $section) and (.conditions | ready($required)))
          elif $scope == "policy" then
            any(.status.ancestors[]?; envoy and (.ancestorRef | intended_gateway)
              and (.conditions | ready($required)))
          else
            (.status.conditions | ready($required)) and (
              if $scope == "gateway" and $section != "" then
                any(.status.listeners[]?; .name == $section
                  and (.conditions | ready("Accepted Programmed ResolvedRefs")))
              else true
              end
            )
          end
        )
      ' <<< "$snapshot" >/dev/null; then
        return 0
      fi
    else
      last_result="kubectl get failed for $resource (see API error above)"
    fi
    if (( deadline - SECONDS > 1 )); then sleep 2; else sleep 1; fi
  done

  echo "ERROR: $resource did not report current $required conditions within 60s." >&2
  printf '%s\n' "${last_result:-No successful read}" >&2
  kubectl "${context[@]}" logs -n envoy-gateway-system deployment/envoy-gateway \
    --request-timeout=10s --tail=100 >&2 || true
  return 1
}

wait_for_resource gatewayclass/eg Accepted resource
section=""
if [[ "$overlay" == prod ]]; then
  section=https
fi
wait_for_resource gateway/retailstore-gateway 'Accepted Programmed' gateway "$section"
if [[ "$overlay" == prod ]]; then
  wait_for_resource httproute/retailstore-https-redirect 'Accepted ResolvedRefs' route http
fi

# These named resources are present in every full application overlay. Read
# each directly so a failed list, or an empty list, cannot skip a required check.
for route in retailstore-webapp-route api-gateway-route keycloak-route internal-jobrunr-route; do
  wait_for_resource "httproute/$route" 'Accepted ResolvedRefs' route "$section"
done
wait_for_resource backendtrafficpolicy/base-traffic-policy Accepted policy
wait_for_resource securitypolicy/jobrunr-security-policy Accepted policy
