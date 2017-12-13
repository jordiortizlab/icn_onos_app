#!/bin/bash

# sshpass -p karaf ssh 10.7.0.4 -l karaf -p 8101 -t shutdown -c -cc -h -f
# sleep 10
# ssh sdn@10.7.0.4 -t sudo systemctl start onos
# sleep 60
#sshpass -p karaf ssh 10.7.0.4 -l karaf -p 8101 -t app deactivate es.um.icn
# sshpass -p karaf ssh 10.7.0.4 -l karaf -p 8101 -t shutdown -c -cc -r -f
# sleep 120
#sshpass -p karaf ssh 10.7.0.4 -l karaf -p 8101 -t app activate es.um.icn
#sleep 15
ssh 10.7.0.4 -t sudo docker stop onos_phd
ssh 10.7.0.4 -t sudo docker rm onos_phd
ssh 10.7.0.4 -t sudo docker run -d -i -e KARAF_DEBUG=true -e ONOS_APPS=openflow --name onos_phd -p 6633:6633 -p 8181:8181 -p 8101:8101 -p 5005:5005 -p 8080:8080 onosproject/onos:1.10.2

nc -z 10.7.0.4 8101
while [ $? -ne 0 ]
do
    echo "Wainting for 10.7.0.4 to become available"
    sleep 60
    nc -z 10.7.0.4 8101
done
# install app
curl -sS --user karaf:karaf --noproxy localhost -X POST -HContent-Type:application/octet-stream http://10.7.0.4:8181/onos/v1/applications?activate=true --data-binary @icn-sdn-1.3-SNAPSHOT.oar
sleep 15
/home/nenjordi/icn_onos_app/icncreationitecuniklu.sh

