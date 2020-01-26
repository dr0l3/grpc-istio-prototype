#!/usr/bin/env bash

curl -d '{"conversationId": 0, "text": "hello"}' -H "Content-Type: application/json" -X POST localhost:8080/conversations/messages
curl -d '{"subject":"bananas", "creator":0}' -H "Content-Type: application/json" -X POST http://localhost:8080/conversations
curl -X POST localhost:8080/users/rune
curl localhost:8080/aggregate/0