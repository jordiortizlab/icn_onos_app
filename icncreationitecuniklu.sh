if [ $# -eq 0 ]
then
    export CONTROLLER='192.168.100.10:8181'
else
    export CONTROLLER=$1':8181'
fi
echo CONTROLLER: $CONTROLLER

curl -u karaf:karaf -X POST --data @data/proxy.json --header 'Content-Type: application/json' http://$CONTROLLER/onos/icn/proxy\?name=proxyMAT
curl -u karaf:karaf -X GET http://$CONTROLLER/onos/icn/proxies
echo ""


if [[ "$2" = "CLOSEST" ]]
then
    echo "CLOSEST WITHOUT PREFETCH"
    curl -u karaf:karaf -X POST --data @data/icnunikluCLOSEST.json --header 'Content-Type: application/json' http://$CONTROLLER/onos/icn/icn\?name=uniklu
elif [[ "$2" = "PREFETCH" ]]
then
    echo "PREFETCHING"
    curl -u karaf:karaf -X POST --data @data/icnunikluCLOSESTDASH.json --header 'Content-Type: application/json' http://$CONTROLLER/onos/icn/icn\?name=uniklu
elif [[ "$2" = "DISTRIBUTED" ]]
then
    echo "DISTRIBUTED SVC"
        curl -u karaf:karaf -X POST --data @data/icnunikluDISTRIBUTED.json --header 'Content-Type: application/json' http://$CONTROLLER/onos/icn/icn\?name=uniklu
fi

curl -u karaf:karaf -X GET http://$CONTROLLER/onos/icn/icns
echo ""

curl -u karaf:karaf -X POST --data @data/cache1.json --header 'Content-Type: application/json' http://$CONTROLLER/onos/icn/cache\?name=uniklu\&cname=squid01
curl -u karaf:karaf -X POST --data @data/cache2.json --header 'Content-Type: application/json' http://$CONTROLLER/onos/icn/cache\?name=uniklu\&cname=squid02
curl -u karaf:karaf -X GET http://$CONTROLLER/onos/icn/caches\?name=uniklu
echo ""

curl -u karaf:karaf -X POST --data @data/provideruniklu.json --header 'Content-Type: application/json' http://$CONTROLLER/onos/icn/provider\?name=uniklu\&cname=uniklu
curl -u karaf:karaf -X GET http://$CONTROLLER/onos/icn/providers\?name=uniklu

# curl -u karaf:karaf -X POST --data @data/providerwithinicn.json --header 'Content-Type: application/json' http://$CONTROLLER/onos/icn/provider\?name=uniklu\&cname=within
# curl -u karaf:karaf -X GET http://$CONTROLLER/onos/icn/providers\?name=uniklu


echo ""
curl -u karaf:karaf -X GET http://$CONTROLLER/onos/icn/icns
echo ""

#echo "Put devices info"
#/DEVEL/SDN/ONOS/onos/tools/package/runtime/bin/onos-netcfg 10.7.0.4 scenario.json
curl -sSL --user karaf:karaf -X POST -H 'Content-Type:application/json' -d@scenario.json  http://$CONTROLLER/onos/v1/network/configuration/
