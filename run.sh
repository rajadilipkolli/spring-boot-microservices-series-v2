#!/usr/bin/env bash

declare project_dir=$(dirname $0)
declare dc_main=${project_dir}/docker-compose.yml
declare dc_tools=${project_dir}/docker-compose-tools.yml

function restart() {
    stop_all
    start_all
}

function start() {
    echo "Starting $1...."
    build_api
    docker-compose -f ${dc_main} up -d $1
    docker-compose -f ${dc_main} logs -f
}

function stop() {
    echo "Stopping $1...."
    docker-compose -f ${dc_main} stop
    docker-compose -f ${dc_main} rm -f
}

function start_infra() {
    echo "Starting zipkin-server postgresql kafka redis config-server naming-server  ...."
    docker compose -f ${dc_main} up -d zipkin-server postgresql kafka redis config-server naming-server
    docker compose -f ${dc_main} logs -f
}

function start_infra_full() {
    echo "Starting grafana promtail postgresql kafka redis config-server naming-server...."
    docker compose -f ${dc_tools} up -d grafana promtail postgresql kafka redis config-server naming-server
    docker compose -f ${dc_tools} logs -f
}

function start_services() {
    echo "Starting api-gateway catalog-service inventory-service order-service payment-service ...."
    docker compose -f ${dc_main} up -d api-gateway catalog-service inventory-service order-service payment-service
    docker compose -f ${dc_main} logs -f
}

function start_all() {
    echo "Starting all services...."
    build_api
    docker-compose -f ${dc_main}  up -d
    docker-compose -f ${dc_main}  logs -f
}

function stop_all() {
    echo 'Stopping all services....'
    docker-compose -f ${dc_main} stop
    docker-compose -f ${dc_main} down
    docker-compose -f ${dc_main} rm -f
}

function stop_all_full() {
    echo 'Stopping all services....'
    docker-compose -f ${dc_tools} stop
    docker-compose -f ${dc_tools} down
    docker-compose -f ${dc_tools} rm -f
}

function build_api() {
    ./mvnw clean spotless:apply spring-boot:build-image -DskipTests
}

action="start_all"

if [[ "$#" != "0"  ]]
then
    action=$@
fi

eval ${action}