import brave.Tracing
import brave.grpc.GrpcTracing
import brave.propagation.B3Propagation
import brave.rpc.RpcTracing
import cats.effect.{ExitCode, IO, IOApp}
import conversations.conversations.{Conversation, ConversationServiceFs2Grpc, ConversationServiceGrpc, CreateConversationRequest, CreateConversationResponse, GetConversationRequest, GetConversationResponse}
import io.grpc.{Metadata, ServerInterceptors}
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.jaegertracing.Configuration
import io.opentracing.Tracer
import io.opentracing.contrib.grpc.TracingServerInterceptor
import io.opentracing.util.GlobalTracer
import users.users.{User, UserServiceFs2Grpc}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.lyranthe.fs2_grpc.java_runtime.implicits._


class ConversationServiceImpl extends ConversationServiceFs2Grpc[IO, Metadata] {
  val conversationDb = ListBuffer.empty[Conversation]
  override def create(request: CreateConversationRequest, ctx: Metadata): IO[CreateConversationResponse] = IO{
    val id = conversationDb.size
    val conversation = Conversation(id, request.subject, request.creator)
    conversationDb.+=(conversation)
    CreateConversationResponse(conversation)
  }

  override def getById(request: GetConversationRequest, ctx: Metadata): IO[GetConversationResponse] = IO{
    GetConversationResponse(conversationDb.find(_.id == request.id))
  }
}

object Main extends IOApp {

  val tracing = Tracing.newBuilder().propagationFactory(B3Propagation.newFactoryBuilder().build()).build()
  val rpcTracing = RpcTracing.newBuilder(tracing).build()
  val grpcTracing = GrpcTracing.create(rpcTracing)

  val userService = ServerInterceptors.intercept(ConversationServiceFs2Grpc.bindService(new ConversationServiceImpl()), new ListAllHeadersInterceptor(), grpcTracing.newServerInterceptor())
  override def run(args: List[String]): IO[ExitCode] = {
    NettyServerBuilder
      .forPort(8084)
      .addService(userService)
      .stream[IO]
      .evalMap(server => IO(server.start()))
      .evalMap(_ => IO.never)
      .compile
      .drain
      .map(_ => ExitCode.Success)
  }
}