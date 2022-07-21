/*
 * TextReceiver.java
 */
package datagramSocket2;
/**
 * @author abj
 */

import CMPC3M06.AudioPlayer;
import uk.ac.uea.cmp.voip.DatagramSocket2;

import javax.sound.sampled.LineUnavailableException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

class TextReceiverThread implements Runnable {

	static DatagramSocket2 socket;
	AudioPlayer audioPlayer = null;
	byte[] torepeatBlock = new byte[540];

	private void playArray(byte[][] audioArray) throws Exception {
		for (int i = 0; i < audioArray.length; i++) {
			byte[] d = audioArray[i];
			if (d != null) {
				System.out.println(i);
				torepeatBlock = d;
			} else {
				System.out.println("null block2 " + i);

				d = torepeatBlock;
			}
			byte[] audioBlock = Arrays.copyOfRange(d, 28, (540));
			audioPlayer.playBlock(audioBlock);
			//deinterleavedArray = new byte[interleaveSize][];
		}
		//lastPlayableBlock=deinterleavedArray[interleaveSize-1];
		System.out.println("Playing full block");
	}

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
			socket = new DatagramSocket2();
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
			socket = new DatagramSocket2(port);
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

	public static byte[] getMd5(byte[] input) {
		try {
			// Static getInstance method is called with hashing MD5
			MessageDigest md = MessageDigest.getInstance("MD5");
			return md.digest(input);
		}
		// For specifying wrong message digest algorithms
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
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
			socket = new DatagramSocket2();
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
		int interleaveSize = 16;
		int counter = 0;
		int curArraySeqNum = 0;
		byte[][] deinterleavedArray = new byte[interleaveSize][];
		byte[] lastPlayableBlock = new byte[540];

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
				handshakeSend(authenticationKey, key, 15, 3000, clientIP, PORT2);
				System.out.println("B: Syn Ack sent.\n");

				System.out.println("B: Listening for Ack");

				if (handshakeReceive(authenticationKey, key, 20, PORT)) {
					break;
				}
			}
			System.out.println("B: Ack received.\n");
			//TextDuplex1.receiverDone = true;
			System.out.println("Receiver done");
			socket = new DatagramSocket2(PORT);
		} catch (Exception e) {
			e.printStackTrace();
		}


		while (running) {
			try {
				//Receive a DatagramPacket (note that the string cant be more than 80 chars)
				byte[] buffer = new byte[540];
				DatagramPacket packet = new DatagramPacket(buffer, 0, 540);
				socket.receive(packet);
				int totalSize = buffer.length;
				ByteBuffer receivedPacket = ByteBuffer.wrap(packet.getData());
				
				receivedPacket = crypt(receivedPacket, key);

				
				receivedPacket.clear();//reset buffer pointers

				int receivedKey = receivedPacket.getInt();
				//get authentication key from header. Position is now 4

				if (receivedKey != authenticationKey) {
					System.out.println("invalid authentication key.\n");
					counter++; //this is not needed- adds delay
					System.out.println("Counter is now: " + counter);
					if (counter >= interleaveSize) {
						playArray(deinterleavedArray);
						deinterleavedArray = new byte[interleaveSize][];
						counter = 0;
						System.out.println("Counter reset");
					}
					continue;//ignore packet if authentication keys do not match
				}

				byte[] receivedPacketData = receivedPacket.array();
				//Authentication
				byte[] digest = Arrays.copyOfRange(receivedPacketData, 12, 28);
				byte[] audioBlockToDigest = Arrays.copyOfRange(receivedPacketData, 28, (540));
				byte[] calculatedDigest = getMd5(audioBlockToDigest);
				//If not authenticated
				if (!Arrays.equals(calculatedDigest, digest)) {
					System.out.println("This is not a legitimate packet.");
					counter++;
					System.out.println("Counter is now: " + counter);
					if (counter >= interleaveSize) {
						playArray(deinterleavedArray);
						deinterleavedArray = new byte[interleaveSize][];
						counter = 0;
						System.out.println("Counter reset");
					}
					continue;//Fail
				}

				int arraySeqNumCheck = receivedPacket.getInt();
				//Get seq number
				int seqNumber = receivedPacket.getInt();
				if (arraySeqNumCheck != curArraySeqNum) {
					playArray(deinterleavedArray);
					deinterleavedArray = new byte[interleaveSize][];
					counter = 1;
					deinterleavedArray[seqNumber] = receivedPacketData;
					System.out.println("Storing packet " + seqNumber);
					System.out.println("Counter is now: " + counter);
					curArraySeqNum = arraySeqNumCheck;
					continue;
				}

				//If still in current interleaved array
				if (counter < interleaveSize) {
					deinterleavedArray[seqNumber] = receivedPacketData;
					System.out.println("Storing packet " + seqNumber);
					counter++;//count number of packets in deinterleaved array
					System.out.println("Counter is now: " + counter);
				}

				if (counter >= interleaveSize) {
					playArray(deinterleavedArray);
					deinterleavedArray = new byte[interleaveSize][];
					counter = 0;
					System.out.println("Counter reset");
					curArraySeqNum ^= 1;
				}
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