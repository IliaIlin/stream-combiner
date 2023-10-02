package sockets.producer;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import sockets.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;

public abstract class StreamProducer {
    private final int port;

    private final int socketTimeoutMillis;
    private PrintWriter stream;
    private final XmlMapper mapper = new XmlMapper();

    private static final Logger log = LoggerFactory.getLogger(StreamProducer.class);

    public StreamProducer(int port, int socketTimeoutMillis) {
        this.port = port;
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    public void start() {
        try {
            log.info("Starting producer on " + InetAddress.getLocalHost().getHostAddress() + ":" + port + " ...");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        try {
            establishConnection();
            scheduleDispatching();
        } catch (Exception e) {
            log.error("Couldn't emit data from producer due to " + e.getMessage(), e);
        }
    }

    protected abstract void scheduleDispatching() throws IOException;

    protected void pushMessageToStream(BigInteger timestamp) {
        try {
            if (stream.checkError()) {
                throw new IOException("Connectivity error");
            }
            var data = new Message(timestamp, new Random().nextDouble());
            String message = mapper.writeValueAsString(data);
            stream.println(message);
            log.info(message);
        } catch (IOException e) {
            log.error("Couldn't send message due to " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void establishConnection() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(socketTimeoutMillis);
            Socket clientSocket = serverSocket.accept();
            stream = new PrintWriter(clientSocket.getOutputStream(), true);
            log.info("Connection established on port " + port);
        }
    }
}
