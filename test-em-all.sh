#!/usr/bin/env bash
# For Windows PowerShell use:
# $env:HOST="localhost"; $env:PORT="8765"; bash test-em-all.sh
### Sample usage:
#
#   for local run
#     HOST=localhost PORT=8765 ./test-em-all.sh
#   with docker compose
#     HOST=localhost PORT=8765 ./test-em-all.sh start stop
#   for setup with tools
#     HOST=localhost PORT=8765 ./test-em-all.sh setup teardown
#   with custom options
#     ./test-em-all.sh --detailed-logs --timeout 150
#

# Exit script immediately if a command exits with a non-zero status
set -e

# Halt on undefined variables
set -u

# Debug mode - uncomment to see commands being executed
# set -x

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}Starting 'Store μServices' for [end-2-end] testing....${NC}\n"

# Default variables with fallback values using parameter expansion
: ${HOST=localhost}
: ${PORT=8765}
: ${PROD_CODE=P0001}
: ${PROD_CODE_1=P0002}
: ${CUSTOMER_NAME=dockerCustomer001}
: ${SERVICE_WAIT_TIMEOUT=100}
: ${INITIAL_SLEEP_TIME=60}
: ${RETRY_SLEEP_TIME=3}
: ${ORDER_PROCESSING_SLEEP_TIME=3}
: ${KAFKA_STARTUP_SLEEP_TIME=8}
: ${DETAILED_LOGS=false}
: ${PARALLEL_SETUP=false}

# Process command line arguments
for arg in "$@"; do
  case $arg in
    --detailed-logs)
      DETAILED_LOGS=true
      shift
      ;;
    --parallel-setup)
      PARALLEL_SETUP=true
      shift
      ;;
    --timeout=*)
      SERVICE_WAIT_TIMEOUT="${arg#*=}"
      shift
      ;;
    --initial-wait=*)
      INITIAL_SLEEP_TIME="${arg#*=}"
      shift
      ;;
    --retry-wait=*)
      RETRY_SLEEP_TIME="${arg#*=}"
      shift
      ;;
    --process-wait=*)
      ORDER_PROCESSING_SLEEP_TIME="${arg#*=}"
      shift
      ;;
  esac
done

# Deployment file locations
DOCKER_COMPOSE_FILE="deployment/docker-compose.yml"
DOCKER_COMPOSE_TOOLS_FILE="deployment/docker-compose-tools.yml"

# Global tracking variables
TEST_STATUS=0
START_TIME=$(date +%s)
TEST_RESULTS=()
FAILED_TESTS=()
TOTAL_TESTS=0
PASSED_TESTS=0

# Performance metrics
SETUP_START_TIME=0
SETUP_END_TIME=0
API_VERIFY_START_TIME=0
API_VERIFY_END_TIME=0

# Function to display error messages and exit
function error_exit() {
  local message="$1"
  local exit_code="${2:-1}" # Default exit code is 1
  
  echo -e "\n${RED}❌ ERROR: ${message}${NC}" >&2
  
  # Print test summary before exiting
  print_summary
  
  exit "${exit_code}"
}

# Function to display info messages with timestamp
function log_info() {
  local message="$1"
  echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] ${BLUE}INFO: ${message}${NC}"
}

# Function to display success messages
function log_success() {
  local message="$1"
  echo -e "${GREEN}✓ SUCCESS: ${message}${NC}"
}

# Function to display warning messages
function log_warning() {
  local message="$1"
  echo -e "${YELLOW}⚠️ WARNING: ${message}${NC}"
}

# Function to track test result
function track_test_result() {
  local name="$1"
  local result="$2"
  local details="${3:-}"
  
  TOTAL_TESTS=$((TOTAL_TESTS + 1))
  TEST_RESULTS+=("$name: $result")
  
  if [[ "$result" == "PASS" ]]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
  else
    FAILED_TESTS+=("$name")
  fi

  if [[ "$DETAILED_LOGS" == "true" && -n "$details" ]]; then
    echo -e "${CYAN}Details: ${details}${NC}"
  fi
}

