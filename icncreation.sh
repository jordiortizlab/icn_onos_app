export CONTROLLER='localhost:8181'

curl -u karaf:karaf -X POST --data @data/proxy.json --header 'Content-Type: application/json' http://192.168.122.78:8181/onos/icn/proxy\?name=proxy01
curl -u karaf:karaf -X GET http://192.168.122.78:8181/onos/icn/proxies
echo ""

curl -u karaf:karaf -X POST --data @data/cdn2.json --header 'Content-Type: application/json' http://192.168.122.78:8181/onos/icn/cdn\?name=vplocan
curl -u karaf:karaf -X GET http://192.168.122.78:8181/onos/icn/cdns
echo ""

curl -u karaf:karaf -X POST --data @data/cache1.json --header 'Content-Type: application/json' http://192.168.122.78:8181/onos/icn/cache\?name=vplocan\&cname=squid01

curl -u karaf:karaf -X POST --data @data/cache2.json --header 'Content-Type: application/json' http://192.168.122.78:8181/onos/icn/cache\?name=vplocan\&cname=squid02
curl -u karaf:karaf -X GET http://192.168.122.78:8181/onos/icn/caches\?name=vplocan
echo ""

curl -u karaf:karaf -X POST --data @data/provider2.json --header 'Content-Type: application/json' http://192.168.122.78:8181/onos/icn/provider\?name=vplocan\&cname=plocan
curl -u karaf:karaf -X GET http://192.168.122.78:8181/onos/icn/providers\?name=vplocan
echo ""



