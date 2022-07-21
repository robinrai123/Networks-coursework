/*
 * TextSender.java
 */
package datagramSocket1;
/**
 * @author abj
 */

import CMPC3M06.AudioRecorder;

import javax.sound.sampled.LineUnavailableException;
import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

class TextSenderThread implements Runnable {

	static DatagramSocket socket;

	public void start() {
		Thread thread = new Thread(this);
		thread.start();
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
			return false;

		} catch (Exception e) {
			socket.close();
            //System.out.println("oof");
		}
		return false;
	}

	public void run() {

		AudioRecorder recorder = null;
		try {
			recorder = new AudioRecorder();
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		}

		//***************************************************
		//Port to send to
		int PORT = 55555;
		int PORT2 = 55556;
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

		//***************************************************
		//Open a socket to send from
		//We dont need to know its port number as we never send anything to it.
		//We need the try and catch block to make sure no errors occur.

		//DatagramSocket socket;
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println("ERROR: TextSender: Could not open UDP socket to send from.");
			e.printStackTrace();
			System.exit(0);
		}

		//Main loop.
		int key = 15;//key for encryption
		int authenticationKey = 47;//authentication key
		boolean running = true;

		try {
			//TimeUnit.SECONDS.sleep(2);
			//TimeUnit.SECONDS.sleep(5);
			for (; ; ) {
				handshakeSend(authenticationKey, key, 10, 10, clientIP, PORT);
				System.out.println("A: Syn sent.\n");

				System.out.println("A: Listening for Syn Ack");
				if (handshakeReceive(authenticationKey, key, 15, PORT2)) {
					break;
				}


			}
			System.out.println("A: Syn Ack received.\n");

			//TimeUnit.SECONDS.sleep(1);

			handshakeSend(authenticationKey, key, 20, 30000, clientIP, PORT);
			System.out.println("A: Ack sent.\n");
			//TextDuplex1.senderDone = true;
			System.out.println("sender done");
			socket = new DatagramSocket();
		} catch (Exception e) {
			e.printStackTrace();
		}


		while (running) {
			try {
				System.out.println("in sender");
				//Read in a string from the standard input
               /* String str = in.readLine();


                //Convert it to an array of bytes
                byte[] buffer = str.getBytes();*/

				byte[] audioBlock = recorder.getBlock();
				//actual audio size of 512
				//System.out.println("audio block size is: " + audioBlock.length);

				ByteBuffer unwrapEncrypt = ByteBuffer.allocate(audioBlock.length + 4);
				//Byte buffer is size of audio and authentication key - 516
				//System.out.println("buffer size is: " + unwrapEncrypt.array().length);

				unwrapEncrypt.putInt(authenticationKey);
				//first 2 bytes are the authentication key


				unwrapEncrypt.put(audioBlock);
				//bytes 20 to 521 are the audio

				//encryption part
				unwrapEncrypt.clear();
				ByteBuffer encryptedBuffer = crypt(unwrapEncrypt, key);


				byte[] toSend = encryptedBuffer.array();
				//converts encrypted block to an array

				DatagramPacket packet = new DatagramPacket(toSend, toSend.length, clientIP, PORT);
				//Send it

				socket.send(packet);


/*                //The user can type EXIT to quit
                if (str.equals("EXIT")){
                    running=false;
                }*/

			} catch (IOException e) {
				System.out.println("ERROR: TextSender: Some random IO error occured!");
				e.printStackTrace();
			}
		}
		recorder.close();
		//Close the socket
		socket.close();
		//***************************************************
	}
} 