package implementations.dm_kernel.super_peer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import commom.GenericConsumer;
import commom.GenericResource;
import commom.JCL_connector;
import commom.JCL_handler;
import implementations.dm_kernel.MessageMetadataImpl;
import implementations.dm_kernel.Server;
import implementations.sm_kernel.JCL_orbImpl;
import implementations.util.ServerDiscovery;
import implementations.util.UDPServer;
import interfaces.kernel.JCL_message_metadata;

import static implementations.dm_kernel.host.MainHost.getNameIPPortDuplicate;


public class MainSuperPeer extends Server{

	private ConcurrentHashMap<String,Map<String,String>> slaves;
	private JCL_connector routerLink;
	Map<String,String> metaData;
	private static Boolean verbose;
	private static String nic,serverAdd;
	private static int routerPort,routerLinks,serverPort, superPeerPort;
	private AtomicInteger registerMsg;
	private static String superpeerID;
	


	public static void main(String[] args) {
		// TODO Auto-generated method stub

		// Read properties file.
		Properties properties = new Properties();
		try {
		    properties.load(new FileInputStream("../jcl_conf/config.properties"));
		}catch (FileNotFoundException e){					
			System.err.println("File not found (../jcl_conf/config.properties) !!!!!");
			System.out.println("Create properties file ../jcl_conf/config.properties.");
			try {
				File file = new File("../jcl_conf/config.properties");
				file.getParentFile().mkdirs(); // Will create parent directories if not exists
				file.createNewFile();
								
				OutputStream output = new FileOutputStream(file,false);

				// set the properties value
				properties.setProperty("distOrParell", "true");
				properties.setProperty("serverMainPort", "6969");
				properties.setProperty("superPeerMainPort", "6868");
				

				properties.setProperty("routerMainPort", "7070");
				properties.setProperty("serverMainAdd", "127.0.0.1");
				properties.setProperty("hostPort", "5151");


				properties.setProperty("nic", "");
				properties.setProperty("simpleServerPort", "4949");
				properties.setProperty("timeOut", "5000");

				properties.setProperty("byteBuffer", "5242880");
				properties.setProperty("routerLink", "5");
				properties.setProperty("enablePBA", "false");

				properties.setProperty("PBAsize", "50");
				properties.setProperty("delta", "0");
				properties.setProperty("PGTerm", "10");

				properties.setProperty("twoStep", "false");
				properties.setProperty("useCore", "100");
				properties.setProperty("deviceID", "Host1");

				properties.setProperty("enableDinamicUp", "false");
				properties.setProperty("findServerTimeOut", "1000");
				properties.setProperty("findHostTimeOut", "1000");
						 
				properties.setProperty("enableFaultTolerance", "false");
				properties.setProperty("verbose", "true");
				properties.setProperty("encryption", "false");

				properties.setProperty("deviceType", "3");
				properties.setProperty("mqttBrokerAdd", "127.0.0.1");
				properties.setProperty("mqttBrokerPort", "1883");

				//save properties to project root folder
			
				properties.store(output, null);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
					e1.printStackTrace();
			}
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		superPeerPort = Integer.parseInt(properties.getProperty("superPeerMainPort"));
		routerPort = Integer.parseInt(properties.getProperty("routerMainPort"));
		routerLinks = Integer.parseInt(properties.getProperty("routerLink"));		
		serverAdd = properties.getProperty("serverMainAdd");
		serverPort = Integer.parseInt(properties.getProperty("serverMainPort"));
		verbose =  Boolean.parseBoolean(properties.getProperty("verbose"));
		nic = properties.getProperty("nic");
		
		
		try {
			new MainSuperPeer(superPeerPort);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public MainSuperPeer(int portS) throws IOException {		
		//Start Server
		super(portS);

		this.slaves = new ConcurrentHashMap<String,Map<String,String>>();
        this.routerLink = new JCL_connector();
        this.metaData = getNameIPPort();
        this.metaData.put("PORT", String.valueOf(portS));
        this.superpeerID = this.metaData.get("MAC") + this.metaData.get("PORT");		
		this.registerMsg = new AtomicInteger();
		JCL_handler.setRegisterMsg(registerMsg);
		JCL_orbImpl.setRegisterMsg(registerMsg);
				
		this.begin();
	}

	@Override
	public <K extends JCL_handler> GenericConsumer<K> createSocketConsumer(GenericResource<K> r, AtomicBoolean kill) {
		// TODO Auto-generated method stub
		return new SocketConsumer<K>(r,kill, this.routerLink,this.slaves,this.superpeerID,serverAdd,serverPort);
	}

	@Override
	protected void beforeListening() {
		// TODO Auto-generated method stub
		try {
						
//			 Set<SelectionKey> LK =  this.selector.keys();
	           
//	           for(SelectionKey k:LK){
//	        	  System.out.println("int OP:"+k.interestOps());
//	           }
			
			
			
			SocketChannel sock = SocketChannel.open();
			sock.configureBlocking(false);					
			sock.socket().setTcpNoDelay(true);
			sock.socket().setKeepAlive(true);

          //  Map<String,String> metaData = getNameIPPort();
    		metaData.put("DEVICE_TYPE","5");         
            JCL_message_metadata msg = new MessageMetadataImpl();
	    	msg.setType(-4);				
			msg.setMetadados(metaData);

			this.selector.wakeup();
            SelectionKey sk = sock.register(this.selector,SelectionKey.OP_CONNECT);			

            this.routerLink.setSocket(sock);
            this.routerLink.setSk(sk);
            this.routerLink.setSel(this.selector);
//          this.routerLink.setLock(this.selectorLock);
            this.routerLink.setServerR(this.serverR);
            this.routerLink.setMsg(msg);
            this.routerLink.setMac(macConvert(metaData.get("MAC")));

            sk.attach(this.routerLink);
            boolean connected = sock.connect(new java.net.InetSocketAddress(serverAdd, routerPort));
            if (!connected){
    			String serverData[] = ServerDiscovery.discoverServerRouterPort();
    			if (serverData != null){
    				serverAdd = serverData[0];
    				routerPort = Integer.parseInt(serverData[1]);
    				System.out.println(serverAdd + "  " + routerPort);
    				
    				sock.finishConnect();
    				sock = SocketChannel.open();
    				sock.configureBlocking(false);					
    				sock.socket().setTcpNoDelay(true);
    				sock.socket().setKeepAlive(true);
    				sk = sock.register(this.selector,SelectionKey.OP_CONNECT);			

    	            this.routerLink.setSocket(sock);
    	            this.routerLink.setSk(sk);
    	            this.routerLink.setSel(this.selector);
//    	          this.routerLink.setLock(this.selectorLock);
    	            this.routerLink.setServerR(this.serverR);
    	            this.routerLink.setMsg(msg);
    	            this.routerLink.setMac(macConvert(metaData.get("MAC")));

    	            sk.attach(this.routerLink);
    				sock.connect(new java.net.InetSocketAddress(serverAdd, routerPort));
    			}
            }
            this.selector.wakeup();

	    	 JCL_message_metadata msgT = new MessageMetadataImpl();
		     msgT.setType(-100);				
			 msgT.setMetadados(metaData);

            for(int i = 0;i < routerLinks;i++){
            	
            	//Create new socket link
            	SocketChannel sockN = SocketChannel.open();
    			sockN.configureBlocking(false);					
    			sockN.socket().setTcpNoDelay(true);
    			sockN.socket().setKeepAlive(true);
    			
    			//Type new tunnel 
    	    	//msg.setType(-100);

    			
    			//wakeup a Selection
    			this.selector.wakeup();
    			SelectionKey skN = sockN.register(this.selector,SelectionKey.OP_CONNECT);    			
    			JCL_connector conecN = new JCL_connector(sockN,skN,this.selector,this.serverR,msgT, macConvert(metaData.get("MAC")));
                skN.attach(conecN);            
                sockN.connect(new java.net.InetSocketAddress(serverAdd, routerPort));
    			this.selector.wakeup();

            }
                  
            
//           LK =  this.selector.keys();
//           
//           for(SelectionKey k:LK){
//        	  System.out.println("int OP:"+k.interestOps());
//           }
            
            ShutDownHook();
//           sk.attach(new JCL_acceptor(this.serverSocket,this.selectorRead,this.selectorReadLock,this.serverR));

			Thread t = new Thread(new UDPServer(superPeerPort, routerPort));
			t.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
		
	}
	
	public byte[] macConvert(String macAddress){
		String[] macAddressParts = macAddress.split("-");
		byte[] macAddressBytes = new byte[6];

        JCL_handler.macConvertDuplicate(macAddress, macAddressParts, macAddressBytes);
        return macAddressBytes;
	}

	@Override
	protected void duringListening() {
		// TODO Auto-generated method stub
		
	}
	
	private void ShutDownHook() {
	    Runtime.getRuntime().addShutdownHook(new Thread() {
	    	
	      @Override
	      public void run() {
	    	try {
			
	    	//	Map<String,String> metaData = getNameIPPort();
    		//	metaData.put("DEVICE_TYPE","5");         
            	JCL_message_metadata msg = new MessageMetadataImpl();
	    		msg.setType(-5);				
				msg.setMetadados(metaData);			
				routerLink.send(msg);
				Thread.sleep(1000);			
	    	} 	    	
	    	catch (Exception e) {
				System.err.println("Erro in unregister host!");
			}
	      }
	    });
	  }
	
	private Map<String,String> getNameIPPort(){
		Map<String,String> IPPort = new HashMap<String,String>();
		try {			
			//InetAddress ip = InetAddress.getLocalHost();
			InetAddress ip = getLocalHostLANAddress();
			StringBuilder sb = getNameIPPortDuplicate(ip);
		//	String[] result = {ip.getHostAddress(), hostPort, sb.toString(),Integer.toString(CoresAutodetect.cores)};
			
//			IPPort.put("IP", ip.getHostAddress());
			IPPort.put("MAC", sb.toString());
			IPPort.put("CORE(S)", "0");

			return IPPort;
			
	 
		} catch (Exception e) {
			
			try {
				InetAddress ip = InetAddress.getLocalHost();
			
				String sb = ip.getHostAddress();
	//			IPPort.put("IP", sb);
				IPPort.put("MAC", sb);
				IPPort.put("CORE(S)", "0");

				return IPPort;
			} catch (UnknownHostException e1) {
				System.err.println("cannot collect host address");
				return null;
			}
			
			
		}
	}
	
	private static InetAddress getLocalHostLANAddress() throws UnknownHostException {
		return getInetAddressDuplicate(nic);
	}	

}
