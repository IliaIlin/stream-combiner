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
From the root of the project execute<br>
`java -jar --enable-preview stream-combiner/target/stream-combiner.jar`