#!/bin/bash

# Default values
TEST_PROFILE="standard"
BASE_URL="http://localhost:8765"
USERS=50
DURATION=300

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
        -p|--profile)
            TEST_PROFILE="$2"
            shift
            shift
            ;;
        -u|--url)
            BASE_URL="$2"
            shift
            shift
            ;;
        -n|--users)
            USERS="$2"
            shift
            shift
            ;;
        -d|--duration)
            DURATION="$2"
            shift
            shift
            ;;
        -h|--help)
            echo "Gatling Performance Test Runner"
            echo "==============================="
            echo ""
            echo "Usage:"
            echo "  ./run-tests.sh [-p|--profile <profile>] [-u|--url <url>] [-n|--users <number>] [-d|--duration <seconds>] [-h|--help]"
            echo ""
            echo "Parameters:"
            echo "  -p, --profile   Test profile to run (quick, standard, extended, resilience, stress, gateway, all)"
            echo "  -u, --url       Base URL for the API Gateway (default: http://localhost:8765)"
            echo "  -n, --users     Number of users for the test (default: 50)"
            echo "  -d, --duration  Duration of the test in seconds (default: 300)"
            echo "  -h, --help      Display this help message"
            echo ""
            echo "Profiles:"
            echo "  quick      ~1-2 minutes, minimal load for smoke testing"
            echo "  standard   ~3-5 minutes, balanced load for regular verification"
            echo "  extended   ~10+ minutes, sustained load for stability testing"
            echo ""
            echo "Examples:"
            echo "  ./run-tests.sh"
            echo "  ./run-tests.sh -p quick"
            echo "  ./run-tests.sh -p extended -n 100"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Function to check service health
check_health() {
    local service_url=$1
    local max_attempts=10
    local attempt=1
    local sleep_time=5

    echo "Checking health for $service_url..."
    while [ $attempt -le $max_attempts ]; do
        status=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$service_url")
        if [ "$status" == "200" ]; then
            echo "Service $service_url is UP!"
            return 0
        fi
        echo "Attempt $attempt/$max_attempts: $service_url is $status. Retrying in ${sleep_time}s..."
        sleep $sleep_time
        attempt=$((attempt + 1))
    done
    echo "ERROR: Service $service_url failed to become healthy after $max_attempts attempts."
    return 1
}

echo "Starting pre-flight health checks..."
SERVICES=("/actuator/health" "/catalog-service/actuator/health" "/inventory-service/actuator/health" "/order-service/actuator/health" "/payment-service/actuator/health")

for service in "${SERVICES[@]}"; do
    if ! check_health "$service"; then
        echo "Pre-flight checks failed. Aborting."
        exit 1
    fi
done
echo "All services are healthy. Proceeding with tests."

# Set Maven command based on the selected profile
case $TEST_PROFILE in
    "quick")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DrampUsers=2 -DconstantUsers=$USERS -DrampDuration=15 -DtestDuration=$DURATION"
        echo "Running quick test profile (using users=$USERS, duration=${DURATION}s)..."
        ;;
    "standard")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DrampUsers=20 -DconstantUsers=$USERS -DrampDuration=30 -DtestDuration=$DURATION"
        echo "Running standard test profile (using users=$USERS, duration=${DURATION}s)..."
        ;;
    "extended")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DrampUsers=50 -DconstantUsers=$USERS -DrampDuration=60 -DtestDuration=$DURATION"
        echo "Running extended test profile (using users=$USERS, duration=${DURATION}s)..."
        ;;
    "resilience")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DconstantUsers=$USERS -DtestDuration=$DURATION -P resilience"
        echo "Running resilience test profile (using users=$USERS, duration=${DURATION}s)..."
        ;;
    "stress")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DconstantUsers=$USERS -DtestDuration=$DURATION -P stress"
        echo "Running stress test profile (using users=$USERS, duration=${DURATION}s)..."
        ;;
    "gateway")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DburstUsersPerSec=$USERS -DtestDuration=$DURATION -P gateway"
        echo "Running API gateway resilience tests (using burstUsersPerSec=$USERS, duration=${DURATION}s)..."
        ;;
    "all")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DconstantUsers=$USERS -DtestDuration=$DURATION -P all"
        echo "Running all test simulations (using users=$USERS, duration=${DURATION}s)..."
        ;;
    *)
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DrampUsers=10 -DconstantUsers=$USERS -DrampDuration=30 -DtestDuration=$DURATION"
        echo "Running default test profile (using users=$USERS, duration=${DURATION}s)..."
        ;;
esac

# Run the tests
CMD="./mvnw clean gatling:test $MAVEN_PARAMS"
echo "Executing: $CMD"
eval $CMD

# Find the latest report by modification time (ignoring the parent directory)
LATEST_REPORT=$(ls -td target/gatling/*/ 2>/dev/null | head -n 1 | sed 's/\/$//')
if [ -n "$LATEST_REPORT" ] && [ -f "$LATEST_REPORT/index.html" ]; then
    echo "Report generated at: $LATEST_REPORT/index.html"
else
    echo "No test report found in target/gatling/."
fi
