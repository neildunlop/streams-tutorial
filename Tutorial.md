Getting Started with Scala, Akka, Reactive Streams and Grafana
===

With Reactive programming becoming more popular I decided to build a sample application to demonstrate some of the 
core concepts in action.  As I have recently started experimenting with Scala and the Akka framework, which has 
experimental support for Reactive Streams, I decided to try and build my sample application using Scala and 
Akka Reactive Streams.

While there are several good articles discussing what Reactive Programming is all about and even more tutorials on
building a simple reactive streams application using Scala, Akka and the Reactive Streams Framework, there aren't
many that cover all the necessary details from end to end.  I aim to change that.

In an interesting turn of events, as I was building this application Typesafe released akka-streams V2.0 
and V2.01 days later.  This new release of akka-streams introduced some significant changes to the framework and a 
nnumber of changes class names and the methods used to build reactive streams.

The Basics - Overview
===

Let's start with something simple.  We want to create an Akka Stream that takes a series of text messages and outputs
them to the console.  This is nothing too special but gets the basics established.  We'll evolve this quickly to 
something more useful and better designed.


Essential Dependencies
===

We'll be using SBT as our build tool so we need to add a few dependencies to get things up and running.  The version 
numbers for akka-streams has recently bumped up to v2.0 which has rendered many of the existing tutorials out of date
since there have been some significant class renaming.

Our starting ```build.sbt``` has the following content:

```scala
name := "streamstutorial"
    
version := "1.0"
    
scalaVersion := "2.11.7"
    
val akka            = "2.3.12"  
val akkaStream      = "2.0"
    
libraryDependencies ++= Seq (
 
    // -- Akka --
    ,"com.typesafe.akka" %% "akka-testkit" % akka % "test"
    ,"com.typesafe.akka" %% "akka-actor" % akka
    ,"com.typesafe.akka" %% "akka-slf4j" % akka
     
    // -- Akka Streams --
    ,"com.typesafe.akka" % "akka-stream-experimental_2.11" % akkaStream
   
    // -- Config --
    ,"com.typesafe" % "config" % "1.2.1"
)
```

The Basics - A Really Simple Stream
===

Let's take a look at some simple code that will do what we want and then we will examine it in detail, discussing some of the theory behind the implementation.

*SimpleStreams.scala*
```scala
package com.datinko.streamstutorial

import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

/**
 * An example of the simplest possible akka stream.
 */
object SimpleStreams {

def printSimpleMessagesToConsole(implicit materializer: ActorMaterializer) = {

   val simpleMessages = "Message 1" :: "Message 2" :: "Message 3" :: "Message 4" :: "Message 5" :: Nil

   Source(simpleMessages)
      .map(println(_))
      .to(Sink.ignore)
      .run()
   }
}
```   

Every stream has two essential components, without which it will not run:

