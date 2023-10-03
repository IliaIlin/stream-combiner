package sockets.combiner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockets.model.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;

public class Combiner {
    private final XmlMapper xmlMapper = new XmlMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(Combiner.class);

    private final List<Socket> openedSockets;
    private final ConcurrentHashMap<Integer, MessageStream> portToItsMessageStream;

    Combiner(List<Socket> openedSockets) {
        this.openedSockets = openedSockets;
        portToItsMessageStream = new ConcurrentHashMap<>();
        openedSockets.forEach(it ->
                portToItsMessageStream.put(
                        it.getPort(),
                        new MessageStream(new AtomicBoolean(true), new ConcurrentLinkedQueue<>()))
        );
        objectMapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
    }

    public void readAndCombineMessageStreams() {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<>()) {
            openedSockets.forEach(it ->
                    scope.fork(() -> {
                        readFromSocket(it);
                        return null;
                    }));

            scope.fork(() -> {
                combineStreams();
                return null;
            });

            scope.join();
        } catch (InterruptedException e) {
            log.error("Issue during combining streams due to " + e.getMessage(), e);
        }
    }

    private void readFromSocket(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Message value = xmlMapper.readValue(line, Message.class);
                portToItsMessageStream.get(socket.getPort()).messageQueue().add(value);
            }
        } catch (IOException e) {
            portToItsMessageStream.get(socket.getPort())
                    .isActive().set(false);
            log.error("Failed to read from socket " + socket + " due to " + e.getMessage() +
                    ", will stop processing stream from that socket", e);
            throw new RuntimeException(e);
        }
    }

    private void combineStreams() throws JsonProcessingException {
        while (true) {
            var countOfActiveOrOnesWithNonEmptyQueue = portToItsMessageStream.values()
                    .stream()
                    .filter(it -> it.isActive().get() || !it.messageQueue().isEmpty())
                    .count();
            if (countOfActiveOrOnesWithNonEmptyQueue == 0) {
                log.info("There are no active message streams anymore and all messages are processed.");
                break;
            }
            var pairsOfPortAndHeadOfQueue = portToItsMessageStream.entrySet()
                    .stream()
                    .map(it -> Pair.of(it.getKey(), it.getValue().messageQueue().peek()))
                    .filter(it -> it.getRight() != null)
                    .toList();

            combineHeadsOfQueues(pairsOfPortAndHeadOfQueue, countOfActiveOrOnesWithNonEmptyQueue);
        }
    }

    private void combineHeadsOfQueues(List<Pair<Integer, Message>> pairsOfPortAndHeadOfQueue, long countOfActiveOrOnesWithNonEmptyQueue) throws JsonProcessingException {
        if (!pairsOfPortAndHeadOfQueue.isEmpty() && pairsOfPortAndHeadOfQueue.size() == countOfActiveOrOnesWithNonEmptyQueue) {
            var minTimestamp = pairsOfPortAndHeadOfQueue.stream()
                    .min(Comparator.comparing(it -> it.getRight().getTimestamp()))
                    .get().getRight().getTimestamp();
            // find all messages with min timestamp
            var pairsWithMinimums = pairsOfPortAndHeadOfQueue.stream()
                    .filter(it -> it.getRight().getTimestamp().equals(minTimestamp))
                    .toList();
            // remove from the queue
            pairsWithMinimums.forEach(it -> portToItsMessageStream.get(it.getLeft()).messageQueue().poll());

            var mergedAmounts = pairsWithMinimums.stream()
                    .map(it -> it.getRight().getAmount())
                    .reduce(0.0, Double::sum);

            log.info(objectMapper.writeValueAsString(new Message(minTimestamp, mergedAmounts)));
        }
    }
}
