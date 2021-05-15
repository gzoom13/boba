# Business Observability for Bank Applications

We would like to extract useful business events from below 
mocks' databases and provide convenient API for queries.

Ultimate goal is to provide API to modify event extraction rules on the fly
without code changes (e.g. add new application in the mocks chain
and configure how information should be retrieved from it).

## Mocks

- Router - receives files, stores their copies locally and then routes to converter
- Converter - converts the file into different format
- Bulker - bulks multiple files into single one

## Test

Use below commands to build everything, start in docker and send message to router

```bash
sbt docker:publishLocal
docker-compose up -d
ROUTER_IP=$(docker network inspect -f '{{json .Containers}}' boba_default | jq '.[] | select(.Name | contains("router_mock_1")) | .IPv4Address' | sed -e 's/"\(.*\)\/.*"/\1/' )
curl -v -X POST http://$ROUTER_IP:8081/transfers --data-binary '{"field": "value2", "id": 12}' -H "Content-Type:  application/octet-stream"
```