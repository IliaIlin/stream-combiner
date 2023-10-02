package sockets.model;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.math.BigInteger;

@JsonRootName(value = "data")
@JacksonXmlRootElement(localName = "data")
public class Message {

    private BigInteger timestamp;
    private double amount;

    public Message() {
    }

    public Message(BigInteger timestamp, double amount) {
        this.timestamp = timestamp;
        this.amount = amount;
    }

    public BigInteger getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(BigInteger timestamp) {
        this.timestamp = timestamp;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Message{" +
                "timestamp=" + timestamp +
                ", amount=" + amount +
                '}';
    }
}
