package com.datinko.streamstutorial

import akka.actor._
import akka.stream._

import kamon.Kamon

import scala.io.StdIn


object Start extends App {

  Kamon.start()

  implicit val system = ActorSystem("streamstutorial")

  implicit val materializer = ActorMaterializer()

  println("")
  println("")
  println("***************************")
  println("Choose a Scenario to run:")
  println("***************************")
  println("A:  Simple Stream to Console")
  println("B:  Throttled Producer to Console")
  println("C:  Fast Source, Fast Sink")
  println("D:  Fast Source, Slowing Sink")
  println("E:  Fast Source, Slowing Sink with Dropping Buffer")
  println("F:  Fast Source, Slowing Sink with Back Pressure Buffer")
  println("G:  Fast Source with Fast Sink and Slowing Sink")
  println("H:  Fast Source with Fast Sink and Slowing Sink with Dropping Buffer")
  println("I:  Fast Source with Fast Sink and Slowing Sink with Back Pressure Buffer")
  println("J:  Fast Source with Fast Sink and Slowing Sink with Balancer")
  println("")


  StdIn.readChar().toUpper match {
    case 'A' => SimpleStreams.printSimpleMessagesToConsole(materializer)
    case 'B' => SimpleStreams.throttledProducerToConsole.run
    case 'C' => Scenarios.fastPublisherFastSubscriber.run
    case 'D' => Scenarios.fastPublisherSlowingSubscriber.run
    case 'E' => Scenarios.fastPublisherSlowingSubscriberWithDropBuffer.run
    case 'F' => Scenarios.fastPublisherSlowingSubscriberWithBackpressureBuffer.run
    case 'G' => Scenarios.fastPublisherFastSubscriberAndSlowingSubscriber.run
    case 'H' => Scenarios.fastPublisherFastSubscriberAndSlowingSubscriberWithDroppingBuffer.run
    case 'I' => Scenarios.fastPublisherFastSubscriberAndSlowingSubscriberWithBackpressureBuffer.run
    case 'J' => Scenarios.fastPublisherFastSubscriberAndSlowingSubscriberWithBalancer.run

  }
}

