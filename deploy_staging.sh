#!/usr/bin/env bash

function assert_git_is_clean() {
    status=$(git status --porcelain)

    if [ -n "${status}" ]; then
    echo "Working directory is not clean. Aborting..."
    exit 1
    fi
}

assert_git_is_clean

# compile
lein ring uberjar

# dockerize
TAG=git-$(git rev-parse --short=12 HEAD)
IMAGE_NAME=mastodonc/witan.app

docker build -t "${IMAGE_NAME}" .
docker tag -f "${IMAGE_NAME}" "${IMAGE_NAME}:latest" && \
docker tag -f "${IMAGE_NAME}" "${IMAGE_NAME}:${TAG}" &&
docker push "${IMAGE_NAME}"

# using deployment service sebastopol
TAG=git-$(git rev-parse --short=12 HEAD)
sed "s/@@TAG@@/$TAG/" witan.app.json.template > witan.app.json
curl -i -X POST http://sebastopol.marathon.mesos:9501/marathon/witan.app -H "Content-Type: application/json" -H "x-kixi-keep-it-secret-keep-it-safe" --data-binary "@witan.app.json"
