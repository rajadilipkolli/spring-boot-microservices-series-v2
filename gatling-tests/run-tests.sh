#!/bin/bash

# Default values
TEST_PROFILE="default"
BASE_URL="http://localhost:8765"
USERS=10
DURATION=60
KAFKA_INIT_DELAY=15

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
            echo "  -p, --profile   Test profile to run (default, health-check, quick, heavy, resilience, stress, gateway, all)"
            echo "  -u, --url       Base URL for the API Gateway (default: http://localhost:8765)"
            echo "  -n, --users     Number of users for the test (default: 10)"
            echo "  -d, --duration  Duration of the test in seconds (default: 60)"
            echo "  -h, --help      Display this help message"
            echo ""
            echo "Examples:"
            echo "  ./run-tests.sh"
            echo "  ./run-tests.sh -p quick"
            echo "  ./run-tests.sh -p heavy -n 50 -d 300"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Set Maven command based on the selected profile
case $TEST_PROFILE in
    "health-check")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -Dgatling.simulations=simulation.ServiceHealthCheckSimulation"
        echo "Running health check simulation to verify all services are up..."
        ;;
    "quick")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DrampUsers=2 -DconstantUsers=5 -DrampDuration=10 -DtestDuration=30"
        echo "Running quick test profile with minimal load..."
        ;;
    "heavy")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DrampUsers=20 -DconstantUsers=$USERS -DrampDuration=30 -DtestDuration=$DURATION"
        echo "Running heavy test profile with high load..."
        ;;
    "resilience")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -Dusers=$USERS -DtestDuration=$DURATION -P resilience"
        echo "Running resilience test profile to check error handling..."
        ;;
    "stress")
        PLATEAU_MINUTES=$(( DURATION / 60 > 0 ? DURATION / 60 : 1 ))
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DmaxUsers=$USERS -DrampDurationMinutes=2 -DplateauDurationMinutes=$PLATEAU_MINUTES -P stress"
        echo "Running stress test profile with increasing load..."
        ;;
    "gateway")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DburstUsers=$USERS -DsustainSeconds=$DURATION -P gateway"
        echo "Running API gateway resilience tests..."
        ;;
    "all")
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -P all"
        echo "Running all test simulations..."
        ;;
    *)
        MAVEN_PARAMS="-DbaseUrl=$BASE_URL -DrampUsers=5 -DconstantUsers=$USERS -DrampDuration=15 -DtestDuration=$DURATION"
        echo "Running default test profile..."
        ;;
esac

# Run the tests
CMD="./mvnw clean gatling:test $MAVEN_PARAMS"
echo "Executing: $CMD"
eval $CMD

# Find the latest report
LATEST_REPORT=$(find target/gatling -type d -name "createproduct*" -o -name "stress*" -o -name "apiresilient*" -o -name "resilient*" | sort -r | head -n 1)
if [ -n "$LATEST_REPORT" ] && [ -f "$LATEST_REPORT/index.html" ]; then
    echo "Report generated at: $LATEST_REPORT/index.html"
    
    # Try to open the report in a browser
    if command -v xdg-open &> /dev/null; then
        xdg-open "$LATEST_REPORT/index.html"
    elif command -v open &> /dev/null; then
        open "$LATEST_REPORT/index.html"
    else
        echo "Please open the report manually in your browser"
    fi
else
    echo "No test report found."
fi
