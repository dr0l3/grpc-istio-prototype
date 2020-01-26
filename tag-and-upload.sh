#!/usr/bin/env bash

docker tag users:0.1.0-SNAPSHOT localhost:32000/grpc-users
docker tag conversations:0.1.0-SNAPSHOT localhost:32000/grpc-conversations
docker tag gateway:0.1.0-SNAPSHOT localhost:32000/grpc-gateway
docker tag orchestator:0.1.0-SNAPSHOT localhost:32000/grpc-orchestator
docker tag messages:0.1.0-SNAPSHOT localhost:32000/grpc-messages

docker push localhost:32000/grpc-users
docker push localhost:32000/grpc-conversations
docker push localhost:32000/grpc-gateway
docker push localhost:32000/grpc-orchestator
docker push localhost:32000/grpc-messages

