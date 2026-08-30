#!/usr/bin/env bash
set -e

echo "Simulating Kafka broker failure..."

# Get the first kafka pod
KAFKA_POD=$(kubectl get pods -n retailstore -l strimzi.io/name=kafka-kafka -o jsonpath='{.items[0].metadata.name}')

if [ -z "$KAFKA_POD" ]; then
    echo "No Kafka broker found. Is Strimzi deployed?"
    exit 1
fi

echo "Found Kafka broker: $KAFKA_POD"
echo "Deleting broker to simulate a crash..."
kubectl delete pod $KAFKA_POD -n retailstore

echo "Broker deleted. Wait for operator to recreate..."

# Wait for recreation
kubectl wait --for=condition=ready pod -l strimzi.io/name=kafka-kafka -n retailstore --timeout=300s

echo "Broker recovered successfully!"

# Check consumer lag via Kafka command
echo "Checking consumer lag for 'orders' topic..."
kubectl exec -n retailstore -c kafka $KAFKA_POD -- bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group order-group || echo "Cannot fetch lag yet"

echo "Broker failure resiliency test completed."
