package implementations.dm_kernel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import commom.GenericConsumer;
import commom.GenericResource;
import commom.JCL_acceptor;
import commom.JCL_handler;

public abstract class ServerUDP {
	
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
//	private final ExecutorService executor = Executors.newFixedThreadPool(2);
	protected final List<GenericConsumer<JCL_handler>> serverThreads = new ArrayList<GenericConsumer<JCL_handler>>();		
	protected List<AtomicBoolean> killWorkers = new ArrayList<AtomicBoolean>();
	protected final GenericResource<JCL_handler> serverR;	
	protected final int port;
	protected final Selector selector;
//	protected final ReentrantLock selectorLock;
//	protected final List<Selector> selectorRead;
//	protected final List<ReentrantLock> selectorReadLock;
	protected final ServerSocketChannel serverSocket;
	protected final long initialTime;
	
//	private int numOfThreads;
	
	public ServerUDP(int port) throws IOException{
				
		this.serverSocket = ServerSocketChannel.open();
		this.initialTime = System.nanoTime();		
		this.port = port;
//		this.selectorRead = new ArrayList<Selector>(); 
//		this.selectorReadLock = new ArrayList<ReentrantLock>();
//		this.numOfThreads = CoresAutodetect.cores;
		
		
//		this.serverThreads = new GenericConsumer[this.numOfThreads];
		this.selector = Selector.open();
//		this.selectorLock = new ReentrantLock();
//		selectorRead.add(this.selector);
//		selectorReadLock.add(this.selectorLock);
		
		this.serverR = new GenericResource<JCL_handler>(); 
//		JCL_handler.setResource(this.serverR);
		
//		for(int i = 0 ;i<(this.numOfThreads/2);i++){
//			Selector sel = Selector.open();
//			selectorRead.add(sel);
//			ReentrantLock lock = new ReentrantLock();
//			selectorReadLock.add(lock);
//			ServerAux SAux = new ServerAux(serverR, sel, lock);
//			SAux.start();
//		}

	}
	
	public void begin(){
		try{
			
//			JCL_Crawler<JCL_handler> crawler = new JCL_Crawler<JCL_handler>(CoresAutodetect.cores,serverThreads,killWorkers,serverR,this);			
//			scheduler.scheduleAtFixedRate(crawler,0,20000,TimeUnit.MILLISECONDS);															

			//start listening 			
			listen();
		}catch (Exception e){
			e.printStackTrace();
		}
	}
	
//	public void begin(List<String> hosts){
//		try{
//			
//			JCL_Crawler<JCL_handler> crawler = new JCL_Crawler<JCL_handler>(CoresAutodetect.cores,serverThreads,killWorkers,serverR,this);			
//			scheduler.scheduleAtFixedRate(crawler,0,10000,TimeUnit.MILLISECONDS);															
//
//			//start listening 			
//			listen();
//		}catch (Exception e){
//			e.printStackTrace();
//		}
//	}
			
	protected void listen(){
		try {
			 openServerSocket();
			 //any other verification can be done here
			 //including send a message to another peer!!!
			 beforeListening();
			
	        while(! this.serverR.isStopped()){
            	        	
//	        	if(this.selector.selectNow()==0){
	        		this.selector.select();
//	        		this.selectorLock.lock();
//		        	this.selectorLock.unlock();
//	        	}
//	        	System.out.println("Nova conex�o!");
	        	Set<SelectionKey> selected = this.selector.selectedKeys();
            	Iterator<SelectionKey> it = selected.iterator();
            	while (it.hasNext()){           			
            			SelectionKey key = it.next();
            			if(key.isValid()){
//            				System.out.println("Run data!");
            				Runnable r = (Runnable)key.attachment();
            				//executor.execute(r);
            				r.run();
            			}
            	}            	
            	selected.clear();
 
	            //any other verification can be done here
	            //including send a message to another peer!!!
	            duringListening();
	        }
	        System.out.println("Server Stopped.") ;
		} catch (IOException e) {
            if(this.serverR.isStopped()) {
                System.out.println("Server Stopped.") ;
                return;
            }
            throw new RuntimeException(
                "Error accepting client connection", e);
        } 
	}	

	
	
    private void openServerSocket() {
        Server.openServerSocketDuplicate(this.serverSocket, this.port, this.selector, this.serverR);
    }
    
    protected GenericResource<JCL_handler> getServerR() {
		return serverR;
	}
    
    public abstract <K extends JCL_handler> GenericConsumer<K> createSocketConsumer( GenericResource<K> r, AtomicBoolean kill);
    protected abstract void beforeListening();
    protected abstract void duringListening();
    
}
