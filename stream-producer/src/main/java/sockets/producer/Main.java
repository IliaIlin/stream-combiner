package sockets.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        var port = getPort(args);
        var properties = readProperties();
        var socketTimeoutMillis = Integer.valueOf((String) properties.get("socket.connect.timeout.millis"));
        var producerPeriodMillis = Integer.valueOf((String) properties.get("producer.emission.period.millis"));
        var producer = new StreamProducerImpl(port, socketTimeoutMillis, producerPeriodMillis);
        producer.start();
    }

    private static int getPort(String[] args) {
        if (args.length != 1) {
            log.error("Please specify port as a program argument next time, terminating");
            System.exit(-1);
        }
        return Integer.parseInt(args[0]);
    }

    private static Properties readProperties() {
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("application.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            return prop;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
