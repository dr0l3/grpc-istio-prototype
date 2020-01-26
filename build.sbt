name := "grpc-istio"

version := "0.1"

scalaVersion := "2.12.10"




val http4sVersion = "0.20.15"
val http4sSettings = Seq(
  libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "io.circe" %% "circe-generic" % "0.11.2",
    "io.circe" %% "circe-literal" % "0.11.2"
  )
)

val baseDependencies = Seq(
  libraryDependencies ++= Seq(
    "com.github.pureconfig" %% "pureconfig" % "0.12.2",
    "io.opentracing.contrib" % "opentracing-grpc" % "0.2.1",
    "io.jaegertracing" % "jaeger-core" % "1.1.0",
    "io.zipkin.brave" % "brave-instrumentation-grpc" % "5.9.1"
  )
)


val grpcDependencies = Seq(
  libraryDependencies ++= Seq(
    "io.grpc" % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion
    //"com.thesamet.scalapb" %% "compilerplugin" % "0.9.4",
    //"io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
    //"com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
  )
)

val grpcSettings = Seq(
  //PB.targets in Compile := Seq(
   // scalapb.gen() -> (sourceManaged in Compile).value
  //
)

lazy val protocols = (project in file("protocols"))
  .settings(
    grpcDependencies,
    grpcSettings,
    baseDependencies
  ).enablePlugins(Fs2Grpc)

lazy val users =(project in file("users"))
  .settings(
    grpcDependencies,
    grpcSettings,
    baseDependencies
  ).dependsOn(protocols)
  .enablePlugins(JavaAppPackaging,DockerPlugin)

lazy val messages =(project in file("message"))
  .settings(
    grpcDependencies,
    grpcSettings,
    baseDependencies
  ).dependsOn(protocols)
  .enablePlugins(JavaAppPackaging,DockerPlugin)

lazy val orchestator =(project in file("orchestator"))
  .settings(
    grpcDependencies,
    grpcSettings,
    baseDependencies
  ).dependsOn(protocols)
  .enablePlugins(JavaAppPackaging,DockerPlugin)

lazy val conversations = (project in file("conversations"))
  .settings(
    grpcDependencies,
    grpcSettings,
    baseDependencies
  ).dependsOn(users, protocols)
  .enablePlugins(JavaAppPackaging,DockerPlugin)

lazy val gateway = (project in file("rest"))
  .dependsOn(protocols)
  .settings(
    scalacOptions ++= Seq("-Ypartial-unification"),
    http4sSettings,
    baseDependencies
  )
  .enablePlugins(JavaAppPackaging,DockerPlugin)

lazy val root = (project in file("."))
  .aggregate(gateway, users, conversations, protocols, orchestator, messages)