# Function to print test summary
function print_summary() {
  local end_time=$(date +%s)
  local time_taken=$((end_time - START_TIME))
  
  echo -e "\n${BLUE}=============================================="
  echo -e "TEST SUMMARY"
  echo -e "===============================================${NC}"
  echo -e "Total tests run: ${TOTAL_TESTS}"
  echo -e "Tests passed: ${GREEN}${PASSED_TESTS}${NC}"
  
  if [[ ${#FAILED_TESTS[@]} -gt 0 ]]; then
    echo -e "Tests failed: ${RED}$((TOTAL_TESTS - PASSED_TESTS))${NC}"
    echo -e "${RED}Failed tests: ${FAILED_TESTS[*]}${NC}"
  else
    echo -e "All tests passed! 🎉"
  fi
  
  # Print performance metrics if available
  if [[ $SETUP_START_TIME -gt 0 && $SETUP_END_TIME -gt 0 ]]; then
    echo -e "Setup time: $((SETUP_END_TIME - SETUP_START_TIME)) seconds"
  fi
  
  if [[ $API_VERIFY_START_TIME -gt 0 && $API_VERIFY_END_TIME -gt 0 ]]; then
    echo -e "API verification time: $((API_VERIFY_END_TIME - API_VERIFY_START_TIME)) seconds"
  fi
  
  echo -e "Total execution time: ${time_taken} seconds"
  echo -e "${BLUE}===============================================${NC}"
}

function assertCurl() {
  local expectedHttpCode=$1
  local curlCmd="$2 -w \"%{http_code}\""
  local testName="${3:-API Call}"
  local result
  
  # Use command substitution with error handling
  if ! result=$(eval ${curlCmd} 2>/dev/null); then
    echo -e "${RED}Curl command failed: $curlCmd${NC}"
    track_test_result "$testName" "FAIL" "Curl command failed"
    TEST_STATUS=1
    return 1
  fi
  
  local httpCode="${result:(-3)}"
  RESPONSE='' && (( ${#result} > 3 )) && RESPONSE="${result%???}"

  if [[ "$httpCode" = "$expectedHttpCode" ]]; then
    if [[ "$httpCode" = "200" ]]; then
      echo -e "${GREEN}Test OK (HTTP Code: $httpCode)${NC}"
    else
      echo -e "${YELLOW}Test OK (HTTP Code: $httpCode, $RESPONSE)${NC}"
    fi
    track_test_result "$testName" "PASS" "Expected: $expectedHttpCode, Got: $httpCode"
    return 0
  else
      echo -e "${RED}Test FAILED, EXPECTED HTTP Code: $expectedHttpCode, GOT: $httpCode, WILL ABORT!${NC}"
      echo -e "${RED}- Failing command: $curlCmd${NC}"
      echo -e "${RED}- Response Body: $RESPONSE${NC}"
      track_test_result "$testName" "FAIL" "Expected: $expectedHttpCode, Got: $httpCode"
      TEST_STATUS=1
      return 1
  fi
}

function assertEqual() {
  local expected=$1
  local actual=$2
  local testName="${3:-Value comparison}"

  if [[ "$actual" = "$expected" ]]; then
    echo -e "${GREEN}Test OK (actual value: $actual)${NC}"
    track_test_result "$testName" "PASS" "Expected: $expected, Got: $actual"
    return 0
  else
    echo -e "${RED}Test FAILED, EXPECTED VALUE: $expected, ACTUAL VALUE: $actual, WILL ABORT${NC}"
    track_test_result "$testName" "FAIL" "Expected: $expected, Got: $actual"
    TEST_STATUS=1
    return 1
  fi
}

function testUrl() {
    local url=$@
    if curl ${url} -ks -f -o /dev/null; then
          return 0
    else
          return 1
    fi
}

function waitForService() {
    local url=$@
    local service_name=$(echo $url | grep -o -E '[^/]+/actuator/health' || echo "Service")
    
    echo -n "${CYAN}Wait for: $service_name... ${NC}"
    n=0
    until testUrl ${url}
    do
        n=$((n + 1))
        if [[ ${n} == ${SERVICE_WAIT_TIMEOUT} ]]; then
            echo -e "${RED}Give up after ${SERVICE_WAIT_TIMEOUT} attempts${NC}"
            track_test_result "Service availability: $service_name" "FAIL" "Timed out after ${SERVICE_WAIT_TIMEOUT} attempts"
            TEST_STATUS=1
            return 1
        else
            # Dynamically adjust sleep time based on attempt count
            local current_sleep_time=${RETRY_SLEEP_TIME}
            if [[ ${n} -gt 20 ]]; then
                current_sleep_time=$((RETRY_SLEEP_TIME * 2))
            fi
            sleep ${current_sleep_time}
            echo -n "${YELLOW}, retry #$n ${NC}"
        fi
    done
    echo -e "\n${GREEN}DONE, service is available.${NC}\n"
    track_test_result "Service availability: $service_name" "PASS"
    return 0
}

function recreateComposite() {
    local identifier=$1
    local composite=$2
    local baseURL=$3
    local methodType=$4
    local testName="API Request to $baseURL"

    echo -e "${CYAN}Calling URL http://${HOST}:${PORT}/${baseURL} with body -${NC} $composite"
    COMPOSITE_RESPONSE=$(curl -X ${methodType} -k http://${HOST}:${PORT}/${baseURL} -H "Content-Type: application/json" \
    --data "$composite")

    # Check if curl was successful
    if [ $? -ne 0 ]; then
        echo -e "${RED}Error: API call failed${NC}"
        track_test_result "$testName" "FAIL" "API call failed"
        TEST_STATUS=1
        return 1
    fi

    echo -e "${GREEN}Response from caller -${NC} ${COMPOSITE_RESPONSE}"
    track_test_result "$testName" "PASS"
    echo " "
}

function setupTestData() {
    log_info "Setting up product data..."
    SETUP_START_TIME=$(date +%s)

    # Product 1
    body="{\"productCode\":\"$PROD_CODE"
    body+=\
'","productName":"product name A","price":100, "imageUrl":"https://www.ikea.com/in/en/images/products/saellskaplig-jug-patterned-green__0941744_pe795674_s5.jpg?f=xl","description": "A Beautiful Product"}'

    # Creating Product
    log_info "Creating product with code - $PROD_CODE"
    recreateComposite "$PROD_CODE" "$body" "catalog-service/api/catalog" "POST" || return 1    # Product 2
    body="{\"productCode\":\"$PROD_CODE_1"
    body+=\
'","productName":"product name B","price":9.99, "imageUrl":"https://cdn.igp.com/f_auto,q_auto,t_pnopt12prodlp/products/p-you-are-my-penguin-personalized-magic-mug-265224-m.jpg","description": "Nice Product"}'

    # Creating Product
    log_info "Creating product with code - $PROD_CODE_1"
    recreateComposite "$PROD_CODE_1" "$body" "catalog-service/api/catalog" "POST" || return 1

    # Waiting for kafka to process the catalog creation request, as it is first time kafka initialization takes time
    log_info "Waiting for Kafka to process catalog creation (${RETRY_SLEEP_TIME}s)..."
    sleep ${RETRY_SLEEP_TIME}
    
    # Verify that a normal request works, expect record exists with product code
    assertCurl 200 "curl -k http://$HOST:$PORT/inventory-service/api/inventory/$PROD_CODE" || return 1
    assertEqual \"${PROD_CODE}\" $(echo ${RESPONSE} | jq .productCode) || return 1

    body="{\"productCode\":\"$PROD_CODE"
    body+=\
'","availableQuantity":100}'

    # Update the product available Quantity
    recreateComposite $(echo "$RESPONSE" | jq -r .id) "$body" "inventory-service/api/inventory/$(echo "$RESPONSE" | jq -r .id)" "PUT" || return 1

    # Verify that a normal request works, expect record exists with product code_1
    assertCurl 200 "curl -k http://$HOST:$PORT/inventory-service/api/inventory/$PROD_CODE_1" || return 1
    assertEqual \"${PROD_CODE_1}\" $(echo ${RESPONSE} | jq .productCode) || return 1

    body="{\"productCode\":\"$PROD_CODE_1"
    body+=\
'","availableQuantity":50}'

    # Update the product_1 available Quantity
    recreateComposite $(echo "$RESPONSE" | jq -r .id) "$body" "inventory-service/api/inventory/$(echo "$RESPONSE" | jq -r .id)" "PUT" || return 1

    # Verify that communication between catalog-service and inventory service is established
    assertCurl 200 "curl -k http://$HOST:$PORT/catalog-service/api/catalog/productCode/$PROD_CODE_1?fetchInStock=true" || return 1
    assertEqual \"${PROD_CODE_1}\" $(echo ${RESPONSE} | jq .productCode) || return 1
    assertEqual true $(echo ${RESPONSE} | jq .inStock) || return 1

    echo "Setting up customer data..."
    body="{\"name\": \"$CUSTOMER_NAME"
    body+=\
'","email": "docker@email.com","phone":"9876543210","address": "docker Address","amountAvailable":1000}'

    # Creating Customer
    recreateComposite "$CUSTOMER_NAME" "$body" "payment-service/api/customers" "POST" || return 1
    CUSTOMER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .customerId)
      # Check if customer ID was retrieved successfully
    if [[ -z "$CUSTOMER_ID" || "$CUSTOMER_ID" == "null" ]]; then
        log_warning "Failed to get valid customer ID"
        track_test_result "Customer creation" "FAIL" "Invalid customer ID returned"
        return 1
    fi
    
    SETUP_END_TIME=$(date +%s)
    log_success "Test data setup completed successfully in $((SETUP_END_TIME - SETUP_START_TIME)) seconds."
}

function verifyAPIs() {
    log_info "Running API verification tests..."
    API_VERIFY_START_TIME=$(date +%s)
      # Step 1 Creating Order and it should be CONFIRMED
    log_info "Step 1: Testing order confirmation process..."
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 10,"productPrice": 5}],"deliveryAddress": {"addressLine1": "string","addressLine2": "string","city": "string","state": "string","zipCode": "string","country": "string"}}'

    echo " "
    # Creating Order
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST" || return 1

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)
      # Check if order ID was retrieved successfully
    if [[ -z "$ORDER_ID" || "$ORDER_ID" == "null" ]]; then
        log_warning "Failed to get valid order ID"
        track_test_result "Order creation" "FAIL" "Invalid order ID returned"
        return 1
    }

    log_info "Sleeping for ${KAFKA_STARTUP_SLEEP_TIME} sec as it is first order, letting kafka start in all services. Processing orderId - $ORDER_ID"
    sleep ${KAFKA_STARTUP_SLEEP_TIME}    # Verify that order processing is completed and status is CONFIRMED
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID" "Order status check for $ORDER_ID" || return 1
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId) "Order ID check" || return 1
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId) "Customer ID check" || return 1
    assertEqual \"CONFIRMED\" $(echo ${RESPONSE} | jq .status) "Order status check" || return 1
    assertEqual null $(echo ${RESPONSE} | jq .source) "Order source check" || return 1

    # Verify that amountAvailable is deducted as per order
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID" "Customer payment check" || return 1
    assertEqual 950.0 $(echo ${RESPONSE} | jq .amountAvailable) "Customer balance check" || return 1

    # Step2, Order Should be rejected
    log_info "Step 2: Testing order rejection due to insufficient inventory..."
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 100,"productPrice": 5}],"deliveryAddress": {"addressLine1": "string","addressLine2": "string","city": "string","state": "string","zipCode": "string","country": "string"}}'

    echo " "
    # Creating 2nd Order, this should ROLLBACK as Inventory is not available
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST" || return 1

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for ${ORDER_PROCESSING_SLEEP_TIME} sec for processing of orderId -" $ORDER_ID
    sleep ${ORDER_PROCESSING_SLEEP_TIME}

    # Verify that order processing is completed and status is ROLLBACK
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID" || return 1
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId) || return 1
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId) || return 1
    assertEqual \"ROLLBACK\" $(echo ${RESPONSE} | jq .status) || return 1
    assertEqual \"INVENTORY\" $(echo ${RESPONSE} | jq .source) || return 1

    # Verify that amountAvailable is not deducted as per order
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID" || return 1
    assertEqual 950.0 $(echo ${RESPONSE} | jq .amountAvailable) || return 1

    # Step 3, Order Should be CONFIRMED 
    echo "Step 3: Testing another order confirmation..."
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 80,"productPrice": 10}],"deliveryAddress": {"addressLine1": "string","addressLine2": "string","city": "string","state": "string","zipCode": "string","country": "string"}}'

    echo " "
    # Creating 3rd Order, this should CONFIRMED as Inventory is available
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST" || return 1

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for ${ORDER_PROCESSING_SLEEP_TIME} sec for processing of orderId -" $ORDER_ID
    sleep ${ORDER_PROCESSING_SLEEP_TIME}

    # Verify that order processing is completed and status is CONFIRMED
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID" || return 1
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId) || return 1
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId) || return 1
    assertEqual \"CONFIRMED\" $(echo ${RESPONSE} | jq .status) || return 1
    assertEqual null $(echo ${RESPONSE} | jq .source) || return 1

    # Verify that amountAvailable is deducted as per order
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID" || return 1
    assertEqual 150.0 $(echo ${RESPONSE} | jq .amountAvailable) || return 1

    # Step 4, Order Should be ROLLBACK 
    echo "Step 4: Testing order rollback due to payment issues..."
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 8,"productPrice": 20}],"deliveryAddress": {"addressLine1": "string","addressLine2": "string","city": "string","state": "string","zipCode": "string","country": "string"}}'

    echo " "
    # Creating 4th Order, this should ROLLBACK as amount is not available for customer
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST" || return 1

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for ${ORDER_PROCESSING_SLEEP_TIME} sec for processing of orderId -" $ORDER_ID
    sleep ${ORDER_PROCESSING_SLEEP_TIME}

    # Verify that order processing is completed and status is ROLLBACK
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID" || return 1
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId) || return 1
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId) || return 1
    assertEqual \"ROLLBACK\" $(echo ${RESPONSE} | jq .status) || return 1
    assertEqual \"PAYMENT\" $(echo ${RESPONSE} | jq .source) || return 1

    # Verify that amountAvailable is not deducted as per order cant be processed
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID" || return 1
    assertEqual 150.0 $(echo ${RESPONSE} | jq .amountAvailable) || return 1

    # Step 5, Order Should be REJECTED 
    echo "Step 5: Testing order rejection..."
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 20,"productPrice": 20}],"deliveryAddress": {"addressLine1": "string","addressLine2": "string","city": "string","state": "string","zipCode": "string","country": "string"}}'

    echo " "
    # Creating 5th Order, this should Reject as amount is not available for customer & inventory not available
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST" || return 1

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for ${ORDER_PROCESSING_SLEEP_TIME} sec for processing of orderId -" $ORDER_ID
    sleep ${ORDER_PROCESSING_SLEEP_TIME}

    # Verify that order processing is completed and status is ROLLBACK
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID" || return 1
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId) || return 1
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId) || return 1
    assertEqual \"REJECTED\" $(echo ${RESPONSE} | jq .status) || return 1
    assertEqual \"INVENTORY\" $(echo ${RESPONSE} | jq .source) || return 1

    # Verify that amountAvailable is not deducted as per order cant be processed
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID" || return 1
    assertEqual 150.0 $(echo ${RESPONSE} | jq .amountAvailable) || return 1

    echo " "

    # Step 6 Creating Order and it should be CONFIRMED
    echo "Step 6: Testing multi-product order confirmation..."
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 1,"productPrice": 10},{"productCode": '
      body+="\"$PROD_CODE_1"
      body+=\
'","quantity": 5,"productPrice": 10}],"deliveryAddress": {"addressLine1": "string","addressLine2": "string","city": "string","state": "string","zipCode": "string","country": "string"}}'

    echo " "
    # Creating Order
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST" || return 1

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for ${ORDER_PROCESSING_SLEEP_TIME} sec for processing orderId -" $ORDER_ID
    sleep ${ORDER_PROCESSING_SLEEP_TIME}

    # Verify that order processing is completed and status is CONFIRMED
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID" || return 1
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId) || return 1
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId) || return 1
    assertEqual \"CONFIRMED\" $(echo ${RESPONSE} | jq .status) || return 1
    assertEqual null $(echo ${RESPONSE} | jq .source) || return 1

    # Verify that amountAvailable is deducted as per order
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID" || return 1
    assertEqual 90.0 $(echo ${RESPONSE} | jq .amountAvailable) || return 1

    echo " "

    # Step 7 Creating Order with one available and it should be ROLLBACK
    echo "Step 7: Testing mixed availability order rejection..."
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 1,"productPrice": 10},{"productCode": '
      body+="\"$PROD_CODE_1"
      body+=\
'","quantity": 500,"productPrice": 9.99}],"deliveryAddress": {"addressLine1": "string","addressLine2": "string","city": "string","state": "string","zipCode": "string","country": "string"}}'

    echo " "
    # Creating Order
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST" || return 1

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for ${ORDER_PROCESSING_SLEEP_TIME} sec for processing orderId -" $ORDER_ID
    sleep ${ORDER_PROCESSING_SLEEP_TIME}

    # Verify that order processing is completed and status is REJECTED
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID" || return 1
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId) || return 1
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId) || return 1
    assertEqual \"REJECTED\" $(echo ${RESPONSE} | jq .status) || return 1
    assertEqual \"INVENTORY\" $(echo ${RESPONSE} | jq .source) || return 1

    # Verify that amountAvailable is not deducted as per order
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID" || return 1
    assertEqual 90.0 $(echo ${RESPONSE} | jq .amountAvailable) || return 1
    
    API_VERIFY_END_TIME=$(date +%s)
    log_success "All API verification tests completed successfully in $((API_VERIFY_END_TIME - API_VERIFY_START_TIME)) seconds."
}

