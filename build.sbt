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
  libraryDependencies += "com.github.pureconfig" %% "pureconfig" % "0.12.2"
)


val grpcDependencies = Seq(
  libraryDependencies ++= Seq(
    "com.thesamet.scalapb" %% "compilerplugin" % "0.9.4",
    "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
  )
)

val grpcSettings = Seq(
  PB.targets in Compile := Seq(
    scalapb.gen() -> (sourceManaged in Compile).value
  )
)

lazy val protocols = (project in file("protocols"))
  .settings(
    grpcDependencies,
    grpcSettings
  )

lazy val users =(project in file("users"))
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
  .dependsOn(users, conversations, protocols)
  .settings(
    scalacOptions ++= Seq("-Ypartial-unification"),
    http4sSettings,
    baseDependencies
  )
  .enablePlugins(JavaAppPackaging,DockerPlugin)

lazy val root = (project in file("."))
  .aggregate(gateway, users, conversations, protocols)