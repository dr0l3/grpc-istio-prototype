import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import io.circe.generic.auto._
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc._
import org.http4s._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze._
import pureconfig._
import users.users.{CreateUserRequest, GetUserRequest, User, UserServiceFs2Grpc}

import scala.concurrent.ExecutionContext

case class RestApiConfig(userAddress: String, conversationAddress: String)


class EnviromentInterceptor extends ClientInterceptor {
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
  val metadataKey = Metadata.Key.of("env", Metadata.ASCII_STRING_MARSHALLER)
}

class RestApi(userClient: UserServiceFs2Grpc[IO, String]) {
  implicit val css = IO.contextShift(ExecutionContext.global)
  implicit val timer = IO.timer(ExecutionContext.global)
  def getUser(userId: Int, environment: String): IO[Option[User]] = {
    println(s"Attempt to find user with id $userId in $environment")
    userClient.getById(GetUserRequest(userId), environment)
      .map{res =>
        println(res.user)
        res
      }
      .map(_.user)
  }

  def createUser(name: String, environment: String): IO[User]= {
    userClient.create(CreateUserRequest(name), environment)
  }

  def envFromReq(req: Request[IO]): String = {
    val env = req.headers.find(_.name.value == "env").map(_.value).getOrElse("base")
    println(s"Env: ${env}. Req ${req.headers}")
    env
  }

  def getHttpApp() = {
    val service = HttpRoutes.of[IO] {
      case req @ GET -> Root / "users" / IntVar(userId) =>
        getUser(userId, envFromReq(req)).flatMap(Ok(_))
      case req @ POST -> Root / "users" / name  =>
        createUser(name, envFromReq(req)).flatMap(Ok(_))
    }

    Router("/" -> service).orNotFound
  }
}

object RestApiRunner extends IOApp{

  val config = ConfigSource.default.load[RestApiConfig].fold(err => throw new RuntimeException(err.prettyPrint()), identity)

  override def run(args: List[String]): IO[ExitCode] = {

    def envToMetadata(env: String) = {
      val metadata = new Metadata()
      metadata.put(Constants.metadataKey, env)
      metadata
    }

    val basechan2 = NettyChannelBuilder.forAddress(config.userAddress, 8081).usePlaintext().build()
    val chan2 =  ClientInterceptors.intercept(basechan2,new EnviromentInterceptor())

    val client2 = UserServiceFs2Grpc.client[IO, String](chan2,envToMetadata)
    val app = new RestApi(client2)

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(app.getHttpApp())
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
