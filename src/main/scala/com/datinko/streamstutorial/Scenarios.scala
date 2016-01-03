package com.datinko.streamstutorial

import akka.actor.Props
import akka.stream.{OverflowStrategy, ClosedShape}
import akka.stream.scaladsl._
import com.datinko.streamstutorial.actors.SlowDownActor
import scala.concurrent.duration._

/**
 * A set of test scenarios to demonstrate Akka Stream backpressure in action.  Metrics are exported
 * to StatsD by Kamon so they can be graphed in Grafana.
 */
object Scenarios {

  def fastPublisherFastSubscriber() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 20 milliseconds, 20000, "fastProducer"))
      val fastSink = builder.add(Sink.actorSubscriber(Props(classOf[actors.DelayingActor], "fastSink")))

      import GraphDSL.Implicits._

      source ~> fastSink

      ClosedShape
    })
    theGraph
  }


  def fastPublisherSlowingSubscriber() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[Unit] =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 30 milliseconds, 20000, "fastProducer"))
      val slowingSink = builder.add(Sink.actorSubscriber(Props(classOf[SlowDownActor], "slowingDownSink", 50l)))

      import GraphDSL.Implicits._

      source ~> slowingSink

      ClosedShape
    })
    theGraph
  }


  def fastPublisherSlowingSubscriberWithDropBuffer() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[Unit] =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 30 milliseconds, 6000, "fastProducer"))
      val slowingSink = builder.add(Sink.actorSubscriber(Props(classOf[SlowDownActor], "slowingDownSink", 50l)))

      // create a buffer, with 100 messages, with an overflow
      // strategy that starts dropping the oldest messages when it is getting
      // too far behind.
      val bufferFlow = Flow[String].buffer(100, OverflowStrategy.dropHead)

      import GraphDSL.Implicits._

      source ~> bufferFlow ~> slowingSink

      ClosedShape
    })
    theGraph
  }


  def fastPublisherSlowingSubscriberWithBackpressureBuffer() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[Unit] =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 30 milliseconds, 6000, "fastProducer"))
      val slowingSink = builder.add(Sink.actorSubscriber(Props(classOf[SlowDownActor], "slowingDownSink", 50l)))

      // create a buffer, with 100 messages, with an overflow
      // strategy that starts dropping the oldest messages when it is getting
      // too far behind.
      val bufferFlow = Flow[String].buffer(100, OverflowStrategy.backpressure)

      import GraphDSL.Implicits._

      source ~> bufferFlow ~> slowingSink

      ClosedShape
    })
    theGraph
  }


  def fastPublisherFastSubscriberAndSlowingSubscriber() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[Unit] =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 30 milliseconds, 9000, "fastProducer"))
      val fastSink = builder.add(Sink.actorSubscriber(Props(classOf[actors.DelayingActor], "fastSink")))
      val slowingSink = builder.add(Sink.actorSubscriber(Props(classOf[SlowDownActor], "slowingDownSink", 50l)))

      // create the broadcast component
      val broadcast = builder.add(Broadcast[String](2))

      import GraphDSL.Implicits._

      source ~> broadcast.in
      broadcast.out(0) ~> fastSink
      broadcast.out(1) ~> slowingSink

      ClosedShape
    })
    theGraph
  }


  def fastPublisherFastSubscriberAndSlowingSubscriberWithDroppingBuffer() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[Unit] =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 30 milliseconds, 9000, "fastProducer"))
      val fastSink = builder.add(Sink.actorSubscriber(Props(classOf[actors.DelayingActor], "fastSink")))
      val slowingSink = builder.add(Sink.actorSubscriber(Props(classOf[SlowDownActor], "slowingDownSink", 50l)))
      val broadcast = builder.add(Broadcast[String](2))

      val bufferFlow = Flow[String].buffer(300, OverflowStrategy.dropHead)

      import GraphDSL.Implicits._

      source ~> broadcast.in
      broadcast.out(0) ~> fastSink
      broadcast.out(1) ~> bufferFlow ~> slowingSink

      ClosedShape
    })
    theGraph
  }


  def fastPublisherFastSubscriberAndSlowingSubscriberWithBackpressureBuffer() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[Unit] =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 30 milliseconds, 9000, "fastProducer"))
      val fastSink = builder.add(Sink.actorSubscriber(Props(classOf[actors.DelayingActor], "fastSink")))
      val slowingSink = builder.add(Sink.actorSubscriber(Props(classOf[SlowDownActor], "slowingDownSink", 50l)))
      val broadcast = builder.add(Broadcast[String](2))

      val bufferFlow = Flow[String].buffer(3500, OverflowStrategy.backpressure)

      import GraphDSL.Implicits._

      source ~> broadcast.in
      broadcast.out(0) ~> fastSink
      broadcast.out(1) ~> bufferFlow ~> slowingSink

      ClosedShape
    })
    theGraph
  }

  def fastPublisherFastSubscriberAndSlowingSubscriberWithBalancer() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[Unit] =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 30 milliseconds, 9000, "fastProducer"))
      val fastSink = builder.add(Sink.actorSubscriber(Props(classOf[actors.DelayingActor], "fastSink")))
      val slowingSink = builder.add(Sink.actorSubscriber(Props(classOf[SlowDownActor], "slowingDownSink", 50l)))
      val balancer = builder.add(Balance[String](2))

      import GraphDSL.Implicits._

      source ~> balancer.in
      balancer.out(0) ~> fastSink
      balancer.out(1) ~> slowingSink

      ClosedShape
    })
    theGraph
  }
}
