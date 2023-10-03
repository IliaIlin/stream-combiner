package sockets.combiner;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

public class SocketConnector {

    private static final Logger log = LoggerFactory.getLogger(SocketConnector.class);

    public List<Socket> connect(List<Pair<String, Integer>> hostsAndPorts) {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<>()) {
            List<StructuredTaskScope.Subtask<Socket>> subtasksForSocketConnection = new ArrayList<>();
            for (Pair<String, Integer> portAndHost : hostsAndPorts) {
                subtasksForSocketConnection.add(scope.fork(() -> establishSocketConnection(portAndHost.getLeft(), portAndHost.getRight())));
            }

            scope.join();
            var openedSockets = subtasksForSocketConnection.stream()
                    .filter(it -> it.state() == StructuredTaskScope.Subtask.State.SUCCESS)
                    .map(StructuredTaskScope.Subtask::get)
                    .toList();
            log.info("Managed to connect to the following sockets: " + openedSockets);
            return openedSockets;
        } catch (InterruptedException e) {
            log.error("Processing stopped due to " + e.getMessage(), e);
            return Collections.emptyList();
        }
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
}
