import cats.effect.{ExitCode, IO, IOApp}
import conversations.conversations.{ConversationServiceFs2Grpc, GetConversationRequest}
import io.grpc.{ClientInterceptors, Metadata, ServerInterceptors}
import io.grpc.netty.shaded.io.grpc.netty.{NettyChannelBuilder, NettyServerBuilder}
import messages.messages.{GetMessagesByConversationRequest, MessageServiceFs2Grpc}
import orchestrator.orchestrator.{ConversationDetails, GetConversationDetailsRequest, GetConversationDetailsResponse, OrchestratorServiceFs2Grpc}
import users.users.{GetUserRequest, UserServiceFs2Grpc}
import org.lyranthe.fs2_grpc.java_runtime.implicits._
import Constants._
import pureconfig._
import pureconfig.generic.auto._

object Constants {
  val envKey = Metadata.Key.of("env", Metadata.ASCII_STRING_MARSHALLER)
}

class Orchestator(userClient: UserServiceFs2Grpc[IO, String], conversationClient: ConversationServiceFs2Grpc[IO, String], messageClient: MessageServiceFs2Grpc[IO, String]) extends OrchestratorServiceFs2Grpc[IO, Metadata] {

  override def getById(request: GetConversationDetailsRequest, ctx: Metadata): IO[GetConversationDetailsResponse] = {
    println("STARTTING")
    for {
      conversation <- conversationClient.getById(GetConversationRequest(request.id), ctx.get(envKey)).map(_.conversation)
      _ = println("conve")
      user <- conversation match {
        case Some(value) => userClient.getById(GetUserRequest(value.userId), ctx.get(envKey)).map(_.user)
        case None => IO(None)
      }
      _ = println("user")
      messages <- conversation match {
        case Some(value) => messageClient.getByConversation(GetMessagesByConversationRequest(value.id), ctx.get(envKey)).map(_.conversationId)
        case None => IO(Nil)
      }
    } yield {
      println(conversation)
      println(user)
      println(messages)
      val details = (conversation, user) match {
        case (Some(conv), Some(us)) => Option(ConversationDetails(us, conv, messages))
        case _   => None
      }

      GetConversationDetailsResponse(details)
    }
  }
}

case class OrchestatorConfig(userAdress: String, conversationAddress: String, messagesAddress: String)

object Main extends IOApp {
  val config = ConfigSource.default.load[OrchestatorConfig].fold(err => throw new RuntimeException(err.prettyPrint()), identity)

  def envToMetadata(env: String) = {
    val metadata = new Metadata()
    metadata.put(envKey, env)
    metadata
  }

  val userCHan = NettyChannelBuilder.forAddress(config.userAdress, 8081).usePlaintext().build()
  val userClient = UserServiceFs2Grpc.client[IO, String](userCHan,envToMetadata)

  val convChan = NettyChannelBuilder.forAddress(config.conversationAddress, 8084).usePlaintext().build()
  val convClient = ConversationServiceFs2Grpc.client[IO, String](convChan,envToMetadata)

  val msgChan = NettyChannelBuilder.forAddress(config.messagesAddress, 8082).usePlaintext().build()
  val msgClient = MessageServiceFs2Grpc.client[IO, String](msgChan,envToMetadata)

  val userService = ServerInterceptors.intercept(OrchestratorServiceFs2Grpc.bindService(new Orchestator(userClient, convClient, msgClient)))
  override def run(args: List[String]): IO[ExitCode] = {
    NettyServerBuilder
      .forPort(8083)
      .addService(userService)
      .stream[IO]
      .evalMap(server => IO(server.start()))
      .evalMap(_ => IO.never)
      .compile
      .drain
      .map(_ => ExitCode.Success)
  }
}
