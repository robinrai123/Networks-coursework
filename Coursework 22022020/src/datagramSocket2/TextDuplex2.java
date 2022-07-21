/*
 * TextDuplex.java
 */

package datagramSocket2;

/**
 *
 * @author  abj
 */
public class TextDuplex2 {

    public static void main (String[] args){

        TextReceiverThread receiver = new TextReceiverThread();
        TextSenderThread sender = new TextSenderThread();

        receiver.start();
        sender.start();
        //pee

    }

}