package sockets.combiner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import sockets.model.Message;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;

public class Processor {

    private final XmlMapper xmlMapper = new XmlMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<Integer, MessageStream> portToItsMessageStream = new ConcurrentHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(Processor.class);

    public Processor() {
        objectMapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
    }

    public void process(List<Pair<String, Integer>> hostsAndPorts) {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<>()) {
            var openedSockets = connectToSockets(hostsAndPorts, scope);

            openedSockets.forEach(it ->
                    portToItsMessageStream.put(
                            it.getPort(),
                            new MessageStream(new AtomicBoolean(true), new ConcurrentLinkedQueue<>()))
            );

            try (var scope2 = new StructuredTaskScope.ShutdownOnSuccess<>()) {
                openedSockets.forEach(it ->
                        scope2.fork(() -> {
                            readFromSocket(it);
                            return null;
                        }));

                scope2.fork(() -> {
                    combineStreams();
                    return null;
                });

                scope2.join();
            }

        } catch (InterruptedException e) {
            log.error("Processing stopped due to " + e.getMessage(), e);
        }
    }

    private List<Socket> connectToSockets(List<Pair<String, Integer>> hostsAndPorts, StructuredTaskScope.ShutdownOnSuccess<Object> scope) throws InterruptedException {
        List<StructuredTaskScope.Subtask<Socket>> subtasksForSocketConnection = new ArrayList<>();
        for (Pair<String, Integer> portAndHost: hostsAndPorts) {
            subtasksForSocketConnection.add(scope.fork(() -> establishSocketConnection(portAndHost.getLeft(), portAndHost.getRight())));
        }

        scope.join();
        var openedSockets = subtasksForSocketConnection.stream()
                .filter(it -> it.state() == StructuredTaskScope.Subtask.State.SUCCESS)
                .map(StructuredTaskScope.Subtask::get)
                .toList();
        log.info("Managed to connect to the following sockets: " + openedSockets);
        return openedSockets;
    }

    private Socket establishSocketConnection(String hostname, int port) {
        Socket socket = new Socket();
        log.info("Attempting to connect to socket " + hostname + ":" + port);
        try {
            socket.connect(new InetSocketAddress(hostname, port));
        } catch (IOException e) {
            log.error("Connect failed due to " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return socket;
    }

    private void readFromSocket(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Message value = xmlMapper.readValue(line, Message.class);
                portToItsMessageStream.get(socket.getPort()).messageQueue.add(value);
            }
        } catch (IOException e) {
            portToItsMessageStream.get(socket.getPort())
                    .isActive.set(false);
            log.error("Failed to read from socket " + socket + " due to " + e.getMessage() +
                    ", will stop processing stream from that socket", e);
            throw new RuntimeException(e);
        }
    }

    private void combineStreams() throws JsonProcessingException {
        while (true) {
            var countOfActiveOrOnesWithNonEmptyQueue = portToItsMessageStream.values()
                    .stream()
                    .filter(it -> it.isActive.get() || !it.messageQueue.isEmpty())
                    .count();
            if (countOfActiveOrOnesWithNonEmptyQueue == 0) {
                log.info("There are no active message streams anymore and all messages are processed.");
                break;
            }
            var pairsOfPortAndPeekElement = portToItsMessageStream.entrySet()
                    .stream()
                    .map(it -> Pair.of(it.getKey(), it.getValue().messageQueue.peek()))
                    .filter(it -> it.getRight() != null)
                    .toList();

            if (!pairsOfPortAndPeekElement.isEmpty() && pairsOfPortAndPeekElement.size() == countOfActiveOrOnesWithNonEmptyQueue) {
                var minTimestamp = pairsOfPortAndPeekElement.stream()
                        .min(Comparator.comparing(it -> it.getRight().getTimestamp()))
                        .get().getRight().getTimestamp();
                // find all messages with min timestamp
                var pairsWithMinimums = pairsOfPortAndPeekElement.stream()
                        .filter(it -> it.getRight().getTimestamp().equals(minTimestamp))
                        .toList();
                // remove from the queue
                pairsWithMinimums.forEach(it -> portToItsMessageStream.get(it.getLeft()).messageQueue.poll());

                var mergedAmounts = pairsWithMinimums.stream()
                        .map(it -> it.getRight().getAmount())
                        .reduce(0.0, Double::sum);

                log.info(objectMapper.writeValueAsString(new Message(minTimestamp, mergedAmounts)));
            }
        }
    }

    record MessageStream(AtomicBoolean isActive, ConcurrentLinkedQueue<Message> messageQueue) {

    }
}
