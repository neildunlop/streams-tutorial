package com.datinko.streamstutorial

import akka.stream.{SourceShape}
import akka.stream.scaladsl.{Flow, Zip, GraphDSL, Source}
import kamon.Kamon

import scala.concurrent.duration.FiniteDuration

/**
 * An Akka Streams Source helper that produces  messages at a defined rate.
 */
object ThrottledProducer {

  def produceThrottled(initialDelay: FiniteDuration, interval: FiniteDuration, numberOfMessages: Int, name: String) = {

    val ticker = Source.tick(initialDelay, interval, Tick)
    val numbers = 1 to numberOfMessages
    val rangeMessageSource = Source(numbers.map(message => s"Message $message"))

    //define a stream to bring it all together..
    val throttledStream = Source.fromGraph(GraphDSL.create() { implicit builder =>

      //create a Kamon counter so we can track number of messages produced
      val createCounter = Kamon.metrics.counter("throttledProducer-create-counter")

      //define a zip operation that expects a tuple with a Tick and a Message in it..
      //(Note that the operations must be added to the builder before they can be used)
      val zip = builder.add(Zip[Tick.type, String])

      //create a flow to extract the second element in the tuple (our message - we dont need the tick part after this stage)
      val messageExtractorFlow = builder.add(Flow[(Tick.type, String)].map(_._2))

      //create a flow to log performance information to Kamon and pass on the message object unmolested
      val statsDExporterFlow = builder.add(Flow[(String)].map{message => createCounter.increment(1); message})


      //import this so we can use the ~> syntax
      import GraphDSL.Implicits._

      //define the inputs for the zip function - it wont fire until something arrives at both inputs, so we are essentially
      //throttling the output of this steam
      ticker ~> zip.in0
      rangeMessageSource ~> zip.in1

      //send the output of our zip operation to a processing messageExtractorFlow that just allows us to take the second element of each Tuple, in our case
      //this is the AMQP message, we dont care about the Tick, it was just for timing and we can throw it away.
      //route that to the 'out' Sink, the RabbitMQ exchange.
      zip.out ~> messageExtractorFlow ~> statsDExporterFlow

      SourceShape(statsDExporterFlow.out)
    })
    throttledStream
  }
}

