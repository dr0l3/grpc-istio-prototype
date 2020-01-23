import cats.effect.{ExitCode, IO, IOApp}
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

import scala.concurrent.ExecutionContext.Implicits.global
import cats.implicits._
import io.grpc.{CallOptions, Channel, ClientCall, ClientInterceptor, ClientInterceptors, Context, ForwardingClientCall, ManagedChannelBuilder, Metadata, MethodDescriptor}
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
import users.users.{CreateUserRequest, GetUserRequest, User, UserServiceGrpc}
import org.http4s.circe._
import io.circe.generic.auto._
import io.grpc.Metadata.Key
import io.grpc.stub.MetadataUtils
import org.http4s.circe.CirceEntityEncoder._
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.Future

case class RestApiConfig(userAddress: Option[String], conversationAddress: Option[String])


class EnviromentInterceptor extends ClientInterceptor {
  override def interceptCall[ReqT, RespT](method: MethodDescriptor[ReqT, RespT], callOptions: CallOptions, next: Channel): ClientCall[ReqT, RespT] = {
    new ForwardingClientCall.SimpleForwardingClientCall[ReqT, RespT](next.newCall(method, callOptions)) {
      override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit =  {
        println("CLIENT INTERCEPTOR")
        println(headers)
        println(Constants.envContext.get())
        headers.put(Metadata.Key.of("x-custom-header", Metadata.ASCII_STRING_MARSHALLER), Constants.envContext.get())
        super.start(responseListener, headers)
      }
    }
  }
}

object Constants {
  val envContext = Context.keyWithDefault("env", "base")
}

object RestApi extends IOApp{

  val config = ConfigSource.default.load[RestApiConfig].fold(failure => {
    println(failure.prettyPrint())
    throw new RuntimeException("FAIL")
  }, config => config)

  val baseChan = ManagedChannelBuilder.forAddress(config.userAddress.getOrElse("localhost"), 8081).usePlaintext().build()
  val channel = ClientInterceptors.intercept(baseChan,new EnviromentInterceptor())

  val userClient = UserServiceGrpc.stub(baseChan).build(channel, CallOptions.DEFAULT)


  def getUser(userId: Int, environment: String): Future[Option[User]] = {
    println(s"Attempt to find user with id $userId in $environment")
    Context.current().withValue(Constants.envContext, environment).call(() =>{
      userClient.getById(GetUserRequest(userId))
        .map{res =>
          println(res.user)
          res
        }
        .map(_.user)
    })
  }

  def createUser(name: String, environment: String): Future[User]= {
    Context.current().withValue(Constants.envContext, environment).call(() => {
      userClient.create(CreateUserRequest(name))
    })
  }

  def envFromReq(req: Request[IO]): String = {
    val env = req.headers.find(_.name.value == "env").map(_.value).getOrElse("base")
    println(s"Env: ${env}. Req ${req.headers}")
    env
  }

  val service = HttpRoutes.of[IO] {
    case req @ GET -> Root / "users" / IntVar(userId) =>
      IO.fromFuture(IO(getUser(userId, envFromReq(req)))).flatMap(Ok(_))
    case req @POST -> Root / "users" / name  =>
      IO.fromFuture(IO(createUser(name, envFromReq(req)))).flatMap(Ok(_))
  }

  val services = service

  val httpApp = Router("/" -> services).orNotFound

  override def run(args: List[String]): IO[ExitCode] = {

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)

  }
}
