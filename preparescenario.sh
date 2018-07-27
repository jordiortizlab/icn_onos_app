#!/bin/bash

ssh 192.168.100.10 -t sudo docker stop onos_phd
sleep 20
ssh 192.168.100.10 -t sudo docker run -d -i --rm -e KARAF_DEBUG=true -e ONOS_APPS=openflow --name onos_phd -p 6633:6633 -p 8181:8181 -p 8101:8101 -p 5005:5005 -p 8080:8080 -v /home/nenjordi/LOGS/:/root/onos/apache-karaf-3.0.8/data/log/ onosproject/onos:1.12.0

nc -z 192.168.100.10 8101
while [ $? -ne 0 ]
do
    echo "Wainting for 192.168.100.10 to become available"
    sleep 10
    nc -z 192.168.100.10 8101
done
# install app
sleep 60
curl -sS --user karaf:karaf --noproxy localhost -X POST -HContent-Type:application/octet-stream http://192.168.100.10:8181/onos/v1/applications?activate=true --data-binary @icn-sdn-1.4-SNAPSHOT.oar
sleep 15
bash /home/nenjordi/ICN/icn_onos_app/icncreationitecuniklu.sh
./locatehosts.sh
