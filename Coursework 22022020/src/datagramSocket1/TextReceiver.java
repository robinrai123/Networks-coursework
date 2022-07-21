/*
 * TextReceiver.java
 */
package datagramSocket1;
/**
 * @author abj
 */

import CMPC3M06.AudioPlayer;
import org.w3c.dom.Text;

import javax.sound.sampled.LineUnavailableException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

class TextReceiverThread implements Runnable {

	static DatagramSocket socket;
	AudioPlayer audioPlayer = null;


	static ByteBuffer crypt(ByteBuffer input, int key) {
		int inputLength = input.array().length;
		input.clear();
		ByteBuffer output = ByteBuffer.allocate(inputLength);
		for (int j = 0; j < inputLength / 4; j++) {
			int fourByte = input.getInt();
			fourByte = (fourByte ^ key);      //xor decrypt
			output.putInt(fourByte);
		}
		return output;
	}

	public static void handshakeSend(int authKey, int cryptKey, int input, int attempts, InetAddress clientIP, int PORT) {
		ByteBuffer toSend = ByteBuffer.allocate(8);
		try {
			socket = new DatagramSocket();
			for (int i = 0; i < attempts; i++) {
				toSend.putInt(authKey);
				toSend.putInt(input);
				toSend = crypt(toSend, cryptKey);
				DatagramPacket packet = new DatagramPacket(toSend.array(), toSend.array().length, clientIP, PORT);
				socket.send(packet);
			}
			socket.close();
		} catch (Exception e) {
		}
	}

	public static boolean handshakeReceive(int authKey, int cryptKey, int input, int port) {
		try {
			socket = new DatagramSocket(port);
			socket.setSoTimeout(3000);
			byte[] buffer = new byte[8];
			DatagramPacket packet = new DatagramPacket(buffer, 0, 8);
			socket.receive(packet);

			ByteBuffer receivedPacket = ByteBuffer.wrap(packet.getData());
			receivedPacket = crypt(receivedPacket, cryptKey);
			receivedPacket.clear();
			int receivedKey = receivedPacket.getInt();
			int message = receivedPacket.getInt();

			//get authentication key from header. Position is now 4

			if (receivedKey == authKey) {
				if (message == input) {
					socket.close();
					return true;
				}
			}


		} catch (Exception e) {
			socket.close();
		}
		return false;
	}

	public void start() {
		Thread thread = new Thread(this);
		thread.start();
	}


	public void run() {
		try {
			audioPlayer = new AudioPlayer();
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		}
		//***************************************************
		//Port to open socket on
		int PORT = 55555;
		int PORT2 = 55556;
		//***************************************************

		//***************************************************

		//IP ADDRESS to send to
		InetAddress clientIP = null;
		try {
			clientIP = InetAddress.getByName("localhost");  //CHANGE localhost to IP or NAME of client machine
		} catch (UnknownHostException e) {
			System.out.println("ERROR: TextSender: Could not find client IP");
			e.printStackTrace();
			System.exit(0);
		}
		//Open a socket to receive from on port PORT

		//DatagramSocket socket;
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println("ERROR: TextReceiver: Could not open UDP socket to receive from.");
			e.printStackTrace();
			System.exit(0);
		}
		//***************************************************

		//***************************************************
		//Main loop.

		boolean running = true;
		int key = 15;
		int authenticationKey = 47;


		try {
			//TimeUnit.SECONDS.sleep(15);
			System.out.println("B: Listening for Syn");
			for (; ; ) {
				if (handshakeReceive(authenticationKey, key, 10, PORT)) {
					break;
				}
			}
			System.out.println("B: Syn received.\n");

			//TimeUnit.SECONDS.sleep(1);

			for (; ; ) {
			handshakeSend(authenticationKey, key, 15, 2000, clientIP, PORT2);
			System.out.println("B: Syn Ack sent.\n");

			System.out.println("B: Listening for Ack");

				if (handshakeReceive(authenticationKey, key, 20, PORT)) {
					break;
				}
			}
			System.out.println("B: Ack received.\n");
			//TextDuplex1.receiverDone = true;
			System.out.println("Receiver done");
			socket = new DatagramSocket(PORT);
		} catch (Exception e) {
			e.printStackTrace();
		}

		while (running) {
			System.out.println("in receiver");
			try {
				//Receive a DatagramPacket (note that the string cant be more than 80 chars)
				byte[] buffer = new byte[516];
				DatagramPacket packet = new DatagramPacket(buffer, 0, 516);
				socket.receive(packet);
				int totalSize = buffer.length;
				ByteBuffer receivedPacket = ByteBuffer.wrap(packet.getData());
				ByteBuffer decryptedPacket = ByteBuffer.allocate(totalSize);
				for (int j = 0; j < totalSize / 4; j++) {
					int fourByte = receivedPacket.getInt();
					fourByte = fourByte ^ key;      //xor decrypt
					decryptedPacket.putInt(fourByte);
				}
				decryptedPacket.clear();//reset buffer pointers

				int receivedKey = decryptedPacket.getInt();
				//get authentication key from header. Position is now 4

				if (receivedKey != authenticationKey) {
					System.out.println("invalid authentication key.\n");
					continue;//ignore packet if authentication keys do not match
				}

				byte[] audioBlock = Arrays.copyOfRange(decryptedPacket.array(), 4, 516);


				//Authentication

				//byte[]
				audioPlayer.playBlock(audioBlock);


			} catch (Exception e) {
				System.out.println("ERROR: TextReceiver: Some random IO error occurred!");
				e.printStackTrace();
			}
		}
		//Close the socket
		socket.close();
		//***************************************************
	}
}