# Function to display help message
function display_help() {
    echo -e "${BLUE}Store Microservices Integration Tests${NC}"
    echo -e "${BLUE}=====================================${NC}"
    echo -e "Usage: ./test-em-all.sh [options] [commands]"
    echo -e "\nOptions:"
    echo -e "  --detailed-logs     Show detailed logs including request/response data"
    echo -e "  --parallel-setup    Run setup tasks in parallel when possible"
    echo -e "  --timeout=<number>  Service wait timeout in seconds (default: $SERVICE_WAIT_TIMEOUT)"
    echo -e "  --initial-wait=<seconds>  Initial wait time for services to start (default: $INITIAL_SLEEP_TIME)"
    echo -e "  --retry-wait=<seconds>    Time between service checks (default: $RETRY_SLEEP_TIME)"
    echo -e "  --process-wait=<seconds>  Time to wait for order processing (default: $ORDER_PROCESSING_SLEEP_TIME)"
    echo -e "  --help              Display this help message"
    echo -e "\nCommands:"
    echo -e "  start               Start the test environment using docker compose"
    echo -e "  stop                Stop the test environment after tests"
    echo -e "  setup               Set up the test environment using tools compose file"
    echo -e "  teardown            Tear down the test environment after tests"
    echo -e "\nExamples:"
    echo -e "  HOST=localhost PORT=8765 ./test-em-all.sh"
    echo -e "  ./test-em-all.sh start stop"
    echo -e "  ./test-em-all.sh setup teardown --detailed-logs"
    echo -e "  ./test-em-all.sh --timeout=150 --initial-wait=90"
    exit 0
}

