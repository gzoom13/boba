#!/bin/bash
export ROUTER_IP=$(docker network inspect -f '{{json .Containers}}' boba_default | jq '.[] | select(.Name | contains("router_mock_1")) | .IPv4Address' | sed -e 's/"\(.*\)\/.*"/\1/' )
export TRACE_ENGINE_IP=$(docker network inspect -f '{{json .Containers}}' boba_default | jq '.[] | select(.Name | contains("trace_engine")) | .IPv4Address' | sed -e 's/"\(.*\)\/.*"/\1/' )
curl -v -X POST http://$ROUTER_IP:8081/transfers --data-binary '{"field": "value2", "id": 12}' -H "Content-Type:  application/octet-stream"
curl -v -X POST http://$TRACE_ENGINE_IP:8083/traces --data '{"transferId":1}'
xdg-open http://$TRACE_ENGINE_IP:8083/traces &
