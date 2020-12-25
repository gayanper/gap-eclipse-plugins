#!/bin/bash

API_KEY=$1
API_UID=$2
ARCHIVE=$3
VERSION=$4

curl -X PUT -T $ARCHIVE -u $API_UID:$API_KEY \
        -H "X-Bintray-Package:gap.eclipse.plugins" \
        -H "X-Bintray-Version:$VERSION" \
        -H "X-Bintray-Explode:1" \
        https://api.bintray.com/content/$API_UID/p2/gap.eclipse.plugins/$VERSION

curl -X POST -u$API_UID:$API_KEY https://api.bintray.com/content/$API_UID/p2/gap.eclipse.plugins/$VERSION/publish

