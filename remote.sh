#!/usr/bin/env bash

#!/usr/bin/env bash

curl -d '{"conversationId": 0, "text": "hello"}' -H "Content-Type: application/json" -X POST localhost:30283/conversations/messages
curl -d '{"subject":"bananas", "creator":0}' -H "Content-Type: application/json" -X POST http://localhost:30283/conversations
curl -X POST localhost:30283/users/rune
curl localhost:30283/aggregate/0
