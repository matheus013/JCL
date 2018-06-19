package implementations.dm_kernel.server;


import implementations.dm_kernel.ConnectorImpl;
import implementations.dm_kernel.Server;
import implementations.dm_kernel.router.Router;
import implementations.dm_kernel.user.JCL_FacadeImpl;
import implementations.sm_kernel.JCL_orbImpl;
import implementations.util.UDPServer;
import implementations.util.IoT.CryptographyUtils;
import interfaces.kernel.JCL_message_register;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import commom.Constants;
import commom.GenericConsumer;
import commom.GenericResource;
import commom.JCL_handler;

public class MainServer extends Server{
	
	private ConcurrentMap<Integer,ConcurrentMap<String,String[]>> slaves;
	private List<Entry<String, Map<String, String>>> devicesExec;
	private ConcurrentMap<Integer,ConcurrentMap<String,Map<String,String>>> metadata;
	private ConcurrentMap<Object,Map<String, String>> globalVarSlaves;
	private AtomicInteger registerMsg;
	private ConcurrentMap<String,List<String>> jarsSlaves;
	private ConcurrentMap<Integer,List<String>> slavesIDs;
	private static TrayIconJCL icon;
	private ConcurrentMap<String,JCL_message_register> jars;
	private ConcurrentMap<String,String[]> runningUser;	
	private static Boolean verbose;
	private static String nic;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub		
				
		// Read properties file.
		Properties properties = new Properties();
		JCL_FacadeImpl.getInstancePacuDuplicate(properties);

		int serverPort = Integer.parseInt(properties.getProperty("serverMainPort"));
		int routerPort = Integer.parseInt(properties.getProperty("routerMainPort"));
		ConnectorImpl.encryption = Boolean.parseBoolean(properties.getProperty("encryption"));		
		nic = properties.getProperty("nic");
		verbose =  Boolean.parseBoolean(properties.getProperty("verbose"));
		
		try {
			new MainServer(serverPort,routerPort);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public MainServer(int portS, int portR) throws IOException{
		
		//Start Server
		super(portS);
		CryptographyUtils.setClusterPassword(this.getMac());
		this.globalVarSlaves = new ConcurrentHashMap<Object, Map<String, String>>();
		this.slavesIDs = new ConcurrentHashMap<Integer,List<String>>();
		this.slaves = new ConcurrentHashMap<Integer,ConcurrentMap<String,String[]>>();
		this.metadata = new ConcurrentHashMap<Integer,ConcurrentMap<String,Map<String,String>>>();		
		this.jarsSlaves = new ConcurrentHashMap<String,List<String>>();
		this.jars = new ConcurrentHashMap<String, JCL_message_register>();
		this.runningUser = new ConcurrentHashMap<String, String[]>();
		this.devicesExec = new ArrayList<Entry<String, Map<String, String>>>();

		try {
			icon = new TrayIconJCL(this.metadata);			
		} catch (Exception e) {
			System.out.println("OS TrayIcon not supported!!!");
		}
		
		this.registerMsg = new AtomicInteger();
		JCL_handler.setRegisterMsg(registerMsg);
		JCL_orbImpl.setRegisterMsg(registerMsg);

		System.err.println("JCL server ok!");

		Thread t = new Thread(new UDPServer(portS, portR));
		t.start();	
		
		//Router Super-Peer 		
		new Thread(new Router(portR,super.getServerR(),nic)).start();
		System.err.println("JCL router ok!");
		
		this.begin();

				
	}

	@Override
	protected void beforeListening() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void duringListening() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public <K extends JCL_handler> GenericConsumer<K> createSocketConsumer(
			GenericResource<K> r, AtomicBoolean kill){
		// TODO Auto-generated method stub
		return new SocketConsumer<K>(r,kill, this.globalVarSlaves, this.slavesIDs, this.slaves,this.jarsSlaves,this.jars,verbose,runningUser,metadata,this.devicesExec,icon);

	}
	
	private String getMac(){
//		Map<String,String> IPPort = new HashMap<String,String>();
		try {			
			//InetAddress ip = InetAddress.getLocalHost();
			InetAddress ip = getLocalHostLANAddress();
//			System.out.println("Current IP address : " + ip.getHostAddress());
	 
			NetworkInterface network = NetworkInterface.getByInetAddress(ip);
	 
			byte[] mac = network.getHardwareAddress();
	 
//			System.out.print("Current MAC address : ");
	 
			StringBuilder sb = new StringBuilder(17);
			for (int i = 0; i < mac.length; i++) {
				sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));		
			}
			
			if (sb.length()==0) sb.append(ip.getHostAddress());
			
			System.out.println(sb.toString());

			return sb.toString();
			
	 
		} catch (Exception e) {
			
			try {
				InetAddress ip = InetAddress.getLocalHost();			
				String sb = ip.getHostAddress();
				
				byte[] mac = macConvert(sb);
				StringBuilder sbS= new StringBuilder(17);
				for (int i = 0; i < mac.length; i++) {
					sbS.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));		
				}
				
				return sbS.toString();
			} catch (UnknownHostException e1) {
				System.err.println("cannot collect host address");
				return null;
			}
			
			
		}
	}
	
	public byte[] macConvert(String macAddress){
		
		String[] macAddressParts = macAddress.split("-");
		byte[] macAddressBytes = new byte[6];

        JCL_handler.macConvertDuplicate(macAddress, macAddressParts, macAddressBytes);
        return macAddressBytes;
	}
	
	private static InetAddress getLocalHostLANAddress() throws UnknownHostException {
		return getInetAddressDuplicate(nic);
	}
}
