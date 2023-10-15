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
: ${PROD_CODE=P0001}
: ${PROD_CODE_1=P0002}
: ${CUSTOMER_NAME=dockerCustomer001}

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

    echo "calling URL" http://${HOST}:${PORT}/${baseURL} " with body -" $composite
#    assertCurl 200 "curl  -X DELETE -k http://${HOST}:${PORT}/${baseURL}/${identifier} -s"
    COMPOSITE_RESPONSE=$(curl -X ${methodType} -k http://${HOST}:${PORT}/${baseURL} -H "Content-Type: application/json" \
    --data "$composite")

    echo "Response from caller - " ${COMPOSITE_RESPONSE}
    echo " "
}

function setupTestData() {

    body="{\"code\":\"$PROD_CODE"
    body+=\
'","productName":"product name A","price":100, "description": "A Beautiful Product"}'

#    Creating Product
    echo "creating product with code - " $PROD_CODE
    recreateComposite "$PROD_CODE" "$body" "catalog-service/api/catalog" "POST"

    body="{\"code\":\"$PROD_CODE_1"
    body+=\
'","productName":"product name B","price":9.99, "description": "Nice Product"}'

    #    Creating Product
    echo "creating product with code - " $PROD_CODE_1
    recreateComposite "$PROD_CODE_1" "$body" "catalog-service/api/catalog" "POST"

    # waiting for kafka to process the catalog creation request, as it is first time kafka initialization takes time
    sleep 3
    # Verify that a normal request works, expect record exists with product code
    assertCurl 200 "curl  -k http://$HOST:$PORT/inventory-service/api/inventory/$PROD_CODE"
    assertEqual \"${PROD_CODE}\" $(echo ${RESPONSE} | jq .productCode)

    body="{\"productCode\":\"$PROD_CODE"
    body+=\
'","availableQuantity":100}'

    # Update the product available Quantity
    recreateComposite $(echo "$RESPONSE" | jq -r .id) "$body" "inventory-service/api/inventory/$(echo "$RESPONSE" | jq -r .id)" "PUT"

    # Verify that a normal request works, expect record exists with product code_1
    assertCurl 200 "curl  -k http://$HOST:$PORT/inventory-service/api/inventory/$PROD_CODE_1"
    assertEqual \"${PROD_CODE_1}\" $(echo ${RESPONSE} | jq .productCode)

    body="{\"productCode\":\"$PROD_CODE_1"
    body+=\
'","availableQuantity":50}'

    # Update the product_1 available Quantity
    recreateComposite $(echo "$RESPONSE" | jq -r .id) "$body" "inventory-service/api/inventory/$(echo "$RESPONSE" | jq -r .id)" "PUT"

    body="{\"name\": \"$CUSTOMER_NAME"
    body+=\
'","email": "docker@email.com","address": "docker Address","amountAvailable":1000}'

    # Creating Customer
    recreateComposite "$CUSTOMER_NAME" "$body" "payment-service/api/customers" "POST"
    CUSTOMER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .customerId)
 
}

