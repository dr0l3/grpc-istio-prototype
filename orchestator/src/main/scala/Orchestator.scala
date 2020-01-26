import Constants._
import brave.Tracing
import brave.grpc.GrpcTracing
import brave.rpc.RpcTracing
import cats.effect.{ExitCode, IO, IOApp}
import conversations.conversations.{ConversationServiceFs2Grpc, GetConversationRequest}
import io.grpc.netty.shaded.io.grpc.netty.{NettyChannelBuilder, NettyServerBuilder}
import io.grpc.{ClientInterceptors, Metadata, ServerInterceptors}
import messages.messages.{GetMessagesByConversationRequest, MessageServiceFs2Grpc}
import orchestrator.orchestrator.{ConversationDetails, GetConversationDetailsRequest, GetConversationDetailsResponse, OrchestratorServiceFs2Grpc}
import org.lyranthe.fs2_grpc.java_runtime.implicits._
import pureconfig._
import pureconfig.generic.auto._
import users.users.{GetUserRequest, UserServiceFs2Grpc}

object Constants {
  val envKey = Metadata.Key.of("env", Metadata.ASCII_STRING_MARSHALLER)
}

class Orchestator(
    userClient: UserServiceFs2Grpc[IO, Metadata],
    conversationClient: ConversationServiceFs2Grpc[IO, Metadata],
    messageClient: MessageServiceFs2Grpc[IO, Metadata],
    tracer: Tracing
) extends OrchestratorServiceFs2Grpc[IO, Metadata] {

  override def getById(request: GetConversationDetailsRequest, ctx: Metadata): IO[GetConversationDetailsResponse] = {
    println("STARTTING")
    println(ctx)
    println(tracer.currentTraceContext().get())
    for {
      conversation <- conversationClient
        .getById(GetConversationRequest(request.id), ctx)
        .map(_.conversation)
      _ = println("conve")
      _ = println(ctx)
      user <- conversation match {
        case Some(value) => userClient.getById(GetUserRequest(value.userId), ctx).map(_.user)
        case None        => IO(None)
      }
      _ = println("user")
      _ = println(ctx)
      messages <- conversation match {
        case Some(value) =>
          messageClient
            .getByConversation(GetMessagesByConversationRequest(value.id), ctx)
            .map(_.conversationId)
        case None => IO(Nil)
      }
    } yield {
      println(conversation)
      println(user)
      println(messages)
      val details = (conversation, user) match {
        case (Some(conv), Some(us)) => Option(ConversationDetails(us, conv, messages))
        case _                      => None
      }

      GetConversationDetailsResponse(details)
    }
  }
}

case class OrchestatorConfig(userAdress: String, conversationAddress: String, messagesAddress: String)

object Main extends IOApp {
  val tracing = Tracing.newBuilder().build()
  val rpcTracing = RpcTracing.newBuilder(tracing).build()
  val grpcTracing = GrpcTracing.create(rpcTracing)

  val config =
    ConfigSource.default.load[OrchestatorConfig].fold(err => throw new RuntimeException(err.prettyPrint()), identity)

  def envToMetadata(env: String) = {
    val metadata = new Metadata()
    metadata.put(envKey, env)
    metadata
  }

  val userCHan = ClientInterceptors.intercept(
    NettyChannelBuilder.forAddress(config.userAdress, 8081).usePlaintext().build(),
    new ListAllHeadersClietn(),
    new ZipkinClientInterceptor(),
    new ListAllHeadersClietn()
  )
  val userClient = UserServiceFs2Grpc.client[IO, Metadata](userCHan, identity)

  val convChan = ClientInterceptors.intercept(
    NettyChannelBuilder.forAddress(config.conversationAddress, 8084).usePlaintext().build(),
    new ListAllHeadersClietn(),
    new ZipkinClientInterceptor(),
    new ListAllHeadersClietn()
  )

  val convClient = ConversationServiceFs2Grpc.client[IO, Metadata](convChan, identity)

  val msgChan = ClientInterceptors.intercept(
    NettyChannelBuilder.forAddress(config.messagesAddress, 8082).usePlaintext().build(),
    new ListAllHeadersClietn(),
    new ZipkinClientInterceptor(),
    new ListAllHeadersClietn()
  )
  val msgClient = MessageServiceFs2Grpc.client[IO, Metadata](msgChan, identity)

  val userService = ServerInterceptors.intercept(
    OrchestratorServiceFs2Grpc.bindService(new Orchestator(userClient, convClient, msgClient, tracing)),
    new ListAllHeadersInterceptor()
  )
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
