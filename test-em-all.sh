#!/usr/bin/env bash
### Sample usage:
#
#   for local run
#     HOST=localhost PORT=8765 ./test-em-all.sh
#   with docker compose
#     HOST=localhost PORT=8765 ./test-em-all.sh start stop
#
echo -e "Starting 'Store μServices' for [end-2-end] testing....\n"

: ${HOST=localhost}
: ${PORT=8765}
: ${PROD_CODE=ProductCode83}
: ${CUSTOMER_NAME=dockerCustomer83}

function assertCurl() {

  local expectedHttpCode=$1
  local curlCmd="$2 -w \"%{http_code}\""
  local result=$(eval ${curlCmd})
  local httpCode="${result:(-3)}"
  RESPONSE='' && (( ${#result} > 3 )) && RESPONSE="${result%???}"

  if [[ "$httpCode" = "$expectedHttpCode" ]]
  then
    if [[ "$httpCode" = "200" ]]
    then
      echo "Test OK (HTTP Code: $httpCode)"
    else
      echo "Test OK (HTTP Code: $httpCode, $RESPONSE)"
    fi
    return 0
  else
      echo  "Test FAILED, EXPECTED HTTP Code: $expectedHttpCode, GOT: $httpCode, WILL ABORT!"
      echo  "- Failing command: $curlCmd"
      echo  "- Response Body: $RESPONSE"
      return 1
  fi
}

function assertEqual() {

  local expected=$1
  local actual=$2

  if [[ "$actual" = "$expected" ]]
  then
    echo "Test OK (actual value: $actual)"
    return 0
  else
    echo "Test FAILED, EXPECTED VALUE: $expected, ACTUAL VALUE: $actual, WILL ABORT"
    return 1
  fi
}

function testUrl() {
    url=$@
    if curl ${url} -ks -f -o /dev/null
    then
          return 0
    else
          return 1
    fi;
}

function waitForService() {
    url=$@
    echo -n "Wait for: $url... "
    n=0
    until testUrl ${url}
    do
        n=$((n + 1))
        if [[ ${n} == 100 ]]
        then
            echo " Give up"
            exit 1
        else
            sleep 3
            echo -n ", retry #$n "
        fi
    done
    echo -e "\n DONE, continues...\n"
}

function recreateComposite() {
    local identifier=$1
    local composite=$2
    local baseURL=$3
    local methodType=$4

    echo "calling URL" http://${HOST}:${PORT}/${baseURL}
#    assertCurl 200 "curl -X DELETE -k http://${HOST}:${PORT}/${baseURL}/${identifier} -s"
    COMPOSITE_RESPONSE=$(curl -X ${methodType} -k http://${HOST}:${PORT}/${baseURL} -H "Content-Type: application/json" \
    --data "$composite")
}

function setupTestData() {

    body="{\"code\":\"$PROD_CODE"
    body+=\
'","productName":"product name A","price":100, "description": "A Beautiful Product"}'

#    Creating Product
    recreateComposite "$PROD_CODE" "$body" "catalog-service/api/catalog" "POST"

    # waiting for kafka to process the catalog creation request
    sleep 3
    # Verify that a normal request works, expect record exists with product code
    assertCurl 200 "curl -k http://$HOST:$PORT/inventory-service/api/inventory/$PROD_CODE"
    assertEqual \"${PROD_CODE}\" $(echo ${RESPONSE} | jq .productCode)

    body="{\"productCode\":\"$PROD_CODE"
    body+=\
'","reservedItems":0,"availableQuantity":100}'

    # Update the product available Quantity
    recreateComposite $(echo "$RESPONSE" | jq -r .id) "$body" "inventory-service/api/inventory/$(echo "$RESPONSE" | jq -r .id)" "PUT"

    body="{\"name\": \"$CUSTOMER_NAME"
    body+=\
'","email": "docker@email.com","address": "docker Address","amountAvailable":1000,"amountReserved":0}'

    # Creating Customer
    recreateComposite "$CUSTOMER_NAME" "$body" "payment-service/api/customers" "POST"
    CUSTOMER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .id)
 
}

function verifyAPIs() {

     # Step 1 Creating Order and it should be CONFIRMED

    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productId": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 10,"productPrice": 5}]}'

    # Creating Order
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST"

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for 3 sec for order processing"
    sleep 3

    # Verify that order processing is completed and status is CONFIRMED
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID"
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId)
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId)
    assertEqual \"CONFIRMED\" $(echo ${RESPONSE} | jq .status)

    # Verify that amountAvailable is deducted as per order
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID"
    assertEqual 950 $(echo ${RESPONSE} | jq .amountAvailable)
    assertEqual 0 $(echo ${RESPONSE} | jq .amountReserved)

    # Step2, Order Should be rejected
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productId": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 100,"productPrice": 5}]}'

    # Creating 2nd Order, this should ROLLBACK as Inventory is not available
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST"

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for 5 sec for order processing"
    sleep 5

    # Verify that order processing is completed and status is CONFIRMED
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID"
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId)
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId)
    assertEqual \"ROLLBACK\" $(echo ${RESPONSE} | jq .status)
    assertEqual \"STOCK\" $(echo ${RESPONSE} | jq .source)

    # Verify that amountAvailable is not deducted as per order
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID"
    assertEqual 950 $(echo ${RESPONSE} | jq .amountAvailable)
    assertEqual 0 $(echo ${RESPONSE} | jq .amountReserved)

    # Step 3, Order Should be CONFIRMED 
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productId": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 80,"productPrice": 5}]}'

    # Creating 3nd Order, this should CONFIRMED as Inventory is available
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST"

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for 5 sec for order processing"
    sleep 5

    # Verify that order processing is completed and status is CONFIRMED
    assertCurl 200 "curl -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID"
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId)
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId)
    assertEqual \"CONFIRMED\" $(echo ${RESPONSE} | jq .status)

    # Verify that amountAvailable is deducted as per order
    assertCurl 200 "curl -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID"
    assertEqual 550 $(echo ${RESPONSE} | jq .amountAvailable)
    assertEqual 0 $(echo ${RESPONSE} | jq .amountReserved)
}

set -e

echo "Start Tests:" `date`

echo "HOST=${HOST}"
echo "PORT=${PORT}"

if [[ $@ == *"start"* ]]
then
    echo "Restarting the test environment..."
    echo "$ docker-compose -f docker-compose.yml down --remove-orphans"
    docker-compose -f docker-compose.yml down --remove-orphans
    echo "$ docker-compose up -d"
    docker-compose -f docker-compose.yml up -d
fi

waitForService curl -k http://${HOST}:${PORT}/actuator/health

# waiting for services to come up
echo "Sleeping for 120 sec for services to start"
sleep 120

waitForService curl -k http://${HOST}:${PORT}/CATALOG-SERVICE/catalog-service/actuator/health

waitForService curl -k http://${HOST}:${PORT}/INVENTORY-SERVICE/inventory-service/actuator/health

waitForService curl -k http://${HOST}:${PORT}/ORDER-SERVICE/order-service/actuator/health

waitForService curl -k http://${HOST}:${PORT}/PAYMENT-SERVICE/payment-service/actuator/health

setupTestData

verifyAPIs

echo "End, all tests OK:" `date`

if [[ $@ == *"stop"* ]]
then
    echo "We are done, stopping the test environment..."
    echo "$ docker-compose -f docker-compose.yml down --remove-orphans"
    docker-compose -f docker-compose.yml down --remove-orphans
fi