# Check for help command
for arg in "$@"; do
  if [[ "$arg" == "--help" ]]; then
    display_help
  fi
done

# Check if jq is installed
command -v jq > /dev/null 2>&1 || { 
  error_exit "jq is required but not installed. Please install jq first." 
}

# Check for Windows environment and print helper message for PowerShell
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
  log_info "Detected Windows environment. If using PowerShell, set variables with:"
  log_info '$env:HOST="localhost"; $env:PORT="8765"; bash test-em-all.sh'
fi

echo -e "${BLUE}===============================================${NC}"
echo -e "${BLUE}Starting Store Microservices Integration Tests${NC}"
echo -e "${BLUE}===============================================${NC}"
echo -e "Start Tests: $(date)"

echo -e "HOST=${HOST}"
echo -e "PORT=${PORT}"
echo -e "DETAILED_LOGS=${DETAILED_LOGS}"
echo -e "SERVICE_WAIT_TIMEOUT=${SERVICE_WAIT_TIMEOUT}"

# Handle docker compose operations based on command line arguments
if [[ $@ == *"start"* ]]; then
    log_info "Restarting the test environment..."
    echo "$ docker compose -f ${DOCKER_COMPOSE_FILE} down --remove-orphans -v"
    docker compose -f ${DOCKER_COMPOSE_FILE} down --remove-orphans -v
    echo "$ docker compose up -d"
    docker compose -f ${DOCKER_COMPOSE_FILE} up -d
