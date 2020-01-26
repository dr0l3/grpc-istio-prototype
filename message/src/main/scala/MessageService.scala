import brave.Tracing
import brave.grpc.GrpcTracing
import brave.propagation.{B3Propagation, Propagation}
import brave.rpc.RpcTracing
import cats.effect.{ExitCode, IO, IOApp}
import conversations.conversations.ConversationServiceFs2Grpc
import io.grpc.{Context, Contexts, Metadata, ServerCall, ServerCallHandler, ServerInterceptor, ServerInterceptors}
import io.grpc.netty.shaded.io.grpc.netty.{NettyChannelBuilder, NettyServerBuilder}
import io.jaegertracing.Configuration
import io.opentracing.contrib.grpc.TracingServerInterceptor
import io.opentracing.util.GlobalTracer
import messages.messages.{CreateMessageRequest, CreateMessageResponse, GetMessagesByConversationRequest, GetMessagesByConversationResponse, Message, MessageServiceFs2Grpc}
import orchestrator.orchestrator.OrchestratorServiceFs2Grpc
import org.lyranthe.fs2_grpc.java_runtime.implicits._

import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._

class MessageServiceImpl extends MessageServiceFs2Grpc[IO, Metadata]{
  val db = ListBuffer.empty[Message]
  override def create(request: CreateMessageRequest, ctx: Metadata): IO[CreateMessageResponse] = {
    val message = Message(db.size, request.conversationId, request.text, request.link)
    db += message
    IO(CreateMessageResponse(Option(message)))
  }

  override def getByConversation(request: GetMessagesByConversationRequest, ctx: Metadata): IO[GetMessagesByConversationResponse] = {
    IO(GetMessagesByConversationResponse(db.filter(_.conversationId == request.conversationId)))
  }
}

object Main extends IOApp {
  val tracing = Tracing.newBuilder().propagationFactory(B3Propagation.newFactoryBuilder().build()).build()
  val rpcTracing = RpcTracing.newBuilder(tracing).build()
  val grpcTracing = GrpcTracing.create(rpcTracing)

  val userService = ServerInterceptors.intercept(MessageServiceFs2Grpc.bindService(new MessageServiceImpl()), new ListAllHeadersInterceptor(), grpcTracing.newServerInterceptor())
  override def run(args: List[String]): IO[ExitCode] = {
    NettyServerBuilder
      .forPort(8082)
      .addService(userService)
      .stream[IO]
      .evalMap(server => IO(server.start()))
      .evalMap(_ => IO.never)
      .compile
      .drain
      .map(_ => ExitCode.Success)
  }
}