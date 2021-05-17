Commands for Kafka topic lookup
------

```bash
KAFKA_IP=$(docker network inspect -f '{{json .Containers}}' boba_default | jq '.[] | select(.Name | contains("kafka")) | .IPv4Address' | sed -e 's/"\(.*\)\/.*"/\1/' )
docker-compose exec kafka bash
/opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic sql-query-actions --time -1 --offsets 1 | awk -F  ":" '{sum += $3} END {print sum}'
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic sql-query-response --offset earliest --partition 0
```