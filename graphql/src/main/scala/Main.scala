import caliban.GraphQL.graphQL
import caliban.RootResolver
import cats.effect.IO
import io.grpc.{ClientInterceptors, Metadata}
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import users.users.{CreateUserRequest, CreateUserResponse, GetUserRequest, GetUserResponse, User, UserServiceFs2Grpc}

import scala.concurrent.ExecutionContext
import caliban.interop.cats.implicits._
import cats.effect.{ExitCode, IO, IOApp}
import zio.DefaultRuntime

case class Meta(env: String)

object HelloWorld extends App {
  implicit val shift = IO.contextShift(ExecutionContext.global)
  implicit val time = IO.timer(ExecutionContext.global)

  val userChan = ClientInterceptors.intercept(
    NettyChannelBuilder.forAddress("localhost", 8081).usePlaintext().build(),
    new ZipkinClientInterceptor
  )

  def metaConverter(meta: Meta) = {
    val metadata = new Metadata()
    metadata.put(Metadata.Key.of("meta", Metadata.ASCII_STRING_MARSHALLER), meta.env)
    metadata
  }

  val userClient = UserServiceFs2Grpc.client[IO, Meta](userChan, metaConverter)

  case class Queries(
                      getById: (GetUserRequest) => IO[GetUserResponse]
                    )

  case class Mutations(
                        create: (CreateUserRequest) => IO[User],
                      )

  val queries = Queries(v => userClient.getById(v,Meta("rune")))
  val mutations = Mutations(v => userClient.create(v, Meta("rune")))
  val interpreter = graphQL(RootResolver(queries, mutations))


  val query =
    """
      |{
      |
      |}
      |""".stripMargin
}