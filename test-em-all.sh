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

    echo "calling URL" http://${HOST}:${PORT}/${baseURL}
#    assertCurl 200 "curl -X DELETE -k http://${HOST}:${PORT}/${baseURL}/${identifier} -s"
    curl -X POST -k http://${HOST}:${PORT}/${baseURL} -H "Content-Type: application/json" \
    --data "$composite"
}

function setupTestData() {

    body="{\"code\":\"$PROD_CODE"
    body+=\
'","productName":"product name A","price":100, "description": "A Beautiful Product"}'

    recreateComposite "$PROD_CODE" "$body" "CATALOG-SERVICE/catalog-service/api/catalog"

    # Verify that a normal request works, expect record exists with product code
    assertCurl 200 "curl -k http://$HOST:$PORT/INVENTORY-SERVICE/inventory-service/api/inventory/$PROD_CODE"
    assertEqual ${PROD_CODE} $(echo ${RESPONSE} | jq .productCode)

    local inventoryId = "$(echo "$RESPONSE" | jq -r .id)"

    echo ${inventoryId}


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

setupTestData

echo "End, all tests OK:" `date`

if [[ $@ == *"stop"* ]]
then
    echo "We are done, stopping the test environment..."
    echo "$ docker-compose down --remove-orphans"
    docker-compose down --remove-orphans
fi