function verifyAPIs() {

     # Step 1 Creating Order and it should be CONFIRMED

    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 10,"productPrice": 5}]}'

    echo " "
    # Creating Order
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST"

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for 8 sec as it is first order, letting kafka start in all services. Processing orderId -" $ORDER_ID
    sleep 8

    # Verify that order processing is completed and status is CONFIRMED
    assertCurl 200 "curl  -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID"
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId)
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId)
    assertEqual \"CONFIRMED\" $(echo ${RESPONSE} | jq .status)
    assertEqual null $(echo ${RESPONSE} | jq .source)

    # Verify that amountAvailable is deducted as per order
    assertCurl 200 "curl  -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID"
    assertEqual 950 $(echo ${RESPONSE} | jq .amountAvailable)
    assertEqual 0 $(echo ${RESPONSE} | jq .amountReserved)

    # Step2, Order Should be rejected
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 100,"productPrice": 5}]}'

    echo " "
    # Creating 2nd Order, this should ROLLBACK as Inventory is not available
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST"

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for 3 sec for processing of orderId -" $ORDER_ID
    sleep 3

    # Verify that order processing is completed and status is ROLLBACK
    assertCurl 200 "curl  -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID"
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId)
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId)
    assertEqual \"ROLLBACK\" $(echo ${RESPONSE} | jq .status)
    assertEqual \"INVENTORY\" $(echo ${RESPONSE} | jq .source)

    # Verify that amountAvailable is not deducted as per order
    assertCurl 200 "curl  -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID"
    assertEqual 950 $(echo ${RESPONSE} | jq .amountAvailable)
    assertEqual 0 $(echo ${RESPONSE} | jq .amountReserved)

    # Step 3, Order Should be CONFIRMED 
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 80,"productPrice": 10}]}'

    echo " "
    # Creating 3rd Order, this should CONFIRMED as Inventory is available
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST"

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for 3 sec for processing of orderId -" $ORDER_ID
    sleep 3

    # Verify that order processing is completed and status is CONFIRMED
    assertCurl 200 "curl  -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID"
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId)
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId)
    assertEqual \"CONFIRMED\" $(echo ${RESPONSE} | jq .status)
    assertEqual null $(echo ${RESPONSE} | jq .source)

    # Verify that amountAvailable is deducted as per order
    assertCurl 200 "curl  -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID"
    assertEqual 150 $(echo ${RESPONSE} | jq .amountAvailable)
    assertEqual 0 $(echo ${RESPONSE} | jq .amountReserved)

    # Step 4, Order Should be ROLLBACK 
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 8,"productPrice": 20}]}'

    echo " "
    # Creating 4th Order, this should ROLLBACK as amount is not available for customer
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST"

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for 3 sec for processing of orderId -" $ORDER_ID
    sleep 3

    # Verify that order processing is completed and status is ROLLBACK
    assertCurl 200 "curl  -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID"
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId)
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId)
    assertEqual \"ROLLBACK\" $(echo ${RESPONSE} | jq .status)
    assertEqual \"PAYMENT\" $(echo ${RESPONSE} | jq .source)

    # Verify that amountAvailable is not deducted as per order cant be processed
    assertCurl 200 "curl  -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID"
    assertEqual 150 $(echo ${RESPONSE} | jq .amountAvailable)
    assertEqual 0 $(echo ${RESPONSE} | jq .amountReserved)

    # Step 5, Order Should be REJECTED 
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 20,"productPrice": 20}]}'

    echo " "
    # Creating 5th Order, this should Reject as amount is not available for customer & inventory not available
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST"

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for 3 sec for processing of orderId -" $ORDER_ID
    sleep 3

    # Verify that order processing is completed and status is ROLLBACK
    assertCurl 200 "curl  -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID"
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId)
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId)
    assertEqual \"REJECTED\" $(echo ${RESPONSE} | jq .status)
    assertEqual null $(echo ${RESPONSE} | jq .source)

    # Verify that amountAvailable is not deducted as per order cant be processed
    assertCurl 200 "curl  -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID"
    assertEqual 150 $(echo ${RESPONSE} | jq .amountAvailable)
    assertEqual 0 $(echo ${RESPONSE} | jq .amountReserved)

    echo " "

    # Step 6 Creating Order and it should be CONFIRMED
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 1,"productPrice": 10},{"productCode": '
      body+="\"$PROD_CODE_1"
      body+=\
'","quantity": 5,"productPrice": 10}]}'

    echo " "
    # Creating Order
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST"

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for 3 sec as it is, Processing orderId -" $ORDER_ID
    sleep 3

    # Verify that order processing is completed and status is CONFIRMED
    assertCurl 200 "curl  -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID"
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId)
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId)
    assertEqual \"CONFIRMED\" $(echo ${RESPONSE} | jq .status)
    assertEqual null $(echo ${RESPONSE} | jq .source)

    # Verify that amountAvailable is deducted as per order
    assertCurl 200 "curl  -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID"
    assertEqual 90 $(echo ${RESPONSE} | jq .amountAvailable)
    assertEqual 0 $(echo ${RESPONSE} | jq .amountReserved)

    echo " "

    # Step 7 Creating Order with one available and it should be ROLLBACK
    body="{\"customerId\": $CUSTOMER_ID"
    body+=\
',"items":[{"productCode": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 1,"productPrice": 10},{"productCode": '
      body+="\"$PROD_CODE_1"
      body+=\
'","quantity": 500,"productPrice": 9.99}]}'

    echo " "
    # Creating Order
    recreateComposite "$CUSTOMER_NAME" "$body" "order-service/api/orders" "POST"

    local ORDER_ID=$(echo ${COMPOSITE_RESPONSE} | jq .orderId)

    echo "Sleeping for 3 sec as it is, Processing orderId -" $ORDER_ID
    sleep 3

    # Verify that order processing is completed and status is REJECTED
    assertCurl 200 "curl  -k http://$HOST:$PORT/order-service/api/orders/$ORDER_ID"
    assertEqual $ORDER_ID $(echo ${RESPONSE} | jq .orderId)
    assertEqual $CUSTOMER_ID $(echo ${RESPONSE} | jq .customerId)
    assertEqual \"REJECTED\" $(echo ${RESPONSE} | jq .status)
    assertEqual null $(echo ${RESPONSE} | jq .source)

    # Verify that amountAvailable is not deducted as per order
    assertCurl 200 "curl  -k http://$HOST:$PORT/payment-service/api/customers/$CUSTOMER_ID"
    assertEqual 90 $(echo ${RESPONSE} | jq .amountAvailable)
    assertEqual 0 $(echo ${RESPONSE} | jq .amountReserved)

}

set -e

echo "Start Tests:" `date`

echo "HOST=${HOST}"
echo "PORT=${PORT}"

if [[ $@ == *"start"* ]]
then
    echo "Restarting the test environment..."
    echo "$ docker compose -f docker-compose.yml down --remove-orphans -v"
    docker compose -f docker-compose.yml down --remove-orphans -v
    echo "$ docker compose up -d"
    docker compose -f docker-compose.yml up -d
fi

if [[ $@ == *"start_all"* ]]
then
    echo "Restarting the test environment..."
    echo "$ docker compose -f docker-compose-tools.yml down --remove-orphans -v"
    docker compose -f docker-compose-tools.yml down --remove-orphans -v
    echo "$ docker compose up -d"
    docker compose -f docker-compose-tools.yml up -d
fi

waitForService curl -k http://${HOST}:${PORT}/actuator/health

# waiting for services to come up
echo "Sleeping for 60 sec for services to start"
sleep 60

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
    echo "$ docker compose -f docker-compose.yml down --remove-orphans -v"
    docker compose -f docker-compose.yml down --remove-orphans -v
fi


if [[ $@ == *"stop_all"* ]]
then
    echo "We are done, stopping the test environment..."
    echo "$ docker compose -f docker-compose-tools.yml down --remove-orphans -v"
    docker compose -f docker-compose-tools.yml down --remove-orphans -v
fi