![](http://i.imgur.com/PF84anG.png)

- A ```Source``` is something that has exactly one output.  It can produce a sequence of many items but they all follow one another, flowing from the Source.  We can use many things as a Source.  In our example above we use a simple List of strings.
- A ```Sink``` is something that has exactly one input and it is the final step in any stream.  In the example above we actually create a dummy Sink with the ```Sink.ignore``` call.  This ensures that the stream can run but we do not need to define the destination any further.

In the above example we use the ```.map(println(_))``` call to process each message that is emitted from the Sink and print it to the console.

Note that we have added the ```.run()``` call to the end of the stream definition so that the stream is automatically run when the ```printSimpleMessagesToConsole``` function is called.

This call to ```.run()``` is the reason that we must pass in the ```implicit materializer: ActorMaterializer``` as a parameter to the function.  The materializer is used to take the stream definition and turn it into a series of akka actors that can actually execute the stream.  If we did not make the call to ```.run()``` within this function the the materializer would not be needed.  We'll illustrate this in later examples.    

   
The Basics - Running our Simple Stream
===

To fire this stream up and make it do something, we create a simple application and make a call to our ```SimpleStreams.printSimpleMessagesToConsole``` method.

*Start.scala*

```scala
package com.datinko.streamstutorial
import akka.stream.{ActorMaterializer}


/**
  * A really simple application to run our simple streams from.
  */
object Start extends App {

  implicit val system = ActorSystem("streamstutorial")
  implicit val materializer = ActorMaterializer()

  SimpleStreams.printSimpleMessagesToConsole(materializer)

}
```

There are two supporting elements that we need in our application to get our stream working:

- An ```ActorSystem``` is an Akka actor system.  As Akka Streams are, somewhat obviously, based on akka, we need to have an actor system up and running for the streams to work.  We have done the simplest possible to create an instance of an akka system with a name of our choosing.  Making this ```implicit``` means that it can be picked up an used in other code whenever it is needed.
- The ```Materializer``` as discussed above provides the processing power to turn a stream definition into something that can actually be executed with a series of akka actors.  Again, we have made this ```implicit``` so that it can be picked up and used in other code whenever it is needed.  The main user of this materializer is our ```SimpleStreams``` object.

When you execute this application, either from within your IDE or from the command line using ```sbt run``` you should see output like:

```
Message 1
Message 2
Message 3
Message 4
Message 5
```

Fairly uninspiring stuff but we now have a working system with all the plumbing we need to get streams up and running.  Now we can move on and make some more useful and better designed streams.

Stream Graphs - Overview
===

In our simple early example of a stream, we did everything in one function.  This is rarely how we would do things in a production environment.  Often we would want to split up the parts of our stream into components that we can reuse.  Once we have these components we will probably want to wire those components together in a range of ways to get the stream we want.  Stream Graphs give us a way to do this.

Splitting up our Sources, Flows and Sinks
===

Although we said that streams has two *essential* components for them to work, we failed to mention that there is one other type of component that makes it easier to decompose our streams into reusable chunks of functionality.

![](http://i.imgur.com/nkvRPX3.png)

- A ```Flow``` is something that has exactly one input and exactly one output.  A flow is often used to modify the messages that pass through the stream.  A stream can contain many flows and there are a range of common operations that can be used within a flow.  You can also implement your own operations within a flow.

Creating a Reusable Source
===

One of the main objectives we would like to achieve in this tutorial is to create a number of streams that we can use to experiment with *backpressure* and monitor to observe its effects.  To do that we will need a ```Source``` object that we can reuse in many different scenarios with a range of different message production rates.  


Say Hello to the ThrottledProducer
===

The first step to building such a system is to create a ```Source``` that produces the data we want at the rate we want.  We then want to be able to wire that source into a range of other components that will consume the messages that are produced.  We will call this source the ```ThrottledProducer```.  Bear with, there may be a lot of typing before we see results.

The ThrottledProducer - Overview
===
In order to get a ```Source``` produce message at a rate that we can control, we need to create a Stream Graph and wrap it up as a ```Source``` that can be reused in another Stream Graph.  The ```ThrottledProducer``` has the following components within it.

![](http://i.imgur.com/tUey1NP.png)

- The ```Ticker``` is a built is akka-stream component that produces simple messages at a defined rate.  This is almost perfect for our needs, except that we want to control the content of the messages that our Source produces.
- The ```NumberGenerator``` is simple a Scala ```Range``` that contains the number of messages we want.  In our case, the content of each message is an actual number.  Message 1 contains the number 1, message 2 contains the number 2 and so on.
- The ```Zip``` operation is a build in akka-stream component and key to controlling the rate at which we produce messages inside our source.  The ```Zip``` operation waits until it has a value at both inputs before it produces an output.  The output takes the form of a tuple containing both inputs.  In our example our ```NumberGenerator``` produces all the messages we want to output almost immediately.  The Ticker produces ```Tick``` messages at the controlled rate we specify.  The messages from the ```NumberGenerator``` wait at the input of the ```Zip``` until a ```Tick``` arrives at its other input.  When this happens the ```Zip``` outputs a Tuple of form ```[Tick, String]``` and sends it on to its output.
- The ```ExtractFlow``` is a simple flow operation that extracts the ```String``` element of the ```Zip``` output and passes it on.  It discards the ```Tick``` as it is not needed.  We only needed the ```Tick``` to control the rate at which messages were produced.

The ThrottledProducer - Code
===
Enough theory, lets take a look at the code.

*ThrottledProducer.scala*

```scala
package com.datinko.streamstutorial

import akka.stream.{SourceShape}
import akka.stream.scaladsl.{Flow, Zip, GraphDSL, Source}
import com.datinko.streamstutorial.Tick

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

      //define a zip operation that expects a tuple with a Tick and a Message in it..
      //(Note that the operations must be added to the builder before they can be used)
      val zip = builder.add(Zip[Tick.type, String])

      //create a flow to extract the second element in the tuple (our message - we dont need the tick part after this stage)
      val messageExtractorFlow = builder.add(Flow[(Tick.type, String)].map(_._2))

      //import this so we can use the ~> syntax
      import GraphDSL.Implicits._

      //define the inputs for the zip function - it wont fire until something arrives at both inputs, so we are essentially
      //throttling the output of this steam
      ticker ~> zip.in0
      rangeMessageSource ~> zip.in1

      //send the output of our zip operation to a processing messageExtractorFlow that just allows us to take the second element of each Tuple, in our case
      //this is the string message, we dont care about the Tick, it was just for timing and we can throw it away.
      //route that to the 'out' Sink.
      zip.out ~> messageExtractorFlow

      SourceShape(messageExtractorFlow.out)
    })
    throttledStream
  }
}
```

The whole purpose of this function is to create a ```Source``` stream that we have called ```throttledStream``` that can included as part of another Stream Graph.

We start by defining our two Source objects, the ```ticker``` and the collection of number messages.  The ```Source``` object has handy constructors that allow us to pass in many different types of collections that are automatically turned into valid sources.  We'll use these two sources a little later to feed into our ```Zip``` operation.

The definition of the Stream Graph is started with:

```val throttledStream = Source.fromGraph(.......)```

We signal our intention to create a Graph from scratch using:

```GraphDSL.create() { implicit builder =>.......}```

All GraphDSL operations need reference to a ```builder``` object which acts as a context for the Stream Graph.  Sources, sinks, flows and opearations are registered with the builder.  Once that is done they can be wired together into a stream definition. 

The next steps are to create the operations we want within our graph.  First we build define the ```Zip``` operation that expects to receive inputs of type ```Tick``` and ```String``` and therefore produce a Tuple with the same type of elements.

```val zip = builder.add(Zip[Tick.type, String])```

The next operation to add to the builder is the message extractor flow which ensures that we only take the second element in each tuple and discard the ```Tick``` element that we no longer need.  (The map ```Flow``` defines the type of objects it expects to receive while the ```map``` operation simply tells the flow to extract and pass on the second element of each message it receives).

```val messageExtractorFlow = builder.add(Flow[(Tick.type, String)].map(_._2))```

ThrottledProducer - Code - Building the Graph
===
Once we have defined all the sources, flows and operations that will be used in the graph, we need to wire those elements together to actually form the graph.

To make this easier the GraphDSL provides a number of implicits we can use.  To access these we import them using:

```import GraphDSL.Implicits._```

The most obvious benefit of this is that it allows us to use the ```~>``` operator to wire components together in the graph, like so:

```scala
ticker ~> zip.in0

rangeMessageSource ~> zip.in1

zip.out ~> messageExtractorFlow
```

(Note that this illustrates that the ```Zip``` has a number inputs and a single output).

The final element inside the Graph definition is to indicate what kind of Graph component we are creating (Source, Sink or Flow).  

```
SourceShape(messageExtractorFlow.out)
```

The ```SourceShape``` call indicates that this is a source component and we must tell the Graph which of its internal operations supplies the messages that will be exposed to consumers of this source.

(Note that there are ```SinkShape``` and ```FlowShape``` elements to support making Sinks and Flows).

Once we have build the graph for the ```throttledSource``` we return a reference to the function so that it can be used when building another Graph.

Using the ThrottledProducer in a Graph
===
Now that we have built our ```ThrottledProducer``` we want to use it for something.  Believe it or not, the cleanest way of showing the ```ThrottledProducer``` in action is to use it in another simple Graph and then run that graph.

If we add a new function to the ```SimpleStreams``` object we created earlier we get the following.  This uses a lot of the concepts we've just covered in the ```ThrottledProducer``` to build a stream graph, expect that this builds a runnable (also known as a 'closed') graph that is complete with a ```Source``` and a ```Sink```.

*SimpleStreams.scala*
```scala
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
```

The differences to note about this function is that it creates a ```RunnableGraph``` which indicates it should have a ```Source``` and a ```Sink```.  This is matched by the use of ```ClosedShape``` at the end of the function rather than than the ```SourceShape``` we used in the ```ThrottledProducer```.

As we did previously, we define the components to be used in the graph.  In this case we have our ```ThrottledProducer``` that is set to produce a message every 20 milliseconds after an initial delay of 1 second and it will produce 20000 messages.

To make it a little easier to see if anything is happening we have created a simple flow that prints every message it sees.

This is all capped off by the ```Sink.ignore``` that we have seen earlier.  This simply gives the graph the sink it must have to be considered valid but this sink simply discards all messages it receives.

To run this graph we simply add the following to our ```Start.scala``` object.

```scala
SimpleStreams.throttledProducerToConsole.run()
```

When you execute this application, either from within your IDE or from the command line using ```sbt run``` you should see output like:

```
Message 1
Message 2
Message 3
Message 4
Message 5
...
Message 999
```

Still not the most impressive of demos but now we have a very neat and reuseable way of defining the major parts of any stream graph.  
 Next we'll move on to building some Sinks that can help us experiment with reactive streams.
  

Creating a Reusable Sink
===
Now that we have a nice reusable, configurable ```Source``` object we could do with having a similarly reusable and configurable
  ```Sink``` object that helps us experiment with reactive streams.  While we are doing that we'll wire in some simple logging
  just so we can we whats happening within our system.

Adding Dependencies for Logging
===
As mentioned earlier, we would like to be able to make use of logging within our system to be able to see what is really happening.  This will
be some pretty simple logging that we will expand upon in the future, but it will do us for now.  To be able to use logging, we need to add
a couple of dependencies and a configuration file to our application.

Add the following dependencies to your build.sbt file.

*build.sbt*
```scala
/* dependencies */
libraryDependencies ++= Seq (

  ...
  
  // -- Logging --
  ,"ch.qos.logback" % "logback-classic" % "1.1.2"
  ,"com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"

  ...

)
```

Because we are using ```Logback``` as our logging provider, we also need to add some configuration settings to our
application to tell logback where to output its logs and what format to write log entries in.

Add a file to ```/scr/main/resources/logback.xml``` with the following contents:

*logback.xml*
```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <!-- path to your log file, where you want to store logs -->
        <file>/Users/yourusername/test.log</file>
        <append>false</append>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="debug">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

We wont dwell on this configuration file, its a pretty standard logback configuration file that does everything we need it to do for now.


Say Hello to the DelayingActor
===
So far in this tutorial we have made very simple ```Sink``` objects that dont do very much apart from discard the messages
they receieve.  Akka actors are a really good way of making a chunk of configurable processing logic that can be reused 
in a range of stream graphs.  

DelayingActor - Overview
===
To make a standard Akka Actor able to work within a reactive stream and be able to understand *backpressure* we need to 
a few minor modifications to a standard actor.  The idea of this ```DelayingActor``` is to add a fake processing
delay to each message it receives.  We'll also take the opportunity to introduce some logging output so that we can
log the processing of each message and see what is happening inside our running system.

http://doc.akka.io/api/akka-stream-and-http-experimental/2.0/index.html#akka.stream.actor.ActorSubscriber

The DelayingActor - Code
===
The code for the ```DelayingActor``` is as follows:

*DelayingActor.scala*
```scala
package com.datinko.streamstutorial.actors

import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnNext}
import akka.stream.actor.{OneByOneRequestStrategy, RequestStrategy, ActorSubscriber}
import com.typesafe.scalalogging.LazyLogging

/**
 * An actor that introduces a fixed delay when processing each message.
 */
//Actor Subscriber trait extension is need so that this actor can be used as part of a stream
class DelayingActor(name: String, delay: Long) extends ActorSubscriber with LazyLogging {
  override protected def requestStrategy: RequestStrategy = OneByOneRequestStrategy

  val actorName = name

  def this(name: String) {
    this(name, 0)
  }

  override def receive: Receive = {
    case OnNext(msg: String) =>
      Thread.sleep(delay)
      logger.debug(s"Message in delaying actor sink ${self.path} '$actorName': $msg")
    case OnComplete =>
      logger.debug(s"Completed Messgae received in ${self.path} '$actorName'")      
    case msg =>
      logger.debug(s"Unknown message $msg in $actorName: ")
  }
}
```

Its worth discussing the minor changes we have had to make to ensure this actor can be used inside a reactive stream.

- The ```LazyLogging``` trait gives us access to the logback ```logger``` inside our actor.  The lazy logging trait only creates the logger when it is first called.  This saves us some resources by ensuring we dont spin up a logger unless we use one, but the downside is slightly worse performance.  Note that this is not needed for reactive streams, its just for us to be able to see what is going on inside the actor in a very simple way.
- To make the actor part of the reactive stream we need to extend ```ActorSubscriber```.  This gives us full control of stream backpressure and means that the actor will recieve ```ActorSubscriberMessage.OnNext```, ```ActorSubscriberMessage.OnComplete``` and```ActorSubscriberMessage.OnError``` messages as well as any other non-stream messages just like any other actor.  The one we are most interested in is the ```OnNext``` message which is used to pass the next stream message to this actor.
- When defining the actor as an ```ActorSubscriber``` we must also define a ```RequestStrategy``` so the actor is able to signal how it wants to control backpressure in the stream, or, in other words, what does the actor do when it wants more or less messages to be sent to it. We have chosen the ```OneByOneRequestStrategy``` so that everytime the actor has 0 messages to process it asks for one more.

For more information on the ```ActorSubscriber``` check out the [http://doc.akka.io/api/akka-stream-and-http-experimental/2.0/index.html#akka.stream.actor.ActorSubscriber](http://doc.akka.io/api/akka-stream-and-http-experimental/2.0/index.html#akka.stream.actor.ActorSubscriber "documentation")

Scenarios - Putting our ThrottledProducer and DelayingActor Together
===
Now we have some reuseable, configurable components it would be useful to put them together in a couple of scenarios that allow us to explore *backpressure* in action.  To achieve this we'll make a simple ```Scenarios``` object that has a function to setup and run each configuration we are interested in.

*Scenarios.scala*
```scala
package com.datinko.streamstutorial

import akka.actor.Props
import akka.stream.{ClosedShape}
import akka.stream.scaladsl.{Sink, GraphDSL, RunnableGraph}
import com.datinko.streamstutorial.actors.DelayingActor
import scala.concurrent.duration._
/**
 * A set of test scenarios to demonstrate Akka Stream back pressure in action.  Metrics are exported
 * to StatsD by Kamon so they can be graphed in Grafana.
 */
object Scenarios {

  def fastPublisherFastSubscriber() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 20 milliseconds, 20000, "fastProducer"))
      val fastSink = builder.add(Sink.actorSubscriber(Props(classOf[DelayingActor], "fastSink")))

      import GraphDSL.Implicits._

      source ~> fastSink

      ClosedShape
    })
    theGraph
  }
}
```  

This function is very much like the ```SimpleStreams.printSimpleMessagesToConsole``` function we defined earlier.  We create a ```RunnableGraph``` that contains both a source and a sink.  The only area of interest is how we create the ```Sink``` so that it uses the ```DelayingActor``` we just defined.

The ```Sink.actorSubscriber(...)``` provides an easy way for us to use an ```ActorSubscriber``` as a sink in a stream graph.

To execute this from our ```Start.scala``` object (with its implicit actor system and implicit materializers) we use:

Scenarios.fastPublisherFastSubscriber().run()
   
The output will be something similar to:

```
14:29:30.344 [Bifrost-akka.actor.default-dispatcher-6] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': Message 1
14:29:30.353 [Bifrost-akka.actor.default-dispatcher-10] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': Message 2
14:29:30.383 [Bifrost-akka.actor.default-dispatcher-6] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': Message 3
14:29:30.393 [Bifrost-akka.actor.default-dispatcher-6] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': Message 4
14:29:30.423 [Bifrost-akka.actor.default-dispatcher-10] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': Message 5
14:29:30.433 [Bifrost-akka.actor.default-dispatcher-10] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': Message 6
14:29:30.463 [Bifrost-akka.actor.default-dispatcher-10] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': Message 7
14:29:30.473 [Bifrost-akka.actor.default-dispatcher-10] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': Message 8
```

From the debug messages we have output to the log we can see that the materializer creates an actor to process the stream graph 
which has its own unique path in the akka actor system.  As hoped, this gives us some basic information that the Source and the Sink
are publishing and receiving messages as expected, however, it would be nice to be able to see whats happening to the message
flow in a more visual way.


Kamon - Visualising what's happening
===

In their own words, 

> "Kamon is a reactive-friendly toolkit for monitoring applications that run on top of the Java 
> Virtual Machine. We are specially enthusiastic to applications built with the Typesafe Reactive Platform (using Scala, 
> Akka, Spray and/or Play!) but as long as you are on the JVM, Kamon can help get the monitoring information you need 
> from your application."

this sounds like a great way of monitoring our application and being able to see what is 
really happening when we run our stream graphs, but to be able visualise the information that Kamon exposes we'll need to add a few more components to the mix.

Kamon is able to gather metrics about performance and health of our application and export this information regularly to a backend that is able to store and aggregate this information.  Other tools are then able to render automatically updating charts based on this information.  A very common collection of components to do this work are Kamon, Graphite, ElasticSearch and Grafana.

![](http://i.imgur.com/i6jbqP4.png)
 
- **Kamon** is a library that uses AspectJ to hook into the method calls made by your ActorSystem and record events of different types.  There are a range of actor system metrics that are gathered automatically.  You can also add your own metrics to be recorded as the application runs.  The default configuration works pretty well but it takes a little digging to get everything up and running, mostly becuase the library is evolving quickly.
- **StatsD** - **Graphite** is a network daemon that runs on the Node.js platform and listens for information like counters and timers sent over UDP.  StatsD then sends its information to be aggregated by a range of pluggable backend services.  In our case, we will use Graphite.
- **Grafana** is a frontend dashboard that provides a very attractive and flexible way of presenting the information aggregated by Graphite.  All of this updates in realtime.


Because getting all these components up and running can be challenging, we are going to use a pre-configured Docker container where all the hard work has already been done.  This builds on the [Kamon Docker Image](http://kamon.io/teamblog/2014/04/27/get-started-quicker-with-our-docker-image/ "Kamon Docker Image") that the Kamon team have already created.  An even simpler implementation that we will use is based on a tutorial by Nepomuk Seiler which you can see [here](http://mukis.de/pages/monitoring-akka-with-kamon/ "here") is you want more details.


Adding Dependencies for Kamon
===
To be able to use kamon, we need to add a couple of dependencies, some AspectJ configuration and a SBT plugin and a configuration file to our application.

Add the following dependencies and AspectJ configuration to your ```build.sbt``` file.


*build.sbt*
```scala

