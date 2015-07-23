package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class Server {

	private ByteBuffer inputBuffer = ByteBuffer.allocate(4096);
	private ByteBuffer outputBuffer = ByteBuffer.allocate(4096);
	private String CONNECTION_CALL_BACK_MSG = "Hello , Establish finished";
	private String CALL_BACK_MSG_PREFIX = "【服务器】回显信息: ";
	private static final int PORT = 12315;
	private Selector selector;
	
	public void init() throws IOException{
		System.out.println("========>>>>>>>初始化服务器端口");
		ServerSocketChannel serverSC = ServerSocketChannel.open();
		serverSC.configureBlocking(false);
		serverSC.socket().bind(new InetSocketAddress(PORT));
		selector = Selector.open();
		serverSC.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("========>>>>>>>服务器端口开始监听");
	}
	
	public void listening() throws IOException{
		while(true){
			try {
				selector.select();
				Iterator<SelectionKey> it = selector.selectedKeys().iterator();
				while(it.hasNext()){
					SelectionKey slKey = (SelectionKey) it.next();
					it.remove();
					dispatch(slKey);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void read(SocketChannel channel) throws IOException{
		channel.read(inputBuffer);
		inputBuffer.flip();
		short length = inputBuffer.getShort();
		byte[] msgBytes = new byte[length];
		inputBuffer.get(msgBytes);
		String receivedmsg = new String(msgBytes, "UTF-8");
		System.err.println("========>>>>>>>服务器收到信息: " + receivedmsg);
		inputBuffer.clear();
		write(channel, receivedmsg);
	}
	
	public void write(SocketChannel channel, String str) throws IOException{
		byte[] outputData = str.getBytes("UTF-8");
		byte[] prefixBytes = CALL_BACK_MSG_PREFIX.getBytes("UTF-8");
		byte[] callBackBytes = new byte[outputData.length + prefixBytes.length];
		System.arraycopy(prefixBytes, 0, callBackBytes, 0, prefixBytes.length);
		System.arraycopy(outputData, 0, callBackBytes, prefixBytes.length, outputData.length);
		outputBuffer.putShort((short)callBackBytes.length);
		outputBuffer.put(callBackBytes);
		outputBuffer.flip();
		channel.write(outputBuffer);
		outputBuffer.clear();
	}
	
	
	public void dispatch(SelectionKey slKey) throws IOException{
		if(slKey.isAcceptable()){
			System.err.println("========>>>>>>>【服务端】收到新连接");
			ServerSocketChannel serverSC = (ServerSocketChannel) slKey.channel();
			SocketChannel clientSC = serverSC.accept();
			clientSC.configureBlocking(false);
			write(clientSC, CONNECTION_CALL_BACK_MSG);
			clientSC.register(selector, SelectionKey.OP_READ);
		}
		else if(slKey.isReadable()){
			try {
				System.err.println("========>>>>>>>【服务端】收到新消息");
				SocketChannel clientSC = (SocketChannel) slKey.channel();
				read(clientSC);
				clientSC.register(selector, SelectionKey.OP_READ);
			} catch (Exception e) {
				System.err.println("========>>>>>>>客户端断开连接");
				slKey.cancel();
				e.printStackTrace();
			}
		}
	}
	
	public static void start() throws IOException {
		Server server = new Server();
		server.init();
		server.listening();
		System.out.println("服务端正在监听中.....");
	}
}
