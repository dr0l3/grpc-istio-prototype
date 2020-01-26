
import brave.Tracing
import brave.grpc.GrpcTracing
import brave.propagation.B3Propagation
import brave.rpc.RpcTracing
import cats.effect.{ExitCode, IO, IOApp}
import io.grpc.{Metadata, ServerBuilder}
import users.users._
import fs2._
import io.grpc._
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.jaegertracing.Configuration
import io.opentracing.contrib.grpc.TracingServerInterceptor
import io.opentracing.util.GlobalTracer
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

  val tracing = Tracing.newBuilder().propagationFactory(B3Propagation.newFactoryBuilder().build()).build()
  val rpcTracing = RpcTracing.newBuilder(tracing).build()
  val grpcTracing = GrpcTracing.create(rpcTracing)


  val userService = ServerInterceptors.intercept(UserServiceFs2Grpc.bindService(new User2()), new ListAllHeadersInterceptor(), grpcTracing.newServerInterceptor(), new ListAllHeadersInterceptor())
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