fi

if [[ $@ == *"setup"* ]]; then
    log_info "Restarting the test environment..."
    echo "$ docker compose -f ${DOCKER_COMPOSE_TOOLS_FILE} down --remove-orphans -v"
    docker compose -f ${DOCKER_COMPOSE_TOOLS_FILE} down --remove-orphans -v
    echo "$ docker compose up -d"
    docker compose -f ${DOCKER_COMPOSE_TOOLS_FILE} up -d
fi

# Wait for gateway health check endpoint
log_info "Checking API Gateway availability..."
waitForService curl -k http://${HOST}:${PORT}/actuator/health || error_exit "Gateway service is not available"

# Waiting for services to come up
log_info "Sleeping for ${INITIAL_SLEEP_TIME} sec for services to start"
sleep ${INITIAL_SLEEP_TIME}

# Check all required services 
log_info "Checking service health..."
waitForService curl -k http://${HOST}:${PORT}/CATALOG-SERVICE/catalog-service/actuator/health || error_exit "Catalog service is not available"
waitForService curl -k http://${HOST}:${PORT}/INVENTORY-SERVICE/inventory-service/actuator/health || error_exit "Inventory service is not available"
waitForService curl -k http://${HOST}:${PORT}/ORDER-SERVICE/order-service/actuator/health || error_exit "Order service is not available"
waitForService curl -k http://${HOST}:${PORT}/PAYMENT-SERVICE/payment-service/actuator/health || error_exit "Payment service is not available"

log_info "Setting up test data..."
setupTestData || error_exit "Test data setup failed!"

log_info "Verifying APIs..."
verifyAPIs || error_exit "API verification failed!"

echo -e "End, all tests OK: $(date)"
echo -e "${GREEN}===============================================${NC}"
echo -e "${GREEN}✅ Tests completed successfully${NC}"
echo -e "${GREEN}===============================================${NC}"

# Clean up based on command line arguments
if [[ $@ == *"stop"* ]]; then
    log_info "We are done, stopping the test environment..."
    echo "$ docker compose -f ${DOCKER_COMPOSE_FILE} down --remove-orphans -v"
    docker compose -f ${DOCKER_COMPOSE_FILE} down --remove-orphans -v
fi

if [[ $@ == *"teardown"* ]]; then
    log_info "We are done, stopping the test environment..."
    echo "$ docker compose -f ${DOCKER_COMPOSE_TOOLS_FILE} down --remove-orphans -v"
    docker compose -f ${DOCKER_COMPOSE_TOOLS_FILE} down --remove-orphans -v
fi

# Print final summary
print_summary

exit ${TEST_STATUS}
