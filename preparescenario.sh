#!/bin/bash

CONTROLLER="192.168.100.10"

ssh $CONTROLLER -t sudo docker stop onos_phd
ssh $CONTROLLER -t sudo rm /home/nenjordi/LOGS/* # clean old log files from onos
sleep 20
ssh $CONTROLLER -t sudo docker run -d -i --rm -e KARAF_DEBUG=true -e ONOS_APPS=openflow --name onos_phd -p 6633:6633 -p 8181:8181 -p 8101:8101 -p 5005:5005 -p 8080:8080 -v /home/nenjordi/LOGS/:/root/onos/apache-karaf-3.0.8/data/log/ onosproject/onos:1.12.0

nc -z $CONTROLLER 8101
while [ $? -ne 0 ]
do
    echo "Wainting for $CONTROLLER to become available"
    sleep 10
    nc -z $CONTROLLER 8101
done
# install app
sleep 60
curl -sS --user karaf:karaf --noproxy localhost -X POST -HContent-Type:application/octet-stream http://$CONTROLLER:8181/onos/v1/applications?activate=true --data-binary @icn-sdn-1.5-SNAPSHOT.oar
sleep 15
if [[ "$1" = "CLOSEST" ]]
then
    echo "CLOSEST WITHOUT PREFETCH"
    bash /home/nenjordi/ICN/icn_onos_app/icncreationitecuniklu.sh  "$CONTROLLER" "CLOSEST"
elif [[ "$1" = "PREFETCH" ]]
then
    echo "PREFETCHING"
    bash /home/nenjordi/ICN/icn_onos_app/icncreationitecuniklu.sh "$CONTROLLER" "PREFETCH"

elif [[ "$1" = "DISTRIBUTED" ]]
then
    echo "DISTRIBUTED SVC"
    bash /home/nenjordi/ICN/icn_onos_app/icncreationitecuniklu.sh "$CONTROLLER" "DISTRIBUTED"
fi
./locatehosts.sh