scalaVersion := "2.11.7"

... existing version numbers ...
val kamonVersion    = "0.5.2"


/* dependencies */
libraryDependencies ++= Seq (

  ... existing dependencies ...
  
  // -- kamon monitoring dependencies --
  ,"io.kamon" % "kamon-core_2.11" % kamonVersion
  ,"io.kamon" %% "kamon-core" % kamonVersion
  ,"io.kamon" %% "kamon-scala" % kamonVersion
  ,"io.kamon" %% "kamon-akka" % kamonVersion
  ,"io.kamon" %% "kamon-statsd" % kamonVersion
  ,"io.kamon" %% "kamon-log-reporter" % kamonVersion
  ,"io.kamon" %% "kamon-system-metrics" % kamonVersion
  ,"org.aspectj" % "aspectjweaver" % "1.8.5"

  ... existing dependencies ...

)

//configure aspectJ plugin to enable Kamon monitoring
aspectjSettings
javaOptions <++= AspectjKeys.weaverOptions in Aspectj
fork in run := true
```

Kamon depends on AspectJ to inject its monitoring code in all the right places in our akka actor system.  To make sure this can happen we need to add a build plug-in to our ```plugins.sbt``` file.

Ensure that your ```/project/plugins.sbt``` looks like this:

```scala
logLevel := Level.Warn

