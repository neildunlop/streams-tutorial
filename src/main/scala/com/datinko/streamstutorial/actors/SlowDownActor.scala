package com.datinko.streamstutorial.actors

import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{ActorSubscriber, OneByOneRequestStrategy, RequestStrategy}
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon

/**
 * An actor that progressively slows as it processes messages.
 */
class SlowDownActor(name: String, delayPerMsg: Long, initialDelay: Long) extends ActorSubscriber with LazyLogging {
  override protected def requestStrategy: RequestStrategy = OneByOneRequestStrategy

  // setup actorname to provided name for better tracing of stats
  val actorName = name
  val consumeCounter = Kamon.metrics.counter("slowdownactor-consumed-counter")

  // default delay is 0
  var delay = 0l

  def this(name: String) {
    this(name, 0, 0)
  }

  def this(name: String, delayPerMsg: Long) {
    this(name, delayPerMsg, 0)
  }

  override def receive: Receive = {

    case OnNext(msg: String) =>
      delay += delayPerMsg
      Thread.sleep(initialDelay + (delay / 1000), delay % 1000 toInt)
      logger.debug(s"Message in slowdown actor sink ${self.path} '$actorName': $msg")
      consumeCounter.increment(1)
    case msg =>
      logger.debug(s"Unknown message $msg in $actorName: ")
  }
}

