package user;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

public class User {
	
	private ByteBuffer inputBuffer = ByteBuffer.allocate(4096);
	private ByteBuffer outputBuffer = ByteBuffer.allocate(4096);
	
	private String MSG_PREFIX_NAME = "【客户端】的消息 : ";
	private static String SERVER_IP = "127.0.0.1";
//	private static String SERVER_IP = "192.168.14.10";
	private static int PORT = 0;
	private Selector selector;
	private KeyBoardListeningThread keyboardListeningThread;
	private Timer printSchedule;
	
	public User(int port){
		this.PORT = port;
	}
	
	/**
	 * 通道
	 */
	SocketChannel sc;
	
	/**
	 * 监听键
	 */
	SelectionKey sk;
	
	/**
	 * 发送队列
	 */
	private Queue<String> sendQueue = new ConcurrentLinkedQueue<String>();
	
	/**
	 * 发送队列
	 */
	private Queue<String> receiveQueue = new ConcurrentLinkedQueue<String>();
	
	
	public void init() throws IOException{
		SocketChannel clientSC = SocketChannel.open();
		clientSC.configureBlocking(false);
		this.selector = Selector.open();
		clientSC.connect(new InetSocketAddress(SERVER_IP, PORT));
		sk = clientSC.register(selector, SelectionKey.OP_CONNECT);
		sk.attach(this);
		keyboardListeningThread = new KeyBoardListeningThread(this, clientSC, selector);
		new Thread(keyboardListeningThread).start();
		printSchedule = new Timer();
		printSchedule.scheduleAtFixedRate(new PrinterThread(this), 0, 500);
		this.listening();
		System.err.println("========>>>>>>>客户端正在监听.....");
	}
	
	
	public void receivedMsg(SelectionKey slKey) throws IOException{
		SocketChannel channel = (SocketChannel) slKey.channel();
		channel.read(inputBuffer);
		inputBuffer.flip();
		short len = inputBuffer.getShort();
		byte[] strBytes = new byte[len];
		inputBuffer.get(strBytes);
		String str = new String(strBytes, "UTF-8");
		receiveQueue.add(str);
		inputBuffer.clear();
		channel.register(selector, SelectionKey.OP_READ);
	}
	
	public void sendMsg(SelectionKey slKey) throws IOException{
		slKey.interestOps(slKey.interestOps()
				& ~SelectionKey.OP_WRITE);
		SocketChannel channel = (SocketChannel) slKey.channel();
		Iterator<String> sendMsgs = sendQueue.iterator();
		while(sendMsgs.hasNext()){
			String str = sendMsgs.next();
			this.writeString(str);
			outputBuffer.flip();
			// TODO 写入失败或者不完全怎么办？ 
//			int writeNum = channel.write(outputBuffer);
//			if(outputBuffer.hasRemaining() || writeNum == 0){
//				continue;
//			}
			outputBuffer.clear();
			sendMsgs.remove();
		}
		channel.register(selector, SelectionKey.OP_READ);
	}
	
	public void listening() throws IOException{
		while(true){
			selector.select();
			Iterator<SelectionKey> it = selector.selectedKeys().iterator();
			while(it.hasNext()){
				SelectionKey slKey = (SelectionKey) it.next();
				it.remove();
				if(slKey.isConnectable()){
					SocketChannel clientSC = (SocketChannel) slKey.channel();
					if(clientSC.isConnectionPending()){
						clientSC.finishConnect();
						clientSC.configureBlocking(false);
					}
					clientSC.register(this.selector, SelectionKey.OP_READ);
				}
				else if(slKey.isReadable()){
					receivedMsg(slKey);
				}
				else if(slKey.isWritable()){
					sendMsg(slKey);
				}
			}
		}
	}

	public Queue<String> getSendQueue() {
		return sendQueue;
	}

	public void setSendQueue(Queue<String> sendQueue) {
		this.sendQueue = sendQueue;
	}

	public Queue<String> getReceiveQueue() {
		return receiveQueue;
	}

	public void setReceiveQueue(Queue<String> receiveQueue) {
		this.receiveQueue = receiveQueue;
	}
	
	public void wakeUp(){
		selector.wakeup();
	}
	
	public void writeString(String str) throws UnsupportedEncodingException{
		byte[] strBytesArray = str.getBytes("UTF-8");
		byte[] prefixBytes = MSG_PREFIX_NAME.getBytes("UTF-8");
		short length = (short) (prefixBytes.length + strBytesArray.length);
		byte[] mergeBytes = new byte[length];
		System.arraycopy(prefixBytes, 0, mergeBytes, 0, prefixBytes.length);
		System.arraycopy(strBytesArray, 0, mergeBytes, prefixBytes.length, strBytesArray.length);
		outputBuffer.putShort(length);
		outputBuffer.put(mergeBytes);
	}
	
	public static void main(String[] args) throws IOException{
		User user = new User(12315);
		user.init();
		user.listening();
	}
}

class KeyBoardListeningThread implements Runnable{
	
	User user;
	SocketChannel clientSC;
	Selector selector;
	
	public KeyBoardListeningThread(User user, SocketChannel clientSC, Selector selector){
		this.user = user;
		this.clientSC = clientSC;
		this.selector = selector;
	}
	
	public void waitForInput(User user) throws ClosedChannelException{
		Scanner sc = new Scanner(System.in);
		String str = sc.nextLine();
		user.getSendQueue().add(str);
		clientSC.register(selector, SelectionKey.OP_WRITE);
		user.wakeUp();
	}

	public void run() {
		while(true){
			try {
				waitForInput(user);
			} catch (ClosedChannelException e) {
				e.printStackTrace();
			}
		}
	}
}

class PrinterThread extends TimerTask{
	
	User user;
	
	public PrinterThread(User user){
		this.user = user;
	}
	
	@Override
	public void run() {
		try {
//			Iterator<String> it = user.getReceiveQueue().iterator();
//			while(user.ha){
//				String str = it.next();
//				it.remove();
//				System.out.println("【客户端】收到了一条消息：" + str);
//			}
			do{
				String receivedMsg = user.getReceiveQueue().poll();
				if(receivedMsg != null){
					System.out.println("【客户端】收到了一条消息：" + receivedMsg);
				}
				else{
					break;
				}
			}while(!user.getReceiveQueue().isEmpty());
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}
}