// The Typesafe repository
resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" % "0.9.4")
```


Configuring Kamon
===
Once we have all the dependencies and SBT plugins configured we need to add some more configuration to our application to tell Kamon what to monitor and where to send its data.

Add the following content to ```/src/main/resources/application.conf```

*application.conf*
```
akka {
  loglevel = INFO

  extensions = ["kamon.akka.Akka", "kamon.statsd.StatsD"]
}


# Kamon Metrics
# ~~~~~~~~~~~~~~

kamon {

  metric {

    # Time interval for collecting all metrics and send the snapshots to all subscribed actors.
    tick-interval = 1 seconds

    # Disables a big error message that will be typically logged if your application wasn't started
    # with the -javaagent:/path-to-aspectj-weaver.jar option. If you are only using KamonStandalone
    # it might be ok for you to turn this error off.
    disable-aspectj-weaver-missing-error = false

    # Specify if entities that do not match any include/exclude filter should be tracked.
    track-unmatched-entities = yes

    filters {
      akka-actor {
        includes = ["*/user/*"]
        excludes = [ "*/system/**", "*/user/IO-**", "*kamon*" ]
      }

      akka-router {
        includes = ["*/user/*"]
        excludes = []
      }

      akka-dispatcher {
        includes = ["*/user/*"]
        excludes = []
      }

      trace {
        includes = [ "**" ]
        excludes = [ ]
      }
    }
  }

  # Controls whether the AspectJ Weaver missing warning should be displayed if any Kamon module requiring AspectJ is
  # found in the classpath but the application is started without the AspectJ Weaver.
  show-aspectj-missing-warning = yes

  statsd {

    # Hostname and port in which your StatsD is running. Remember that StatsD packets are sent using UDP and
    # setting unreachable hosts and/or not open ports wont be warned by the Kamon, your data wont go anywhere.  If you're running Docker on Linux this will probably be 127.0.0.1, if you're running Windows or OSX, like me, it will probably be 192.168.99.100
    hostname = "192.168.99.100"
    port = 8125

    # Interval between metrics data flushes to StatsD. It's value must be equal or greater than the
    # kamon.metric.tick-interval setting.
    flush-interval = 1 seconds

    # Max packet size for UDP metrics data sent to StatsD.
    max-packet-size = 1024 bytes

    # Subscription patterns used to select which metrics will be pushed to StatsD. Note that first, metrics
    # collection for your desired entities must be activated under the kamon.metrics.filters settings.
    subscriptions {
      histogram       = [ "**" ]
      min-max-counter = [ "**" ]
      gauge           = [ "**" ]
      counter         = [ "**" ]
      trace           = [ "**" ]
      trace-segment   = [ "**" ]
      akka-actor      = [ "**" ]
      akka-dispatcher = [ "**" ]
      akka-router     = [ "**" ]
      system-metric   = [ "**" ]
      http-server     = [ "**" ]
    }

    # FQCN of the implementation of `kamon.statsd.MetricKeyGenerator` to be instantiated and used for assigning
    # metric names. The implementation must have a single parameter constructor accepting a `com.typesafe.config.Config`.
    metric-key-generator = kamon.statsd.SimpleMetricKeyGenerator

    simple-metric-key-generator {

      # Application prefix for all metrics pushed to StatsD. The default namespacing scheme for metrics follows
      # this pattern:
      #    application.host.entity.entity-name.metric-name
      application = "streamstutorial"

      # Includes the name of the hostname in the generated metric. When set to false, the scheme for the metrics
      # will look as follows:
      #    application.entity.entity-name.metric-name
      include-hostname = true

      # Allow users to override the name of the hostname reported by kamon. When changed, the scheme for the metrics
      # will have the following pattern:
      #   application.hostname-override-value.entity.entity-name.metric-name
      hostname-override = none

      # When the sections that make up the metric names have special characters like dots (very common in dispatcher
      # names) or forward slashes (all actor metrics) we need to sanitize those values before sending them to StatsD
      # with one of the following strategies:
      #   - normalize: changes ': ' to '-' and ' ', '/' and '.' to '_'.
      #   - percent-encode: percent encode the section on the metric name. Please note that StatsD doesn't support
      #     percent encoded metric names, this option is only useful if using our docker image which has a patched
      #     version of StatsD or if you are running your own, customized version of StatsD that supports this.
      metric-name-normalization-strategy = normalize
    }
  }

  # modules can be disabled at startup using yes/no arguments.
  modules {
    kamon-log-reporter.auto-start = no
    kamon-system-metrics.auto-start = no
    #seems like you need to leave this set to 'no' or kamon double reports all statsd metrics..
    kamon-statsd.auto-start = no
    kamon-akka.auto-start = yes
  }
}
```

This is a fairly standard setup that was lifted straight from the Kamon website.  The only points to note are the statsD hostname, which when you're running Docker on Linux is likely to be 127.0.0.1.  If you are running Docker Toolbox on Windows or Mac OSX then its likely to be 192.168.99.100.

Getting the StatsD/Graphite/Grafana Docker Image Running
===

I'm assuming you have some understanding of Docker and its ability to provide lightweight fully configured images of servers and applications.  I wont delve into too much detail here.  To get our chosen Docker image running, start the Docker Quickstart Terminal (if you're on Windows or OSX - if you on Linux you can enter commands directly on the command line).  Once its running, enter the following command:

```docker run -p 80:80 -p 8125:8125/udp -p 8126:8126 -p 8083:8083 -p 8086:8086 -p 8084:8084 --name kamon-grafana-dashboard muuki88/grafana_graphite:latest```

This gets our image running and maps all the ports on the running image to the same ports on our local machine, so as far as local applications are concerned, StatsD, Graphite and Grafana are running locally.

(If you have not run this image before, the image will be pulled from the Docker repository and this may take a little time).

Once the image has been pulled you should be able to open your browser and navigate to:

```http://192.168.99.100/```  (or http://127.0.0.1/ if you're running Linux).  

