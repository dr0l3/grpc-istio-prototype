# Install microk8s

go to microk8s.io

``sudo snap alias microk8s.kubectl mkcl``

# install service

``mkcl apply -f kubernetes``

# test

find port

``mkcl get svc``

```
mkcl get svc
NAME         TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
gateway      NodePort    10.152.183.188   <none>        8080:32545/TCP   2m11s <-- port 32545
kubernetes   ClusterIP   10.152.183.1     <none>        443/TCP          14h
users        NodePort    10.152.183.168   <none>        8081:31436/TCP   2m11s
```

add user

``curl -X POST localhost:32545/users/rune``

test

``curl localhost:32545/users/0``