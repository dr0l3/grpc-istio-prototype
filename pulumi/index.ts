import * as k8s from "@pulumi/kubernetes";
import * as pulumi from "@pulumi/pulumi";

const appLabels = { app: "nginx" };

const config = new pulumi.Config();
const isLocalCluster = config.requireBoolean("isLocalCluster");

function createLabels(version: string, name: string) {
    return {
        app: name,
        version: version
    }
}

const serviceAddress = [
    {
        name: "USER_ADRESS",
        value: "users"
    },
    {
        name: "CONVERSATION_ADRESS",
        value: "conversations"
    },
    {
        name: "MESSAGES_ADDRESS",
        value: "messages"
    },
    {
        name: "ORCHESTATOR_ADDRESS",
        value: "orchestator"
    }
    ]

function createDeployment(versionStr: string, imageSuffix: string, port: number, portName: string = "grpc") {
    const name = `${imageSuffix}-${versionStr}`;
    const imageName = `localhost:32000/grpc-${imageSuffix}`;
    return  new k8s.apps.v1.Deployment(name, {
        spec: {
            selector : {
                matchLabels:  createLabels(versionStr, imageSuffix)
            },
            replicas: 1,
            template: {
                metadata: {
                    name: imageSuffix,
                    labels: createLabels(versionStr, imageSuffix)
                },
                spec: {
                    containers: [
                        {
                            name: imageSuffix,
                            image: imageName,
                            imagePullPolicy: "Always",
                            ports: [
                                {
                                    containerPort: port,
                                    name: portName
                                }
                                ],
                            env: serviceAddress
                        }
                    ]
                }
            },

        }
    });
}

function createServiceForDeployment(name: string, deployment: k8s.apps.v1.Deployment, portNumber: number, portName: string = "grpc") {
    return new k8s.core.v1.Service(name, {
        metadata: {
            name: name
        },
        spec: {
            type: "NodePort",
            ports: [{port: portNumber, name: portName, protocol: "TCP"}],
            selector: {
                app: name
            }
        }
    })
}

const usersName = "users";
const user1Deployment = createDeployment("1", usersName, 8081);
const user2Deployment = createDeployment("2", usersName, 8081);
const userService = createServiceForDeployment(usersName, user1Deployment, 8081);

const conversationsName = "conversations"
const conversationDeployment = createDeployment("1", conversationsName, 8084);
const conversationService = createServiceForDeployment(conversationsName, conversationDeployment, 8084);

const messagesDeployment = createDeployment("1", "messages", 8082);
const messagesService = createServiceForDeployment("messages", messagesDeployment, 8082);


const orchestratorDeployment = createDeployment("1", "orchestator", 8083);
const orchestratorService = createServiceForDeployment("orchestator", orchestratorDeployment,8083);

const gatewayDeployment = createDeployment("1", "gateway", 8080, "http");
const gatewayService = createServiceForDeployment("gateway", gatewayDeployment, 8080, "http")

export const users = user1Deployment.metadata.name;

export const ip = isLocalCluster
    ? gatewayService.spec.clusterIP
    : gatewayService.status.loadBalancer.ingress[0].ip;
