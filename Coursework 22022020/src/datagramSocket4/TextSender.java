/*
 * TextSender.java
 */
package datagramSocket4;
/**
 * @author abj
 */

import CMPC3M06.AudioRecorder;
import uk.ac.uea.cmp.voip.DatagramSocket4;
import uk.ac.uea.cmp.voip.DatagramSocket4;

import javax.sound.sampled.LineUnavailableException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

class TextSenderThread implements Runnable {

	static DatagramSocket4 socket;

	public void start() {
		Thread thread = new Thread(this);
		thread.start();
	}

	public static byte[] getMd5(byte[] input) {
		try {
			// Static getInstance method is called with hashing MD5
			MessageDigest md = MessageDigest.getInstance("MD5");//you can specify SHA-1, MD5 etc
			return md.digest(input);//return message digest
		}
		// For specifying wrong message digest algorithms
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	//Interleave method. Takes in a Packet array and an int d representing the width/height of the 2d array
	//The size of the interleave is dxd
	public static DatagramPacket[] interleave(DatagramPacket[] array, int d) {
		int index;
		int counter = 0;
		DatagramPacket[] interleaved = new DatagramPacket[array.length];
		for (int i = 0; i < d; i++) {
			for (int j = 0; j < d; j++) {
				index = j * d + (d - 1 - i);
				interleaved[counter] = array[index];
				counter++;
			}
		}
		return interleaved;
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
			socket = new DatagramSocket4();
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
			socket = new DatagramSocket4(port);
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
			socket = new DatagramSocket4();
		} catch (SocketException e) {
			System.out.println("ERROR: TextSender: Could not open UDP socket to send from.");
			e.printStackTrace();
			System.exit(0);
		}
		//***************************************************

		//***************************************************
		//Get a handle to the Standard Input (console) so we can read user input

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		//***************************************************

		//***************************************************
		//Main loop.
		int key = 15;//key for encryption
		int authenticationKey = 47;//authentication key
		boolean running = true;
		int seq_num = 0;
		int interleaveSize = 9;
		DatagramPacket[] toInterleave = new DatagramPacket[interleaveSize];
		DatagramPacket[] interleaved;


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
			socket = new DatagramSocket4();
		} catch (Exception e) {
			e.printStackTrace();
		}

		while (running) {
			try {
				//Read in a string from the standard input
               /* String str = in.readLine();


                //Convert it to an array of bytes
                byte[] buffer = str.getBytes();*/

				byte[] audioBlock = recorder.getBlock();
				//actual audio size of 512
				//System.out.println("audio block size is: " + audioBlock.length);

				ByteBuffer unwrapEncrypt = ByteBuffer.allocate(audioBlock.length + 4 + 4 + 16);
				//Byte buffer is size of audio and authentication key - 516
				//System.out.println("buffer size is: " + unwrapEncrypt.array().length);

				unwrapEncrypt.putInt(authenticationKey);
				//first 2 bytes are the authentication key

				unwrapEncrypt.putInt( seq_num);
				//next 2 bytes are the seq number

				//position is now 4
				byte[] hashCode = getMd5(audioBlock);
				unwrapEncrypt.put(hashCode);
				//Get the hashcode and put it into the buffer

				unwrapEncrypt.put(audioBlock);
				//bytes 20 to 521 are the audio

				//encryption part
				unwrapEncrypt.clear();
				ByteBuffer encryptedBuffer = crypt(unwrapEncrypt, key);


				byte[] toSend = encryptedBuffer.array();
				//converts encrypted block to an array

				DatagramPacket packet = new DatagramPacket(toSend, toSend.length, clientIP, PORT);
				//Send it

				if (seq_num < interleaveSize) {
					toInterleave[seq_num] = packet;
				}
				seq_num++;
				if (seq_num == interleaveSize) {
					interleaved = interleave(toInterleave, (int) Math.sqrt(interleaveSize));
					for (DatagramPacket d : interleaved) {
						socket.send(d);
					}
					seq_num = 0;
				}
				//socket.send(packet);


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