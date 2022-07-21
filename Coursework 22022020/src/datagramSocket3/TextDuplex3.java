/*
 * TextDuplex.java
 */

package datagramSocket3;

/**
 *
 * @author  abj
 */
public class TextDuplex3 {

    public static void main (String[] args){

        TextReceiverThread receiver = new TextReceiverThread();
        TextSenderThread sender = new TextSenderThread();

        receiver.start();

        sender.start();
        //pee

    }

}