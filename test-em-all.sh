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
#

# Exit script immediately if a command exits with a non-zero status
set -e

# Halt on undefined variables
set -u

# Debug mode - uncomment to see commands being executed
# set -x

echo -e "Starting 'Store μServices' for [end-2-end] testing....\n"

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

# Deployment file locations
DOCKER_COMPOSE_FILE="deployment/docker-compose.yml"
DOCKER_COMPOSE_TOOLS_FILE="deployment/docker-compose-tools.yml"

# Global status tracking
TEST_STATUS=0

# Function to display error messages and exit
function error_exit() {
  local message="$1"
  local exit_code="${2:-1}" # Default exit code is 1
  
  echo -e "\n❌ ERROR: ${message}" >&2
  exit "${exit_code}"
}

function assertCurl() {
  local expectedHttpCode=$1
  local curlCmd="$2 -w \"%{http_code}\""
  local result
  
  # Use command substitution with error handling
  if ! result=$(eval ${curlCmd} 2>/dev/null); then
    echo "Curl command failed: $curlCmd"
    TEST_STATUS=1
    return 1
  fi
  
  local httpCode="${result:(-3)}"
  RESPONSE='' && (( ${#result} > 3 )) && RESPONSE="${result%???}"

  if [[ "$httpCode" = "$expectedHttpCode" ]]; then
    if [[ "$httpCode" = "200" ]]; then
      echo "Test OK (HTTP Code: $httpCode)"
    else
      echo "Test OK (HTTP Code: $httpCode, $RESPONSE)"
    fi
    return 0
  else
      echo  "Test FAILED, EXPECTED HTTP Code: $expectedHttpCode, GOT: $httpCode, WILL ABORT!"
      echo  "- Failing command: $curlCmd"
      echo  "- Response Body: $RESPONSE"
      TEST_STATUS=1
      return 1
  fi
}

function assertEqual() {
  local expected=$1
  local actual=$2

  if [[ "$actual" = "$expected" ]]; then
    echo "Test OK (actual value: $actual)"
    return 0
  else
    echo "Test FAILED, EXPECTED VALUE: $expected, ACTUAL VALUE: $actual, WILL ABORT"
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
    echo -n "Wait for: $url... "
    n=0
    until testUrl ${url}
    do
        n=$((n + 1))
        if [[ ${n} == ${SERVICE_WAIT_TIMEOUT} ]]; then
            echo " Give up after ${SERVICE_WAIT_TIMEOUT} attempts"
            TEST_STATUS=1
            return 1
        else
            sleep ${RETRY_SLEEP_TIME}
            echo -n ", retry #$n "
        fi
    done
    echo -e "\n DONE, continues...\n"
    return 0
}

function recreateComposite() {
    local identifier=$1
    local composite=$2
    local baseURL=$3
    local methodType=$4

    echo "Calling URL http://${HOST}:${PORT}/${baseURL} with body -" $composite
    COMPOSITE_RESPONSE=$(curl -X ${methodType} -k http://${HOST}:${PORT}/${baseURL} -H "Content-Type: application/json" \
    --data "$composite")

    # Check if curl was successful
    if [ $? -ne 0 ]; then
        echo "Error: API call failed"
        TEST_STATUS=1
        return 1
    fi

    echo "Response from caller - " ${COMPOSITE_RESPONSE}
    echo " "
}

function setupTestData() {
    echo "Setting up product data..."
    body="{\"productCode\":\"$PROD_CODE"
    body+=\
'","productName":"product name A","price":100, "imageUrl":"https://www.ikea.com/in/en/images/products/saellskaplig-jug-patterned-green__0941744_pe795674_s5.jpg?f=xl","description": "A Beautiful Product"}'

    # Creating Product
    echo "Creating product with code - " $PROD_CODE
    recreateComposite "$PROD_CODE" "$body" "catalog-service/api/catalog" "POST" || return 1

    body="{\"productCode\":\"$PROD_CODE_1"
    body+=\
'","productName":"product name B","price":9.99, "imageUrl":"https://cdn.igp.com/f_auto,q_auto,t_pnopt12prodlp/products/p-you-are-my-penguin-personalized-magic-mug-265224-m.jpg","description": "Nice Product"}'

    # Creating Product
    echo "Creating product with code - " $PROD_CODE_1
    recreateComposite "$PROD_CODE_1" "$body" "catalog-service/api/catalog" "POST" || return 1

    # Waiting for kafka to process the catalog creation request, as it is first time kafka initialization takes time
    echo "Waiting for Kafka to process catalog creation (${RETRY_SLEEP_TIME}s)..."
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
        echo "Failed to get valid customer ID"
        return 1
    fi
    
    echo "Test data setup completed successfully."
}

function verifyAPIs() {
    echo "Running API verification tests..."
    
    # Step 1 Creating Order and it should be CONFIRMED
    echo "Step 1: Testing order confirmation process..."
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
        echo "Failed to get valid order ID"
        return 1
    fi

    echo "Sleeping for ${KAFKA_STARTUP_SLEEP_TIME} sec as it is first order, letting kafka start in all services. Processing orderId -" $ORDER_ID
    sleep ${KAFKA_STARTUP_SLEEP_TIME}

    # Verify that order processing is completed and status is CONFIRMED
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID" || return 1
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId) || return 1
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId) || return 1
    assertEqual \"CONFIRMED\" $(echo ${RESPONSE} | jq .status) || return 1
    assertEqual null $(echo ${RESPONSE} | jq .source) || return 1

    # Verify that amountAvailable is deducted as per order
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID" || return 1
    assertEqual 950.0 $(echo ${RESPONSE} | jq .amountAvailable) || return 1

    # Step2, Order Should be rejected
    echo "Step 2: Testing order rejection due to insufficient inventory..."
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
    assertEqual null $(echo ${RESPONSE} | jq .source) || return 1

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
    assertEqual null $(echo ${RESPONSE} | jq .source) || return 1

    # Verify that amountAvailable is not deducted as per order
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID" || return 1
    assertEqual 90.0 $(echo ${RESPONSE} | jq .amountAvailable) || return 1
    
    echo "All API verification tests completed successfully."
}

# Check if jq is installed
command -v jq > /dev/null 2>&1 || { 
  error_exit "jq is required but not installed. Please install jq first." 
}

echo "=============================================="
echo "Starting Store Microservices Integration Tests"
echo "=============================================="
echo "Start Tests:" `date`

echo "HOST=${HOST}"
echo "PORT=${PORT}"

# Handle docker compose operations based on command line arguments
if [[ $@ == *"start"* ]]; then
    echo "Restarting the test environment..."
    echo "$ docker compose -f ${DOCKER_COMPOSE_FILE} down --remove-orphans -v"
    docker compose -f ${DOCKER_COMPOSE_FILE} down --remove-orphans -v
    echo "$ docker compose up -d"
    docker compose -f ${DOCKER_COMPOSE_FILE} up -d
fi

if [[ $@ == *"setup"* ]]; then
    echo "Restarting the test environment..."
    echo "$ docker compose -f ${DOCKER_COMPOSE_TOOLS_FILE} down --remove-orphans -v"
    docker compose -f ${DOCKER_COMPOSE_TOOLS_FILE} down --remove-orphans -v
    echo "$ docker compose up -d"
    docker compose -f ${DOCKER_COMPOSE_TOOLS_FILE} up -d
fi

# Wait for gateway health check endpoint
waitForService curl -k http://${HOST}:${PORT}/actuator/health || error_exit "Gateway service is not available"

# Waiting for services to come up
echo "Sleeping for ${INITIAL_SLEEP_TIME} sec for services to start"
sleep ${INITIAL_SLEEP_TIME}

# Check all required services 
echo "Checking service health..."
waitForService curl -k http://${HOST}:${PORT}/CATALOG-SERVICE/catalog-service/actuator/health || error_exit "Catalog service is not available"
waitForService curl -k http://${HOST}:${PORT}/INVENTORY-SERVICE/inventory-service/actuator/health || error_exit "Inventory service is not available"
waitForService curl -k http://${HOST}:${PORT}/ORDER-SERVICE/order-service/actuator/health || error_exit "Order service is not available"
waitForService curl -k http://${HOST}:${PORT}/PAYMENT-SERVICE/payment-service/actuator/health || error_exit "Payment service is not available"

echo "Setting up test data..."
setupTestData || error_exit "Test data setup failed!"

echo "Verifying APIs..."
verifyAPIs || error_exit "API verification failed!"

echo "End, all tests OK:" `date`
echo "==============================================="
echo "✅ Tests completed successfully"
echo "==============================================="

# Clean up based on command line arguments
if [[ $@ == *"stop"* ]]; then
    echo "We are done, stopping the test environment..."
    echo "$ docker compose -f ${DOCKER_COMPOSE_FILE} down --remove-orphans -v"
    docker compose -f ${DOCKER_COMPOSE_FILE} down --remove-orphans -v
fi

if [[ $@ == *"teardown"* ]]; then
    echo "We are done, stopping the test environment..."
    echo "$ docker compose -f ${DOCKER_COMPOSE_TOOLS_FILE} down --remove-orphans -v"
    docker compose -f ${DOCKER_COMPOSE_TOOLS_FILE} down --remove-orphans -v
fi

exit ${TEST_STATUS}
