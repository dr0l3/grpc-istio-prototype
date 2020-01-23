import io.grpc._
import users.users._

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UserServiceImpl extends UserServiceGrpc.UserService{
  val userDb = ListBuffer.empty[User]

  override def create(request: CreateUserRequest): Future[User] = Future{
    println("")
    val id = userDb.size
    val user = User(id, request.name)
    userDb.+=(user)
    user
  }

  override def getById(request: GetUserRequest): Future[GetUserResponse] = Future {
    println(s"Attempt to fetch user ${request}")
    GetUserResponse(userDb.find(_.id == request.id))
  }
}

object UserRunner extends App {
  val serverDef = ServerInterceptors.intercept(UserServiceGrpc.bindService(new UserServiceImpl(), ExecutionContext.global), new Zomg())

  val server = ServerBuilder
    .forPort(8081)
    .addService(serverDef)
    .build
    .start

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = server.shutdown()
  })

    server.awaitTermination()
}

class Zomg extends ServerInterceptor {
  override def interceptCall[ReqT, RespT](call: ServerCall[ReqT, RespT], headers: Metadata, next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {
    val current = Context.current()
    println("SERVER INTERCEPT")
    println(headers)
    Contexts.interceptCall(current, call, headers, next)
  }
}

class ZomgClinet extends ClientInterceptor {
  override def interceptCall[ReqT, RespT](method: MethodDescriptor[ReqT, RespT], callOptions: CallOptions, next: Channel): ClientCall[ReqT, RespT] = {
    new ForwardingClientCall.SimpleForwardingClientCall[ReqT, RespT](next.newCall(method, callOptions)) {
      override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit =  {
        println("CLIENT INTERCEPTOR")
        println(headers)
        headers.put(Metadata.Key.of("x-custom-header", Metadata.ASCII_STRING_MARSHALLER), Constants.envContext.get())
        super.start(responseListener, headers)
      }
    }
  }
}

object Constants {
  val envContext = Context.keyWithDefault("env", "base")
}

//object Headers extends App {
//  val port = 9876
//  val serverDef = ServerInterceptors.intercept(UserServiceGrpc.bindService(new UserServiceImpl(), ExecutionContext.global), new Zomg())
//
//  val server = ServerBuilder
//    .forPort(port)
//    .addService(serverDef)
//    .build
//    .start
//
//  val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
//  val wht = ClientInterceptors.intercept(channel, new ZomgClinet())
//
//  val userClient = UserServiceGrpc.stub(channel).build(wht, CallOptions.DEFAULT)
//
//  Context.current().withValue(Constants.envContext, "rune").call(() => {
//    val res = userClient.create(CreateUserRequest("rune"))
//    println(res)
//    res
//  })
//
//  Context.current().withValue(Constants.envContext, "jens").run(() => {
//    val res2 = userClient.getById(GetUserRequest(0))
//    println(res2)
//  })
//
//
//  server.awaitTermination()
//}