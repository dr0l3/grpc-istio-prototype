
import cats.effect.{ExitCode, IO, IOApp}
import io.grpc.{Metadata, ServerBuilder}
import users.users._
import fs2._
import io.grpc._
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import org.lyranthe.fs2_grpc.java_runtime.implicits._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class User2 extends UserServiceFs2Grpc[IO, Metadata] {
  val users = ListBuffer.empty[User]
  override def create(request: CreateUserRequest, ctx: Metadata): IO[User] = IO {
    println("SERVER INTERCEPT")
    println(ctx)
    val user = User(users.size, request.name)
    users += user
    user
  }

  override def getById(request: GetUserRequest, ctx: Metadata): IO[GetUserResponse] = IO{
    println("SERVER INTERCEPT")
    println(ctx)
    GetUserResponse(users.find(_.id == request.id))
  }
}


object Main extends IOApp {

  val userService = ServerInterceptors.intercept(UserServiceFs2Grpc.bindService(new User2()))
  override def run(args: List[String]): IO[ExitCode] = {
    NettyServerBuilder
      .forPort(8081)
      .addService(userService)
      .stream[IO]
      .evalMap(server => IO(server.start()))
      .evalMap(_ => IO.never)
      .compile
      .drain
      .map(_ => ExitCode.Success)
  }
}