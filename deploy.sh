#!/usr/bin/env bash

SEBASTOPOL_IP=$1
ENVIRONMENT=$2
# using deployment service sebastopol
TAG=git-$(echo $CIRCLE_SHA1 | cut -c1-12)
sed -e "s/@@TAG@@/$TAG/" -e "s/@@ENVIRONMENT@@/$ENVIRONMENT/" witan-app.json.template > witan-app.json

# we want curl to output something we can use to indicate success/failure
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://$SEBASTOPOL_IP:9501/marathon/witan-app -H "Content-Type: application/json" -H "$SEKRIT_HEADER: 123" --data-binary "@witan-app.json")
echo "HTTP code " $STATUS
if [ $STATUS == "201" ]
then exit 0
else exit 1
fi
