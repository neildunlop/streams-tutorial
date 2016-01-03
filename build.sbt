import com.typesafe.sbt.SbtAspectj.AspectjKeys
import com.typesafe.sbt.SbtAspectj._

name := "streamstutorial"

version := "1.0"

scalaVersion := "2.11.7"

val akka            = "2.3.12"  //need this for reactive rabbit
val reactiveRabbit  = "1.0.3"
val akkaStream      = "2.0"
val kamonVersion    = "0.5.2"

/* dependencies */
libraryDependencies ++= Seq (
  "com.github.nscala-time" %% "nscala-time" % "1.4.0"

  // -- Testing --
  , "org.scalatest" %% "scalatest" % "2.2.2" % "test"

  // -- Logging --
  ,"ch.qos.logback" % "logback-classic" % "1.1.2"
  ,"com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"

  // -- Akka --
  ,"com.typesafe.akka" %% "akka-testkit" % akka % "test"
  ,"com.typesafe.akka" %% "akka-actor" % akka
  ,"com.typesafe.akka" %% "akka-slf4j" % akka

  // --Akka Streams
  ,"com.typesafe.akka" % "akka-stream-experimental_2.11" % akkaStream

  // -- Config --
  ,"com.typesafe" % "config" % "1.2.1"

  // -- kamon monitoring dependencies --
  ,"io.kamon" % "kamon-core_2.11" % kamonVersion
  ,"io.kamon" %% "kamon-core" % kamonVersion
  ,"io.kamon" %% "kamon-scala" % kamonVersion
  ,"io.kamon" %% "kamon-akka" % kamonVersion
  ,"io.kamon" %% "kamon-statsd" % kamonVersion
  ,"io.kamon" %% "kamon-log-reporter" % kamonVersion
  ,"io.kamon" %% "kamon-system-metrics" % kamonVersion
  ,"org.aspectj" % "aspectjweaver" % "1.8.5"
)

//configure aspectJ plugin to enable Kamon monitoring
aspectjSettings
javaOptions <++= AspectjKeys.weaverOptions in Aspectj
fork in run := true

connectInput in run := true