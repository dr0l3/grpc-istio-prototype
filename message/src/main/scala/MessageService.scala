import cats.effect.{ExitCode, IO, IOApp}
import conversations.conversations.ConversationServiceFs2Grpc
import io.grpc.{Metadata, ServerInterceptors}
import io.grpc.netty.shaded.io.grpc.netty.{NettyChannelBuilder, NettyServerBuilder}
import messages.messages.{CreateMessageRequest, CreateMessageResponse, GetMessagesByConversationRequest, GetMessagesByConversationResponse, Message, MessageServiceFs2Grpc}
import orchestrator.orchestrator.OrchestratorServiceFs2Grpc
import org.lyranthe.fs2_grpc.java_runtime.implicits._


import scala.collection.mutable.ListBuffer

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

  val userService = ServerInterceptors.intercept(MessageServiceFs2Grpc.bindService(new MessageServiceImpl()))
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