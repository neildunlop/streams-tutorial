Streams Tutorial
====

A reasonably in depth Akka Streams tutorial, including performance monitoring a charting.


For a step by step guide see ```Tutorial.md```.  To get started quickly, do this:

1.  In the terminal, execute ```docker run -p 80:80 -p 8125:8125/udp -p 8126:8126 -p 8083:8083 -p 8086:8086 -p 8084:8084 --name kamon-grafana-dashboard muuki88/grafana_graphite:latest```
2.  Fire up the application from the terminal with ```sbt run```.  If you dont do this then no performance metrics will be recorded.
3.  Choose the scenario you want to run.
4.  Navigate to ```http://192.168.99.100``` in your web browser then click on the title of the chart to edit it.
5. Include the application metrics to see what is happening in the reactive stream.

(Seriously, go and read the tutorial!)

