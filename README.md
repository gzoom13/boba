# Business Observability for Bank Applications

We would like to extract useful business events from below 
mocks' databases and provide convinient API for queries.

Ultimate goal is to provide API to modify event extraction rules on the fly
without code changes (e.g. add new applicaiton in the mocks chain
and configure how information should be retrivied from it).

## Mocks

- Router - recieves files, stores their copies locally and then routes to converter
- Converter - converts the file into different format
- Bulker - bulks multiple files into single one
