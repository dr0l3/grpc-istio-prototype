# Install microk8s

go to microk8s.io

``sudo snap alias microk8s.kubectl mkcl``

# install service

```
mkcl apply -f kubernetes/users.yaml
mkcl apply -f kubernetes/gateway.yaml
```


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

# test routing header routing

```
mkcl apply -f kubernetes/header-routing.yaml
curl localhost:32545/users/0
curl localhost:32545/users/0 -H "env: rune"
```

Should now see that you can decide which version to hit by using the header