If everything worked, you should see something similar to this:

![](http://i.imgur.com/KJxWlec.png) 


Updating our Application to Report to Kamon
===
Now we have our Docker container running, we need to make a few minor changes to get our stream graph application to report useful metrics.

Although Kamon is able to expose some standard metrics such as 'Mailbox Size' and 'Time Spent in Mailbox' I wasn't able to interpret them usefully so I decided to do something a little more direct and implement my own metrics.

## Updating the ThrottledProducer ##
The first step is to update our ```ThrottledProducer``` so that it increments a counter every time it produces a message.  Lets take a look at the fully updated class and look at the details afterwards.

*ThrottledProducer.scala*
```scala
import akka.stream.{SourceShape}
import akka.stream.scaladsl.{Flow, Zip, GraphDSL, Source}
import com.datinko.streamstutorial.Tick
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

      //1. create a Kamon counter so we can track number of messages produced
      val createCounter = Kamon.metrics.counter("throttledProducer-create-counter")

      //define a zip operation that expects a tuple with a Tick and a Message in it..
      //(Note that the operations must be added to the builder before they can be used)
      val zip = builder.add(Zip[Tick.type, String])

      //create a flow to extract the second element in the tuple (our message - we dont need the tick part after this stage)
      val messageExtractorFlow = builder.add(Flow[(Tick.type, String)].map(_._2))

      //2. create a flow to log performance information to Kamon and pass on the message object unmolested
      val statsDExporterFlow = builder.add(Flow[(String)].map{message => createCounter.increment(1); message})

      //import this so we can use the ~> syntax
      import GraphDSL.Implicits._

      //define the inputs for the zip function - it wont fire until something arrives at both inputs, so we are essentially
      //throttling the output of this steam
      ticker ~> zip.in0
      rangeMessageSource ~> zip.in1

      //send the output of our zip operation to a processing messageExtractorFlow that just allows us to take the second element of each Tuple, in our case
      //this is the message, we dont care about the Tick, it was just for timing and we can throw it away.
	  //3. Then we route the output of the extractor to a flow that exports data to StatsD
      //then route that to the 'out' Sink as before.
      zip.out ~> messageExtractorFlow ~> statsDExporterFlow

      //4.  make sure we pass the right output to the SourceShape outlet
      SourceShape(statsDExporterFlow.out)
    })
    throttledStream
  }
}
```

The changes we've made are highlighted in bold.  The first and most obvious change is to import the Kamon dependency, ```import kamon.Kamon```.  The other changes in detail are:

1. We create a named Kamon metrics counter.  This needs to have a unique name that helps us and Kamon identify it.  The counter 'recording instrument' as Kamon call it has two simple methods of ```increment``` and ```decrement```.  We'll only be using ```increment```.
2. We create a graph flow that expects to receieve a message of type ```String```, increments the kamon counter and then passes on the message it received, unchanged.  The increment method can take any numeric parameter, or even none but it seems best to be explicit and increment the counter by one for every message it receieves.
3. We bolt our new ```statsDExporterFlow``` onto the end of the existing stream graph so that it actually gets used.
4. We change the ```SourceShape``` call to use the output of the ```statsDExporterFlow``` so that the output of our graph stream is exposed to the outside world as the output of our ```ThrottledProducer```.

## Updating the DelayingActor ##
The next step is to update our ```DelayingActor``` so that it increments a counter every time it processes a message.  Again, lets take a look at the fully updated class and look at the details afterwards.
 
*DelayingActor.scala*
```scala
package com.datinko.asgard.streamstutorial.actors

import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnNext}
import akka.stream.actor.{OneByOneRequestStrategy, RequestStrategy, ActorSubscriber}
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
```

As before, we have some very minor changes to our ```DelayingActor``` to make it report metrics to Kamon.

1. Add the Kamon dependency, ```import kamon.Kamon```.
2. Create a counter with a unique name so we can report the number of messages that have been consumed, ```val consumeCounter = Kamon.metrics.counter("delayingactor-consumed-counter")```  
3. Increment the counter every time we actually consume a message from the stream.  (Note that because each metrics value is timestamped, Graphite can infer a rate from this information), ```consumeCounter.increment(1)```

Running the Application to Report Metrics
===
Because Kamon uses AspectJ to inject its monitoring and reporting code, we must run the application so that the AspectJ SBT plugin gets used.  The simplest way of doing this is to execute ```sbt run``` from the command line.  If you run the application from within your IDE you will see a large reminder that Kamon is not running correcly and no metrics are being recorded, a little something like this:

```
[ERROR] [12/31/2015 17:03:23.195] [main] [ModuleLoader(akka://kamon)] 

  ___                           _      ___   _    _                                 ___  ___ _            _
 / _ \                         | |    |_  | | |  | |                                |  \/  |(_)          (_)
/ /_\ \ ___  _ __    ___   ___ | |_     | | | |  | |  ___   __ _ __   __ ___  _ __  | .  . | _  ___  ___  _  _ __    __ _
|  _  |/ __|| '_ \  / _ \ / __|| __|    | | | |/\| | / _ \ / _` |\ \ / // _ \| '__| | |\/| || |/ __|/ __|| || '_ \  / _` |
| | | |\__ \| |_) ||  __/| (__ | |_ /\__/ / \  /\  /|  __/| (_| | \ V /|  __/| |    | |  | || |\__ \\__ \| || | | || (_| |
\_| |_/|___/| .__/  \___| \___| \__|\____/   \/  \/  \___| \__,_|  \_/  \___||_|    \_|  |_/|_||___/|___/|_||_| |_| \__, |
            | |                                                                                                      __/ |
            |_|                                                                                                     |___/

 It seems like your application was not started with the -javaagent:/path-to-aspectj-weaver.jar option but Kamon detected
 the following modules which require AspectJ to work properly:

      kamon-akka, kamon-scala

 If you need help on setting up the aspectj weaver go to http://kamon.io/introduction/get-started/ for more info. On the
 other hand, if you are sure that you do not need or do not want to use the weaver then you can disable this error message
 by changing the kamon.show-aspectj-missing-warning setting in your configuration file.
```

When everything is running correctly you should see:

```
[info] Running com.datinko.asgard.bifrost.Start
[info] [INFO] [12/31/2015 17:12:21.301] [main] [StatsDExtension(akka://Bifrost)] Starting the Kamon(StatsD) extension
[info] 17:12:24.204 [Bifrost-akka.actor.default-dispatcher-6] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': M
essage 1
[info] 17:12:24.229 [Bifrost-akka.actor.default-dispatcher-5] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': M
essage 2
[info] 17:12:24.239 [Bifrost-akka.actor.default-dispatcher-4] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': M
essage 3
[info] 17:12:24.259 [Bifrost-akka.actor.default-dispatcher-6] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': M
essage 4
[info] 17:12:24.294 [Bifrost-akka.actor.default-dispatcher-3] DEBUG c.d.a.b.t.actors.DelayingActor - Message in delaying actor sink akka://Bifrost/user/StreamSupervisor-0/flow-0-1-actorSubscriberSink 'fastSink': M
essage 5
...
```

Updating Grafana to Show our Metrics
===
Now that our application is sending metrics to Graphite, we can customise our Grafana dashboard to show us some pretty charts of that data.

1. Using your browser, navigate to ```http://192.168.99.100/``` (or 127.0.0.1 on Linux) and this should show you the default Grafana dashboard.
2. A random line chart will be shown at the bottom of the dashboard.  Click on the title of the chart, 'First Graph (click to edit)' and click 'Edit' from the menu that appears.
3. The display will change to show some edit controls.
4. Click on the 'Metrics' tab and from the list shown in the bottom half of the screen click on the 'select metric' box. This will display a list of all sources of data that are available. 
5. Click the 'stats' entry.
6. Click the 'select metric' title that appears in the next box. Click the 'counters' entry.
7. Click the 'select metric' title that appears in the next box. Click the 'streamstutorial' entry.  (If you called your application something else, then you should see it here).
8. Click the 'select metric' title that appears in the next box.  Click the entry that is shown (this is usually the hostname of your machine).
9. Click the 'select metric' title that appears in the next box.  Click the 'counter' entry.
10. Click the 'select metric' title that appears in the next box.  You should now see a list of the counters we defined:  'throttledProducer-create-counter' and 'delayingActor-consumed-counter'.  Choose 'throttledProducer-create-counter' for now.
11. Click the 'select metric' title that appears in the next box.  Click the 'count' entry.

All being well, you should now see a chart showing the rate at which our ThrottledProducer is creating messages and pushing them into the steam.

![](http://i.imgur.com/R6U4y0V.png)

Following a similar process we can add a line to show the rate at which our Sink is consuming messages.

1.  Click the 'Add Query' button at the bottom right of the screen and follow steps 5 to 9 above.
2.  At step 10, choose 'delayingActor-consumed-counter'
3.  Click the 'select metric' title that appears in the next box.  Click the 'count' entry.

This will add a series line for our Sink so that we can see the Source and Sink message rates on the same chart.  With the default settings you will get something looking like this (which doesn't look quite right!):

![](http://i.imgur.com/449mb7w.png)

At first glance it looks like our Sink is actually consuming twice as many messages as our Source.  This is actually just a 'Display Style' setting that we can change:

1. Click the 'Display Styles' tab.
2. Ensure the 'Stack' option in the 'Multiple Series' area is unchecked.

![](http://i.imgur.com/KvocaIm.png)

Both series lines will now sit on top of each other.  To prove that both lines are actually still present, click on the 'Metrics' tab and then click the eye icon that is next to each entry.  This will toggle the visibility of each series.  Despite the annoying habit of Grafana to change the colour of the series when their visibility is toggled we can prove to ourselves that both series are identical. 

In other words, our ThrottledProducer (our Source) is creating and sending messages as fast as the DelayingActor (our Sink) is able to produce them.  Believe it or not, this is back pressure in action.

Well, okay.. but maybe we're not totally convinced?  Let's do some more experiments.

Experiments with Backpressure
===
Finally!  Its been a long haul to get here, but we are finally in a position to be able to conduct some experiments with backpressure.

The inspiration for this set of scenarios to demonstrate backpressure came from an article written Jos Dirksen.  You can check it out [here](http://www.smartjava.org/content/visualizing-back-pressure-and-reactive-streams-akka-streams-statsd-grafana-and-influxdb "here").

## Scenario - Fast Source, Slowing Sink ##

We have already seen above a Fast producing source and a fast consuming sink.  Things get a little more interesting when we have a sink that consumes messages more slowly over time.  This is a classic situation where, normally, the Source would just keep pumping out messages and the Sink would be expected to buffer the messages until it could catch up.  As we all know, this rarely happens.  Usually the buffer fills and the Sink gets overwhelmed.  

To conduct our experiment we need another actor that gets slower at processing messages over time.  Introducing the ```SlowDownActor.scala```.

*SlowDownActor.scala*
```scala
package com.datinko.streamstutorial.actors

import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, OneByOneRequestStrategy, ActorSubscriber}
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
```

Once we have this we can add a new scenario to our existing ```Scenarios.scala``` object.

*Scenarios.scala*
```scala
package com.datinko.asgard.streamstutorial

import akka.actor.Props
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Sink, GraphDSL, RunnableGraph}
import com.datinko.streamstutorial.actors.SlowDownActor
import scala.concurrent.duration._
/**
 * A set of test scenarios to demonstrate Akka Stream back pressure in action.  Metrics are exported
 * to StatsD by Kamon so they can be graphed in Grafana.
 */
object Scenarios {

   ... existing scenarios ...

  def fastPublisherSlowingSubscriber(implicit materializer: ActorMaterializer) = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[Unit] =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 30 milliseconds, 20000, "fastProducer"))
      val slowingSink = builder.add(Sink.actorSubscriber(Props(classOf[SlowDownActor], "slowingDownSink", 10l)))

      import GraphDSL.Implicits._

      source ~> slowingSink

      ClosedShape
    })
    theGraph
  }
}

```

If we execute this scenario in ```Start.scala``` with 

tutorial.Scenarios.fastPublisherSlowingSubscriber().run()

and take a look at Grafana (having updated our Sink metric to be 'slowdownactor-consumed-counter')  we get:

![](http://i.imgur.com/yWobrkt.png)

This chart shows that as the Sink processes messages an an increasingly slow rate, the producer slows its production rate. 
 These is because of the backpressure in the stream coming from the Sink to the Source.  As it struggles to consume
  messages the Sink signals that it needs messages less often, this causes the Source to slow down.  The message 
  production rate is actually controlled by the SlowDownActor (the Sink).

## Scenario - Fast Source, Slowing Sink with Drop Buffer ##

Akka Streams has a built in component, the buffer, that helps us to deal with situations where the Source is going too fast for the Subscriber.  This is useful in situations where the Source isn't reactive and doesn't respond to backpressure messages.

To illustrate the buffer in action, add the following scenario to ```Scenarios.scala```:

*Scenario.scala*
```scala
def fastPublisherSlowingSubscriberWithDropBuffer() = {

    val theGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[Unit] =>

      val source = builder.add(ThrottledProducer.produceThrottled(1 second, 30 milliseconds, 20000, "fastProducer"))
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
```   

Running this scenario results in the follow chart in Grafana:

![](http://i.imgur.com/K4bsIA2.png)

From this chart we can see that the Source essentially ignores the Sink as all of its messages are sent to the buffer.  Because of the strategy used in the buffer it will never signal any backpressure, it will simply drop messages.

It is interesting to note that once the Source has produced all 6000 messages it was configured to send, the Sink keeps running to empty the buffer of the remaining messages.  Bear in mind that the buffer is dropping messages when it contains more than 100 messages.

This in not exactly an ideal arrangement and we can improve upon it.

## Scenario - Fast Source, Slowing Sink with Backpressure Buffer ##

Having a buffer that drops mesages is less than ideal.  In situations where we have a reactive Source that will respond to backpressure signals but we still want to use a buffer we can simply change the ```OverflowStrategy``` of the buffer to ```OverflowStrategy.backpressure``` and we get the following chart in Grafana:

![](http://i.imgur.com/wzXGEjY.png)

As we can see from this chart, The Source ignores the Sink up until the buffer is full.  Once the buffer is full it signals backpressure and the Source slows its rate to meet the requirements of the Sink.  Once the message run is complete the producer stops and the Sink empties the buffer.

## Scenario - Fast Source With Two Sinks, One Fast, One Slowing ##

So far we have only looked at stream graphs with one Source and one Sink.  It would be interesting to look at what happens when we have two Sinks.  This also gives us an opportunity to look at another component in the akka stream toolbox.

Let's create another scenario to test this out:

*Scenarios.scala*
```scala

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

```
 The ```Broadcast``` component accepts one input that it then duplicates it to as many outputs as you wish.  In this example we have wired two output.  One output is to our Fast Sink.  The other is to our Slowing Sink.

Running this scenario gives us the following Grafana chart:

![](http://i.imgur.com/u4h8xaY.png)

Looking closely we can see that the whole stream is limited to the rate of the slowest sink.  This sink signals that it needs the producer to slow down, so the producer slows, which slows the rate at which it passes messages to the fast sink as well as the slow sink.

## Scenario - Fast Source With Two Sinks, One Fast, One Slowing with a Dropping Buffer ##

Having a stream graph that is limited to the rate of the slowest sink is not ideal.  One way of overcoming this limitation is to introduce a buffer between the source and the slow sink.  Consider the following scenario.

*Scenario.scala*

```scala

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
```

Running this scenario we get the following output:

![](http://i.imgur.com/gZBoTAG.png) 

As we can see, the source and the fast sink continue at maximum speed until all the messages are produced.  The slow sink proceeds at its own rate because the buffer ensures that it never gets overwhelmed.  However, this is at the cost of dropping messages!

## Scenario - Fast Source With Two Sinks, One Fast, One Slowing with a Backpressure Buffer ##

While buffering is useful when dealing with sinks that are able to processes messages at different rates, dropping messages is far from ideal in most situations.  If we use a bigger buffer with a *backpressure* overflow strategy, we can improve on our previous example.

*Scenario.scala*

```scala

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
```

This produces the following output:

![](http://i.imgur.com/tq808my.png)

In this scenario, the source and the fast sink process messages as fast as possible until the buffer for the slow sink fills to its limit.  Once this happens, the buffer signals backpressure and the source slows to the rate of the slow sink.

## Scenario - Fast Source With Two Sinks, One Fast, One Slowing with a Balancer ##

Buffering with backpressure is a good solution to our problem but there is another alternative which is a little more adaptive and ensures good utilisation of both subscribers.

The ```Balancer``` is a component that sends messages to an available sink.  As the slow subscriber slows, more messages will be sent to the fast sink.  Eventually the majority of messages will be sent to the fast sink.

*Scenarios.scala*

```scala

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

```

Running this scenario gives us the following output:

![](http://i.imgur.com/KmE1YZV.png)

Although its fairly subtle on this graph, as the slow sink gets slower the fast sinks picks up more of the messages.  Throughout this scenario the source never slows as it the balancer never signals any backpressure.  It is is worth nothing that if the fast sink gets overloaded, it will signal backpressure to the balancer and the balancer will signal the source, which will slow its rate of message production.

# Conclusion #

This has been a rather lengthy journey into reactive streams using akka-streams and the supporting tooling.  Along the way we've learnt a lot about how to create stream graphs, how to monitor them and how to use akka actors to encapsulate the functionality we need.  

There are more components to explore in akka-streams and more and more common tools are exposing reactive endpoints.  For example, both RabbitMQ and Kafka have reactive implementations that allow us to propogate backpressure from our akka streams to those components.

Reactive streams give us a really effective way of building flexible and adaptive message processing pipelines.  Hopefully this tutorial has provided enough information for you do take a look for yourselves. 