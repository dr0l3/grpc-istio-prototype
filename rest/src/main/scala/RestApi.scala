import brave.Tracing
import brave.grpc.GrpcTracing
import brave.propagation.B3Propagation
import brave.rpc.RpcTracing
import cats.effect.{ ExitCode, IO, IOApp }
import cats.implicits._
import conversations.conversations.{ ConversationServiceFs2Grpc, CreateConversationRequest, GetConversationRequest }
import io.circe.generic.auto._
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc._
import messages.messages.{ CreateMessageRequest, GetMessagesByConversationRequest, MessageServiceFs2Grpc }
import orchestrator.orchestrator.{ GetConversationDetailsRequest, OrchestratorServiceFs2Grpc }
import org.http4s._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze._
import pureconfig._
import pureconfig.generic.auto._
import users.users.{ CreateUserRequest, GetUserRequest, User, UserServiceFs2Grpc }
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.jaegertracing.Configuration
import io.opentracing.contrib.grpc.{ OpenTracingContextKey, TracingClientInterceptor }
import io.opentracing.util.GlobalTracer
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._

import scala.concurrent.ExecutionContext

case class RestApiConfig(
    userAddress: String,
    conversationAddress: String,
    messageAddress: String,
    orchestratorAddress: String
)

//class EnviromentInterceptor extends ClientInterceptor {
//  override def interceptCall[ReqT, RespT](method: MethodDescriptor[ReqT, RespT], callOptions: CallOptions, next: Channel): ClientCall[ReqT, RespT] = {
//    new ForwardingClientCall.SimpleForwardingClientCall[ReqT, RespT](next.newCall(method, callOptions)) {
//      override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit =  {
//        println("CLIENT INTERCEPTOR")
//        println(headers)
//        headers.put(Metadata.Key.of("x-custom-header", Metadata.ASCII_STRING_MARSHALLER), Constants.envContext.get())
//        super.start(responseListener, headers)
//      }
//    }
//  }
//}

object Constants {
  val envContext = Context.keyWithDefault("env", "base")
  val metadataKey = Metadata.Key.of("env", Metadata.ASCII_STRING_MARSHALLER)
}

case class CreateConversationInput(subject: Option[String], userId: Int)

class RestApi(
    userClient: UserServiceFs2Grpc[IO, Metadata],
    messageClient: MessageServiceFs2Grpc[IO, Metadata],
    conversationClient: ConversationServiceFs2Grpc[IO, Metadata],
    orchestatorClient: OrchestratorServiceFs2Grpc[IO, Metadata]
) {
  implicit val css = IO.contextShift(ExecutionContext.global)
  implicit val timer = IO.timer(ExecutionContext.global)

  implicit val decoder = jsonOf[IO, CreateConversationRequest]
  implicit val decoder2 = jsonOf[IO, CreateMessageRequest]

  def getUser(userId: Int, headers: Metadata): IO[Option[User]] = {
    println(s"Attempt to find user with id $userId in $headers")
    userClient
      .getById(GetUserRequest(userId), headers)
      .map { res =>
        println(res.user)
        res
      }
      .map(_.user)
  }

  def createUser(name: String, headers: Metadata): IO[User] = {
    userClient.create(CreateUserRequest(name), headers)
  }

  def envFromReq(req: Request[IO]): Metadata = {
    val env = req.headers.find(_.name.value == "env").map(_.value).getOrElse("base")
    println(s"Env: ${env}. Req ${req.headers}")
    req.headers.foreach(println)
    val metadata = new Metadata()
    metadata.put(Constants.metadataKey, env)
    req.headers.find(_.name.value == "x-b3-traceid").foreach(header => metadata.put(TracingKeys.traceIdKey, header.value))
    req.headers.find(_.name.value == "x-b3-spanid").foreach(header => metadata.put(TracingKeys.spanIdKey, header.value))
    metadata
  }

  def getHttpApp() = {
    val service = HttpRoutes.of[IO] {
      case req @ GET -> Root / "users" / IntVar(userId) =>
        getUser(userId, envFromReq(req)).flatMap(Ok(_))

      case req @ POST -> Root / "users" / name =>
        createUser(name, envFromReq(req)).flatMap(Ok(_))

      case req @ GET -> Root / "conversations" / "messages" / IntVar(conversationId) =>
        messageClient
          .getByConversation(GetMessagesByConversationRequest(conversationId), envFromReq(req))
          .flatMap(Ok(_))

      case req @ POST -> Root / "conversations" / "messages" =>
        for {
          input <- req.as[CreateMessageRequest]
          msg <- messageClient.create(input, envFromReq(req))
          res <- Ok(msg)
        } yield res

      case req @ GET -> Root / "conversation" / IntVar(csid) =>
        conversationClient.getById(GetConversationRequest(csid), envFromReq(req)).flatMap(Ok(_))

      case req @ POST -> Root / "conversations" =>
        for {
          request <- req.as[CreateConversationRequest]
          conv <- conversationClient.create(request, envFromReq(req))
          res <- Ok(conv)
        } yield res

      case req @ GET -> Root / "aggregate" / IntVar(csid) =>
        println("GET AGGREAGTE")
        orchestatorClient
          .getById(GetConversationDetailsRequest(csid), envFromReq(req))
          .map(v => {
            println(s"OMG RSULT $v")
            v
          })
          .flatMap(Ok(_))
    }

    Router("/" -> service).orNotFound
  }
}

object RestApiRunner extends IOApp {

  val tracing = Tracing.newBuilder().propagationFactory(B3Propagation.newFactoryBuilder().build()).build()
  val rpcTracing = RpcTracing.newBuilder(tracing).build()
  val grpcTracing = GrpcTracing.create(rpcTracing)

  val config =
    ConfigSource.default.load[RestApiConfig].fold(err => throw new RuntimeException(err.prettyPrint()), identity)

  override def run(args: List[String]): IO[ExitCode] = {

    def envToMetadata(env: String) = {
      val metadata = new Metadata()
      metadata.put(Constants.metadataKey, env)
      metadata
    }

    val userChan = ClientInterceptors.intercept(
      NettyChannelBuilder.forAddress(config.userAddress, 8081).usePlaintext().build(),
      new ZipkinClientInterceptor
    )
    val userClient = UserServiceFs2Grpc.client[IO, Metadata](userChan, identity)

    val convChan = ClientInterceptors.intercept(
      NettyChannelBuilder.forAddress(config.conversationAddress, 8084).usePlaintext().build(),
      new ZipkinClientInterceptor
    )
    val convClient = ConversationServiceFs2Grpc.client[IO, Metadata](convChan, identity)

    val messageChan = ClientInterceptors.intercept(
      NettyChannelBuilder.forAddress(config.messageAddress, 8082).usePlaintext().build(),
      new ZipkinClientInterceptor
    )
    val messageClient = MessageServiceFs2Grpc.client[IO, Metadata](messageChan, identity)

    val orchestaorChan = ClientInterceptors.intercept(
      NettyChannelBuilder.forAddress(config.orchestratorAddress, 8083).usePlaintext().build(),
      new ZipkinClientInterceptor
    )
    val orchestaorClient = OrchestratorServiceFs2Grpc.client[IO, Metadata](orchestaorChan, identity)

    val app = new RestApi(userClient, messageClient, convClient, orchestaorClient)

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(app.getHttpApp())
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
