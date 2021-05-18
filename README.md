# Business Observability for Bank Applications

We would like to extract useful business events from below 
mocks' databases and provide convenient API for queries.

Ultimate goal is to provide API to modify event extraction rules on the fly
without code changes (e.g. add new application in the mocks chain
and configure how information should be retrieved from it).

## Mocks

- Router - receives files, stores their copies locally and then routes to converter
- Converter - converts the file into different format

## Test

Use below commands to build everything, start in docker and send message to router

```bash
sbt trace-engine:docker:publishLocal
docker-compose up -d
./test.sh
```