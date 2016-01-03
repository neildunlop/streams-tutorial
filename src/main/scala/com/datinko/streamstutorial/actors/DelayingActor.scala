package com.datinko.streamstutorial.actors

import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnNext}
import akka.stream.actor.{ActorSubscriber, OneByOneRequestStrategy, RequestStrategy}
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon

/**
 * An actor that introduces a fixed delay when processing each message.
 */
//Actor Subscriber trait extension is need so that this actor can be used as part of a stream
class DelayingActor(name: String, delay: Long) extends ActorSubscriber with LazyLogging {
  override protected def requestStrategy: RequestStrategy = OneByOneRequestStrategy

  val actorName = name
  val consumeCounter = Kamon.metrics.counter("delayingactor-consumed-counter")

  def this(name: String) {
    this(name, 0)
  }

  override def receive: Receive = {
    case OnNext(msg: String) =>
      Thread.sleep(delay)
      logger.debug(s"Message in delaying actor sink ${self.path} '$actorName': $msg")
      consumeCounter.increment(1)
    case OnComplete =>
      logger.debug(s"Completed Messgae received in ${self.path} '$actorName'")
    case msg =>
      logger.debug(s"Unknown message $msg in $actorName: ")
  }
}