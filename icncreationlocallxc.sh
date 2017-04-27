if [ $# -eq 0 ]
then
    export CONTROLLER='192.168.122.1:8181'
else
    export CONTROLLER=$1':8181'
fi
echo CONTROLLER: $CONTROLLER

curl -u karaf:karaf -X POST --data @datalocal/proxylocallxc.json --header 'Content-Type: application/json' http://$CONTROLLER/onos/icn/proxy\?name=proxyubuntuDockerlibvirt
curl -u karaf:karaf -X GET http:///$CONTROLLER/onos/icn/proxies
echo ""

curl -u karaf:karaf -X POST --data @datalocal/cdnlocal.json --header 'Content-Type: application/json' http:///$CONTROLLER/onos/icn/cdn\?name=LocalCDN4Testing
curl -u karaf:karaf -X GET http:///$CONTROLLER/onos/icn/cdns
echo ""

curl -u karaf:karaf -X POST --data @datalocal/cachelocal.json --header 'Content-Type: application/json' http:///$CONTROLLER/onos/icn/cache\?name=LocalCDN4Testing\&cname=squid01docker

#curl -u karaf:karaf -X POST --data @data/cache2.json --header 'Content-Type: application/json' http:///$CONTROLLER/onos/icn/cache\?name=vplocan\&cname=squid02
curl -u karaf:karaf -X GET http:///$CONTROLLER/onos/icn/caches\?name=LocalCDN4Testing
echo ""

curl -u karaf:karaf -X POST --data @datalocal/providerlxc.json --header 'Content-Type: application/json' http:///$CONTROLLER/onos/icn/provider\?name=LocalCDN4Testing\&cname=lxchost2
#curl -u karaf:karaf -X POST --data @datalocal/providerplexp.json --header 'Content-Type: application/json' http:///$CONTROLLER/onos/icn/provider\?name=LocalCDN4Testing\&cname=plexp
curl -u karaf:karaf -X GET http:///$CONTROLLER/onos/icn/providers\?name=LocalCDN4Testing
echo ""



