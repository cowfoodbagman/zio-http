import sbt._

object Dependencies {
  val JwtCoreVersion                = "9.0.5"
  val NettyVersion                  = "4.1.76.Final"
  val NettyIncubatorVersion         = "0.0.13.Final"
  val ScalaCompactCollectionVersion = "2.7.0"
  val ZioVersion                    = "1.0.14"
  val SttpVersion                   = "3.3.18"
//  val ZioSchema                     = "0.1.8"
  val ZioJson                       = "0.2.0-M3"

  val `jwt-core`                 = "com.github.jwt-scala"   %% "jwt-core"                % JwtCoreVersion
  val `scala-compact-collection` = "org.scala-lang.modules" %% "scala-collection-compat" % ScalaCompactCollectionVersion

  val netty             = "io.netty" % "netty-all" % NettyVersion
  val `netty-incubator` =
    "io.netty.incubator" % "netty-incubator-transport-native-io_uring" % NettyIncubatorVersion classifier "linux-x86_64"

  val zio            = "dev.zio" %% "zio"          % ZioVersion
  val `zio-streams`  = "dev.zio" %% "zio-streams"  % ZioVersion
  val `zio-test`     = "dev.zio" %% "zio-test"     % ZioVersion % "test"
  val `zio-test-sbt` = "dev.zio" %% "zio-test-sbt" % ZioVersion % "test"

  val `zio-json_2.13`        = "dev.zio" % "zio-json_2.13"        % ZioJson
  val `zio-json-macros_2.13` = "dev.zio" % "zio-json-macros_2.13" % ZioJson

  val `zio-json_2.12`        = "dev.zio" % "zio-json_2.12"        % ZioJson
  val `zio-json-macros_2.12` = "dev.zio" % "zio-json-macros_2.12" % ZioJson
}
