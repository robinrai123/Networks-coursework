/*
 * TextDuplex.java
 */

package datagramSocket4;

/**
 *
 * @author  abj
 */
public class TextDuplex4 {

    public static void main (String[] args){

        TextReceiverThread receiver = new TextReceiverThread();
        TextSenderThread sender = new TextSenderThread();

        receiver.start();
        sender.start();
        //pee

    }

}