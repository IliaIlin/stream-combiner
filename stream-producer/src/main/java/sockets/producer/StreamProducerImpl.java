package sockets.producer;

import java.math.BigInteger;
import java.util.Timer;
import java.util.TimerTask;

public class StreamProducerImpl extends StreamProducer {

    final int producerPeriodMillis;

    public StreamProducerImpl(int port, int socketTimeoutMillis, int producerPeriodMillis) {
        super(port, socketTimeoutMillis);
        this.producerPeriodMillis = producerPeriodMillis;
    }

    @Override
    protected void scheduleDispatching() {
        final int[] timestamp = {0};
        Timer timer = new Timer();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                timestamp[0] += 1;
                pushMessageToStream(BigInteger.valueOf(timestamp[0]));
            }
        };
        timer.scheduleAtFixedRate(task, 0, producerPeriodMillis);
    }
}
