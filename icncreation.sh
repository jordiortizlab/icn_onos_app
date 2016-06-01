export CONTROLLER='localhost:8181'

curl -u karaf:karaf -X POST --data @data/proxy.json --header 'Content-Type: application/json' http://192.168.122.78:8181/onos/icn/proxy\?name=proxy01
curl -u karaf:karaf -X GET http://192.168.122.78:8181/onos/icn/proxies
CONTROLLER='localhost:8181'

curl -u karaf:karaf -X POST --data @data/proxy.json --header 'Content-Type: application/json' http://192.168.122.78:8181/onos/icn/proxy\?name=proxy01