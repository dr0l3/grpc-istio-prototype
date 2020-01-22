#!/usr/bin/env bash

docker tag users:0.1.0-SNAPSHOT dr0l3/grpc-users
docker tag conversations:0.1.0-SNAPSHOT dr0l3/grpc-conversations
docker tag gateway:0.1.0-SNAPSHOT dr0l3/grpc-gateway

docker push dr0l3/grpc-users
docker push dr0l3/grpc-conversations
docker push dr0l3/grpc-gateway

