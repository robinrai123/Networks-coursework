/*
 * TextDuplex.java
 */

package datagramSocket1;

/**
 * @author abj
 */
public class TextDuplex1 {


    public static void main(String[] args) {

        TextReceiverThread receiver = new TextReceiverThread();
        TextSenderThread sender = new TextSenderThread();

        receiver.start();
        sender.start();
        //pee

    }



}