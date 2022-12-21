#!/usr/bin/env bash
### Sample usage:
#
#   for local run
#     HOST=localhost PORT=8765 ./test-em-all.bash
#   with docker compose
#     HOST=localhost PORT=8765 ./test-em-all.bash start stop
#
echo -e "Starting 'Store μServices' for [end-2-end] testing....\n"

: ${HOST=localhost}
: ${PORT=8765}
: ${PROD_CODE=ProductCode1}
: ${CUSTOMER_NAME=dockerCustomer}

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
    curl -X ${methodType} -k http://${HOST}:${PORT}/${baseURL} -H "Content-Type: application/json" \
    --data "$composite"
}

function setupTestData() {

    body="{\"code\":\"$PROD_CODE"
    body+=\
'","productName":"product name A","price":100, "description": "A Beautiful Product"}'

#    Creating Product
    recreateComposite "$PROD_CODE" "$body" "CATALOG-SERVICE/catalog-service/api/catalog" "POST"

    body="{\"name\": \"$CUSTOMER_NAME"
    body+=\
'","amountAvailable":1000,"amountReserved":0}'

    # Creating Customer
    recreateComposite "$CUSTOMER_NAME" "$body" "PAYMENT-SERVICE/payment-service/api/customers" "POST"

    # waiting for kafka to process the catalog creation request
    sleep 5
    # Verify that a normal request works, expect record exists with product code
    assertCurl 200 "curl -k http://$HOST:$PORT/INVENTORY-SERVICE/inventory-service/api/inventory/$PROD_CODE"
    assertEqual \"${PROD_CODE}\" $(echo ${RESPONSE} | jq .productCode)

    body="{\"productCode\":\"$PROD_CODE"
    body+=\
'","reservedItems":0,"availableQuantity":100}'

    # Update the product available Quantity
    recreateComposite $(echo "$RESPONSE" | jq -r .id) "$body" "INVENTORY-SERVICE/inventory-service/api/inventory/$(echo "$RESPONSE" | jq -r .id)" "PUT"

    # Verify that a normal request works, expect record exists with CustomerName
    assertCurl 200 "curl -k http://$HOST:$PORT/PAYMENT-SERVICE/payment-service/api/customers/name/$CUSTOMER_NAME"
    assertEqual \"${CUSTOMER_NAME}\" $(echo ${RESPONSE} | jq .name)

    body="{\"customerId\": $(echo ${RESPONSE} | jq .id)"
    body+=\
',"customerEmail": "docker@email.com","customerAddress": "docker Address","items":[{"productId": '
    body+="\"$PROD_CODE"
    body+=\
'","quantity": 10,"productPrice": 5}]}'

    # Creating Order
    recreateComposite "$CUSTOMER_NAME" "$body" "ORDER-SERVICE/order-service/api/orders" "POST"

}

set -e

echo "Start Tests:" `date`

echo "HOST=${HOST}"
echo "PORT=${PORT}"

if [[ $@ == *"start"* ]]
then
    echo "Restarting the test environment..."
    echo "$ docker-compose down --remove-orphans"
    docker-compose down --remove-orphans
    echo "$ docker-compose up -d"
    docker-compose up -d
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

echo "End, all tests OK:" `date`

if [[ $@ == *"stop"* ]]
then
    echo "We are done, stopping the test environment..."
    echo "$ docker-compose down --remove-orphans"
    docker-compose down --remove-orphans
fi
