export CONTROLLER='10.7.0.4:8181'

curl -u karaf:karaf -X POST --data @data/proxy.json --header 'Content-Type: application/json' http://$CONTROLLER/onos/icn/proxy\?name=proxyMAT
curl -u karaf:karaf -X GET http:///$CONTROLLER/onos/icn/proxies
echo ""

curl -u karaf:karaf -X POST --data @data/cdn2.json --header 'Content-Type: application/json' http:///$CONTROLLER/onos/icn/cdn\?name=vplocan
curl -u karaf:karaf -X GET http:///$CONTROLLER/onos/icn/cdns
echo ""

curl -u karaf:karaf -X POST --data @data/cache1.json --header 'Content-Type: application/json' http:///$CONTROLLER/onos/icn/cache\?name=vplocan\&cname=squid01

#curl -u karaf:karaf -X POST --data @data/cache2.json --header 'Content-Type: application/json' http:///$CONTROLLER/onos/icn/cache\?name=vplocan\&cname=squid02
curl -u karaf:karaf -X GET http:///$CONTROLLER/onos/icn/caches\?name=vplocan
echo ""

curl -u karaf:karaf -X POST --data @data/providerplexp.json --header 'Content-Type: application/json' http:///$CONTROLLER/onos/icn/provider\?name=vplocan\&cname=plexp
curl -u karaf:karaf -X GET http:///$CONTROLLER/onos/icn/providers\?name=vplocan
echo ""



