#!/bin/bash

sshpass -p karaf ssh 10.7.0.4 -l karaf -p 8101 -t app deactivate es.um.icn
sshpass -p karaf ssh 10.7.0.4 -l karaf -p 8101 -t shutdown -c -cc -r -f
sleep 120
sshpass -p karaf ssh 10.7.0.4 -l karaf -p 8101 -t app activate es.um.icn
sleep 30
./icncreationitecuniklu.sh

