#!/usr/bin/env bash

# compile
lein ring uberjar

# using deployment service sebastopol
TAG=git-$(echo $CIRCLE_SHA1 | cut -c1-12)
sed "s/@@TAG@@/$TAG/" witan.app.json.template > witan.app.json
curl -i -X POST http://52.28.251.86:9501/marathon/witan.app -H "Content-Type: application/json" -H "x-kixi-keep-it-secret-keep-it-safe" --data-binary "@witan.app.json"
