import cats.effect.{ExitCode, IO, IOApp}
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._

import scala.concurrent.ExecutionContext.Implicits.global
import cats.implicits._
import io.grpc.ManagedChannelBuilder
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
import users.users.{CreateUserRequest, GetUserRequest, User, UserServiceGrpc}
import org.http4s.circe._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityEncoder._

import pureconfig._
import pureconfig.generic.auto._

case class RestApiConfig(userAddress: Option[String], conversationAddress: Option[String])

object RestApi extends IOApp{

  val config = ConfigSource.default.load[RestApiConfig].fold(failure => {
    println(failure.prettyPrint())
    throw new RuntimeException("FAIL")
  }, config => config)

  val channel = ManagedChannelBuilder.forAddress(config.userAddress.getOrElse("localhost"), 8081).usePlaintext().build()

  val userClient = UserServiceGrpc.stub(channel)


  def getUser(userId: Int): IO[Option[User]] = {
    println(s"Attempt to find user with id $userId")
    val res = IO(userClient.getById(GetUserRequest(userId)))
    IO.fromFuture(res).map{res =>
      println(res.user)
      res
    }
      .map(_.user)
  }

  def createUser(name: String): IO[User]= {
    IO.fromFuture(IO(userClient.create(CreateUserRequest(name))))
  }


  val service = HttpRoutes.of[IO] {
    case GET -> Root / "users" / IntVar(userId) =>
      getUser(userId).flatMap(Ok(_))
    case POST -> Root / "users" / name  =>
      createUser(name).flatMap(Ok(_))
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
