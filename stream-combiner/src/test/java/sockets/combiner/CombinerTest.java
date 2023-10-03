package sockets.combiner;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import helper.MemoryLogAppender;
import helper.TestSockets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import sockets.model.Message;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CombinerTest {

    MemoryLogAppender memoryLogAppender;
    Logger combinerLogger;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        memoryLogAppender = new MemoryLogAppender();
        memoryLogAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());

        combinerLogger = (Logger) LoggerFactory.getLogger(Combiner.class);
        combinerLogger.addAppender(memoryLogAppender);

        memoryLogAppender.start();

        objectMapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
    }

    @Test
    @DisplayName("Terminates when no sockets in input")
    public void terminatesWhenNoSockets() {
        new Combiner(new ArrayList<>()).readAndCombineMessageStreams();
        assertTrue(memoryLogAppender.isMessageSubstringPresentInLogger(
                combinerLogger.getName(), "There are no active message streams anymore"));
    }

    /*
     Flaky test due to sockets communication, worth retrying
     */
    @Test
    @DisplayName("Correctly reads messages from one socket, deserializing them from XML and serializing to JSON")
    public void ableToReadFromOneSocket() throws JsonProcessingException, InterruptedException {
        TestSockets testSockets = new TestSockets();
        int port = 12345;
        var listOfSockets = testSockets.openSockets(List.of(port));

        Message message = new Message(BigInteger.TEN, 56.067);
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            scope.fork(() -> {
                new Combiner(listOfSockets).readAndCombineMessageStreams();
                return null;
            });
            scope.fork(() -> {
                testSockets.sendMessage(port, message);
                return null;
            });
            scope.join();
        }

        assertTrue(memoryLogAppender.isMessageSubstringPresentInLogger(
                combinerLogger.getName(), objectMapper.writeValueAsString(message)));
    }

    /*
    Flaky test due to sockets communication, worth retrying
    */
    @Test
    @DisplayName("Correctly reads/combines message with same timestamp from several sockets, deserializing them from XML and serializing to JSON")
    public void ableToReadFromSeveralSockets() throws JsonProcessingException, InterruptedException {
        TestSockets testSockets = new TestSockets();
        var listOfSockets = testSockets.openSockets(List.of(12345, 12346, 12347));

        Message messageOf12345 = new Message(BigInteger.ONE, 1.01);
        Message messageOf12346 = new Message(BigInteger.ONE, 4.1);
        Message messageOf12347 = new Message(BigInteger.ONE, 2.2);

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            scope.fork(() -> {
                new Combiner(listOfSockets).readAndCombineMessageStreams();
                return null;
            });
            scope.fork(() -> {
                testSockets.sendMessage(12345, messageOf12345);
                return null;
            });
            scope.fork(() -> {
                testSockets.sendMessage(12346, messageOf12346);
                return null;
            });
            scope.fork(() -> {
                testSockets.sendMessage(12347, messageOf12347);
                return null;
            });
            scope.join();
        }

        assertTrue(memoryLogAppender.isMessageSubstringPresentInLogger(
                combinerLogger.getName(), objectMapper.writeValueAsString(
                        new Message(BigInteger.ONE, messageOf12345.getAmount() + messageOf12346.getAmount() + messageOf12347.getAmount()))));
    }
}
