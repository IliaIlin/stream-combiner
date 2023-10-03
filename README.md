# Socket streams combiner

## Prerequisites

- Java 21

## How to build project

From the root of the project execute<br>
`./mvnw clean package` or `mvnw.cmd clean package`

## How to start producer (Socket server)

From the root of the project execute<br>
`java -jar stream-producer/target/stream-producer.jar <your_port>`

## How to start combiner (Socket client)

* Make sure `stream-combiner/src/main/resources/application.properties`
  contains all needed hosts:ports to connect to
* From the root of the project execute<br>
  `java -jar --enable-preview stream-combiner/target/stream-combiner.jar`

## How does combiner work

1. Attempts to connect to all hosts and ports specified in property file
2. All the successful connections are taken further into processing, failed ones are ignored and not retried (point of
   improvement)
3. Concurrent map now becomes center of a processing. Its key is a port of the socket. Its value is a pair of isActive flag and concurrent message queue.
4. Message queue for each socket port is filled while socket is active. When connection drops, isActive flag is set to false. 
5. Message queue is processed simultaneously with 4., queue is processed until it is empty despite the isActive flag - we want to process everything.
6. Algorithm of processing message queue is comparing heads of all non-empty message queues, minimums are removed from head of queues and merged if timestamps are equal.