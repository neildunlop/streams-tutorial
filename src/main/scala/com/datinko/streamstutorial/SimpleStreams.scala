package com.datinko.streamstutorial

import akka.stream.{ClosedShape, ActorMaterializer}
import akka.stream.scaladsl._
import scala.concurrent.duration._

/**
 * An examples of the simplest possible akka stream.
 */
object SimpleStreams {

  def printSimpleMessagesToConsole(implicit materializer: ActorMaterializer) = {

    val simpleMessages = "Message 1" :: "Message 2" :: "Message 3" :: "Message 4" :: "Message 5" :: Nil

    Source(simpleMessages)
      .map(println(_))
      .to(Sink.ignore)
      .run()
  }

  def throttledProducerToConsole() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 20 milliseconds, 20000, "fastProducer"))
      val printFlow = builder.add(Flow[(String)].map{println(_)})
      val sink = builder.add(Sink.ignore)

      import GraphDSL.Implicits._

      source ~> printFlow ~> sink

      ClosedShape
    })
    theGraph
  }
}

