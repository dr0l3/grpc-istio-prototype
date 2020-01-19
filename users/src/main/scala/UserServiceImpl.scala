import java.util.UUID

import io.grpc.ServerBuilder
import users.users.{CreateUserRequest, GetUserRequest, GetUserResponse, User, UserServiceGrpc}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class UserServiceImpl extends UserServiceGrpc.UserService{
  val userDb = ListBuffer.empty[User]

  override def create(request: CreateUserRequest): Future[User] = Future{
    val id = userDb.size
    val user = User(id, request.name)
    userDb.+=(user)
    user
  }

  override def getById(request: GetUserRequest): Future[GetUserResponse] = Future {
    GetUserResponse(userDb.find(_.id == request.id))
  }
}

object UserRunner extends App {
  val serverDef = UserServiceGrpc.bindService(new UserServiceImpl(), ExecutionContext.global)

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