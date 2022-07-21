/*
 * TextReceiver.java
 */
package datagramSocket3;
/**
 * @author abj
 */

import CMPC3M06.AudioPlayer;
import uk.ac.uea.cmp.voip.DatagramSocket3;

import javax.sound.sampled.LineUnavailableException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

class TextReceiverThread implements Runnable {

	static DatagramSocket3 socket;
	AudioPlayer audioPlayer = null;

	private void playArray(byte[][] audioArray) throws Exception {
		for (int i = 0; i < audioArray.length; i++) {
			byte[] d = audioArray[i];
			if (d == null) {
				System.out.println("null block2");
				//d = empty;
				//lastPlayableBlock = deinterleavedArray[i-1];
				//d = empty;silence
				continue;//splicing

				//d= lastPlayableBlock;
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

	public static void handshakeSend(int authKey, int cryptKey, int input, int attempts, InetAddress clientIP, int PORT) {
		ByteBuffer toSend = ByteBuffer.allocate(8);
		try {
			socket = new DatagramSocket3();
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
			socket = new DatagramSocket3(port);
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
//IP ADDRESS to send to
		InetAddress clientIP = null;
		try {
			clientIP = InetAddress.getByName("localhost");  //CHANGE localhost to IP or NAME of client machine
		} catch (UnknownHostException e) {
			System.out.println("ERROR: TextSender: Could not find client IP");
			e.printStackTrace();
			System.exit(0);
		}
		//***************************************************
		//Open a socket to receive from on port PORT

		//DatagramSocket socket;
		try {
			socket = new DatagramSocket3();
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
		int interleaveSize = 9;
		int counter = 0;
		int curArraySeqNum = 0;
		final byte[] empty = new byte[512];
		byte[][] deinterleavedArray = new byte[interleaveSize][];
		byte[][] deinterleavedArray1 = new byte[interleaveSize][];
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
			socket = new DatagramSocket3(PORT);
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
						System.out.println("are you fucking up 1");
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
						System.out.println("are you fucking up? 2");
						deinterleavedArray = new byte[interleaveSize][];
						counter = 0;
						System.out.println("Counter reset");
					}
					continue;//Fail
				}

				int arraySeqNumCheck = receivedPacket.getInt();
				//Get seq number
				int seqNumber = receivedPacket.getInt();
				if (arraySeqNumCheck == 0) {
					if (deinterleavedArray[seqNumber] != null) {
						System.out.println("Occupied Block found... playing array THIS IS ZERO: " + arraySeqNumCheck);
						playArray(deinterleavedArray);
						deinterleavedArray = new byte[interleaveSize][];
					}
					System.out.println("Storing block " + arraySeqNumCheck + " " + seqNumber);
					deinterleavedArray[seqNumber] = receivedPacketData;
				} else {
					if (deinterleavedArray1[seqNumber] != null) {
						System.out.println("Occupied Block found... playing array: " + arraySeqNumCheck);
						playArray(deinterleavedArray1);
						deinterleavedArray1 = new byte[interleaveSize][];
					}
					System.out.println("Storing block " + arraySeqNumCheck + " " + seqNumber);
					deinterleavedArray1[seqNumber] = receivedPacketData;
				}
//				if(arraySeqNumCheck!=curArraySeqNum)
//				{
//					playArray(deinterleavedArray);
//					System.out.println("are you fucking up? 3");
//					deinterleavedArray = new byte[interleaveSize][];
//					counter = 1;
//					deinterleavedArray[seqNumber] = receivedPacketData;
//					System.out.println("Storing packet " + seqNumber);
//					System.out.println("Counter is now: " + counter);
//					curArraySeqNum=arraySeqNumCheck;
//					System.out.println("Arr seq num: " + curArraySeqNum);
//					continue;
//				}
//				if (counter < interleaveSize)
//				{
//
//					deinterleavedArray[seqNumber] = receivedPacketData;
//					System.out.println("Storing packet " + seqNumber);
//					counter++;//count number of packets in deinterleaved array
//					System.out.println("Counter is now: " + counter);
//				}
//				if (counter >= interleaveSize)
//				{
//					playArray(deinterleavedArray);
//					System.out.println("are you fucking up? 4");
//					deinterleavedArray = new byte[interleaveSize][];
//					counter = 0;
//					curArraySeqNum ^=1;
//					System.out.println("Arr seq num: " + curArraySeqNum);
//					System.out.println("Counter reset");
//				}
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