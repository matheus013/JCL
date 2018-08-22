package implementations.dm_kernel.user;

import commom.JCL_resultImpl;
import commom.JCL_taskImpl;
import implementations.collections.JCLFuture;
import implementations.collections.JCLPFuture;
import implementations.dm_kernel.*;
import implementations.dm_kernel.server.RoundRobin;
import implementations.util.*;
import implementations.util.IoT.CryptographyUtils;
import interfaces.kernel.*;
import io.protostuff.LinkedBuffer;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import javassist.ClassPool;
import javassist.CtClass;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JCL_FacadeImplSS extends implementations.sm_kernel.JCL_FacadeImpl.Holder implements JCL_facade {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Map<Integer, Map<String, Map<String, String>>> devices;
    //	private Map<String, Map<String, String>> devicesExec;
    private static List<Map.Entry<String, Map<String, String>>> devicesStorage, devicesExec;
    private JCL_message_list_task msgTask = new MessageListTaskImpl();
    private static ReadWriteLock lock = new ReentrantReadWriteLock();
    private Set<String> registerClass = new HashSet<String>();
    private static ConcurrentMap<String, JCL_message_register> jars;
    private static ConcurrentMap<String, List<String>> jarsSlaves;
    //	private static ConcurrentMap<String,String[]> slaves;
    private boolean watchExecMeth = true;
    private static JCL_facade instance;
    private SimpleServer simpleSever;
    //	private static List<String> slavesIDs;
    private static XORShiftRandom rand;
    private boolean JPF = true;
    public static String serverAdd;
    private int watchdog = 0;
    private int JPBsize = 50;
    private static JCL_facade jcl;
    public static int serverPort, serverSPort;
    private static int delta;
    private int port;

    private JCL_result apply(Future<JCL_result> j) {
        try {
            return j.get();
        } catch (InterruptedException | ExecutionException e) {
            System.err
                    .println("problem in JCL facade getAllResultBlocking(List<String> ID)");
            e.printStackTrace();
        }
        return null;
    }

    private void init(Properties properties) {
        JPF = Boolean.valueOf(properties.getProperty("enablePBA"));
        JPBsize = Integer.parseInt(properties.getProperty("PBAsize"));
        delta = Integer.parseInt(properties.getProperty("delta"));

        serverAdd = properties.getProperty("serverMainAdd");
        serverPort = Integer.parseInt(properties.getProperty("serverMainPort"));
        serverSPort = Integer.parseInt(properties.getProperty("superPeerMainPort"));
        int timeOut = Integer.parseInt(properties.getProperty("timeOut"));
        this.port = Integer.parseInt(properties.getProperty("simpleServerPort"));
        jars = new ConcurrentHashMap<String, JCL_message_register>();
        jarsSlaves = new ConcurrentHashMap<String, List<String>>();
        jcl = super.getInstance();
        //Start seed rand GV
        rand = new XORShiftRandom();
        //config connection
        ConnectorImpl.timeout = timeOut;
    }

    protected JCL_FacadeImplSS(Properties properties) {
        try {
            //single pattern
            if (instance == null) {
                instance = this;
            }
            boolean DA = Boolean.valueOf(properties.getProperty("enableDinamicUp"));
            init(properties);

            JCL_connector controlConnector = new ConnectorImpl(false);

            if (controlConnector.connect(serverAdd, serverPort, null)) {
                controlConnector.disconnect();
            } else {
                String serverData[] = ServerDiscovery.discoverServer();
                if (serverData != null) {
                    serverAdd = serverData[0];
                    serverPort = Integer.parseInt(serverData[1]);
                    controlConnector.connect(serverAdd, serverPort, null);
                }
            }

            //ini jcl lambari
            jcl.register(JCL_FacadeImplLamb.class, "JCL_FacadeImplLamb");

            // scheduler flush in execute
            if (JPF) {
                scheduler.scheduleAtFixedRate(
                        () -> {
                            try {
                                //watchdog end bin exec
                                if ((watchdog != 0) && (watchdog == msgTask.taskSize()) && (watchExecMeth)) {
                                    //Get host
                                    //Init RoundRobin
                                    DeviceProperty deviceProperty = new DeviceProperty(RoundRobin.getDevice());

                                    //Register missing class
                                    for (String classReg : registerClass) {
                                        if (!jarsSlaves.get(classReg).contains(deviceProperty.sumAll())) {
                                            Object[] argsLam = {deviceProperty.host(), deviceProperty.port(), deviceProperty.mac(), deviceProperty.portS(), jars.get(classReg)};
                                            Future<JCL_result> ti = jcl.execute("JCL_FacadeImplLamb", "register", argsLam);
                                            ti.get();
                                            //									jcl.getResultBlocking(ti);
                                            jarsSlaves.get(classReg).add(deviceProperty.sumAll());
                                        }
                                    }

                                    //Send to host task bin
                                    Object[] argsLam = {deviceProperty.host(), deviceProperty.port(), deviceProperty.mac(), deviceProperty.portS(), msgTask};
                                    jcl.execute("JCL_FacadeImplLamb", "binexecutetask", argsLam);
                                    msgTask = new MessageListTaskImpl();
                                } else {
                                    //update watchdog
                                    watchdog = msgTask.taskSize();
                                }

                            } catch (Exception e) {
                                // TODO Auto-generated catch block
                                System.err.println("JCL facade watchdog error");
                                e.printStackTrace();
                            }
                        }, 0, 5, TimeUnit.SECONDS);
            }

            //Start simple server
            if (DA) {
                simpleSever = new SimpleServer(this.port, devices, lock);
                simpleSever.start();
            }

            //getHosts using lambari
            int type = 5;

            //Get devices thar compose the cluster

            this.update();

            RoundRobin.ini(devicesExec);

            //finish
            System.out.println("client JCL is OK");


        } catch (Exception e) {
            System.err.println("JCL facade constructor error");
            e.printStackTrace();
        }
    }

    private void update() {
        try {
            JCL_message_generic mgh = (JCL_message_generic) this.getSlaveIds(serverAdd, serverPort, serverSPort, 3);

            Object obj[] = (Object[]) mgh.getRegisterData();
            devices = (Map<Integer, Map<String, Map<String, String>>>) obj[0];

            //Init RoundRobin
            devicesExec = new ArrayList<>();
            devicesStorage = new ArrayList<>();

            devicesExec.addAll(devices.get(2).entrySet());
            devicesExec.addAll(devices.get(3).entrySet());
            devicesExec.addAll(devices.get(6).entrySet());
            devicesExec.addAll(devices.get(7).entrySet());

            devicesStorage.addAll(devices.get(1).entrySet());
            devicesStorage.addAll(devices.get(3).entrySet());
            devicesStorage.addAll(devices.get(5).entrySet());
            devicesStorage.addAll(devices.get(7).entrySet());


            // Sorting
            Comparator com = (Comparator<Map.Entry<String, Map<String, String>>>) (entry2, entry1) -> entry1.getKey().compareTo(entry2.getKey());

            Collections.sort(devicesExec, com);
            Collections.sort(devicesStorage, com);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JCL_message getSlaveIds(String serverAdd, int serverPort, int serverSPort, int deviceType) {

        try {
            //Get a list of hosts
            //this.port = port;
            JCL_message_generic mc = new MessageGenericImpl();
            mc.setType(42);
            mc.setRegisterData(deviceType);
            boolean activateEncryption = false;
            if (ConnectorImpl.encryption) {
                activateEncryption = true;
                ConnectorImpl.encryption = false;
            }
            JCL_connector controlConnector = new ConnectorImpl(false);
            if (!controlConnector.connect(serverAdd, serverPort, null)) {
                JCL_FacadeImplSS.serverPort = JCL_FacadeImplSS.serverSPort;
                controlConnector.connect(serverAdd, serverSPort, null);
            }

            JCL_message mr = controlConnector.sendReceiveG(mc, null);
            JCL_message_generic mg = (MessageGenericImpl) mr;
            Object obj[] = (Object[]) mg.getRegisterData();
            CryptographyUtils.setClusterPassword(obj[1] + "");
            controlConnector.disconnect();

            ConnectorImpl.encryption = activateEncryption;

            return mr;

        } catch (Exception e) {
            System.err.println("problem in JCL facade getSlaveIds()");
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean register(Class<?> serviceClass, String classToBeExecuted) {
        try {
            // Local register
            ClassPool pool = ClassPool.getDefault();
            CtClass cc = pool.get(serviceClass.getName());
            JCL_message_register msg = new MessageRegisterImpl();
            byte[][] cb = new byte[1][];
            cb[0] = cc.toBytecode();
            msg.setJars(cb);
            msg.setJarsNames(new String[]{cc.getName()});
            msg.setClassName(classToBeExecuted);
            msg.setType(3);

            return register(msg, classToBeExecuted);

        } catch (Exception e) {

            System.err
                    .println("problem in JCL facade register(Class<?> serviceClass,String classToBeExecuted)");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean register(File[] f, String classToBeExecuted) {
        try {

            // Local register
            JCL_message_register msg = new MessageRegisterImpl();
            msg.setJars(f);
            msg.setJarsNames(f);
            msg.setClassName(classToBeExecuted);
            msg.setType(1);

            return register(msg, classToBeExecuted);
        } catch (Exception e) {

            System.err
                    .println("problem in JCL facade register(File f, String classToBeExecuted)");
            e.printStackTrace();
            return false;
        }
    }

    private boolean register(JCL_message_register msg, String classToBeExecuted) throws ExecutionException, InterruptedException {
        Object[] argsLam = {serverAdd, String.valueOf(serverPort), null, "0", msg};
        Future<JCL_result> t = jcl.execute("JCL_FacadeImplLamb", "register", argsLam);

        if ((Boolean) t.get().getCorrectResult()) {
            jars.put(classToBeExecuted, msg);
            jarsSlaves.put(classToBeExecuted, new ArrayList<>());

            return true;
        }
        return false;

    }

    @Override
    public boolean unRegister(String nickName) {
        boolean result = true;
        try {
            //List host
            for (Map.Entry<String, Map<String, String>> oneHostPort : devicesExec) {
                DeviceProperty deviceProperty = new DeviceProperty(oneHostPort);
                if (jarsSlaves.get(nickName).contains(deviceProperty.sumAll())) {
                    // UnRegister using lambari on host
                    Object[] argsLam = {nickName, deviceProperty.host(), deviceProperty.port(), deviceProperty.mac()};
                    Future<JCL_result> t = jcl.execute("JCL_FacadeImplLamb", "unRegister", argsLam);
                    if ((t.get()).getCorrectResult() != null) {
                        jarsSlaves.get(nickName).remove(deviceProperty.sumAll());
                    } else {
                        result = false;
                    }
                }
            }
            //remove class
            jars.remove(nickName);
            jarsSlaves.remove(nickName);
            return result;

        } catch (Exception e) {
            System.err.println("JCL problem in unRegister(String nickName) method");
            e.printStackTrace();

            return false;
        }
    }

    @Override
    public Future<JCL_result> execute(String objectNickname, Object... args) {
        DeviceProperty deviceProperty;

        try {
            if (!JPF) {
                //Get host

                return executeInNotJPF(objectNickname, null, args);
            } else {
                //watch this method
                watchExecMeth = false;

                //Create bin task message
                JCL_task t = new JCL_taskImpl(null, objectNickname, args);
                Long ticket = super.createTicketH();
                t.setPort(this.port);
                msgTask.addTask(ticket, t);
                registerClass.add(objectNickname);

                executeInJPF();
                //watch this method
                watchExecMeth = true;

                return new JCLFuture<JCL_result>(ticket);
            }
        } catch (Exception e) {
            System.err
                    .println("JCL facade Pacu problem in execute(String className, Object... args)");
            e.printStackTrace();
            return null;
        }
    }

    private void executeInJPF() throws InterruptedException, ExecutionException {
        DeviceProperty deviceProperty;
        if (this.msgTask.taskSize() == (JPBsize * RoundRobin.core)) {
            deviceProperty = new DeviceProperty(RoundRobin.getDevice());

            //Register bin task class
            for (String classReg : registerClass) {
                if (!jarsSlaves.get(classReg).contains(deviceProperty.sumAll())) {
                    Object[] argsLam = {deviceProperty.host(), deviceProperty.port(),
                            deviceProperty.mac(), deviceProperty.portS(), jars.get(classReg)};
                    Future<JCL_result> ti = jcl.execute("JCL_FacadeImplLamb", "register", argsLam);
                    ti.get();
                    jarsSlaves.get(classReg).add(deviceProperty.sumAll());
                }
            }

            //execute lambari
            Object[] argsLam = {deviceProperty.host(), deviceProperty.port(),
                    deviceProperty.mac(), deviceProperty.portS(), this.msgTask};
            jcl.execute("JCL_FacadeImplLamb", "binexecutetask", argsLam);
            msgTask = new MessageListTaskImpl();
        }
    }


    private Future<JCL_result> executeInNotJPF(String objectNickname, String methodName, Object... args) throws ExecutionException, InterruptedException {
        DeviceProperty deviceProperty;
        if (jars.containsKey(objectNickname)) {
            // Get host
            deviceProperty = new DeviceProperty(RoundRobin.getDevice());
        } else {

            Object[] argsLam = {serverAdd, String.valueOf(serverPort), null, null, objectNickname};
            Future<JCL_result> ticket = jcl.execute("JCL_FacadeImplLamb", "registerByServer", argsLam);

            deviceProperty = new DeviceProperty((Map<String, String>) ticket.get().getCorrectResult());

            if (deviceProperty.getSize() == 0) {
                System.err.println("No class Found!!!");
            }

            List<String> js = new ArrayList<String>();
            js.add(deviceProperty.sumAll());
            jarsSlaves.put(objectNickname, js);

        }

        //Test if host contain jar
        if (jarsSlaves.get(objectNickname).contains(deviceProperty.sumAll())) {
            // Just exec
            Object[] argsLam;
            if (methodName == null)
                argsLam = new Object[]{objectNickname, deviceProperty.host(), deviceProperty.port(),
                        deviceProperty.mac(), deviceProperty.portS(), Boolean.TRUE, args};
            else
                argsLam = new Object[]{objectNickname, methodName, deviceProperty.host(), deviceProperty.port(),
                        deviceProperty.mac(), deviceProperty.portS(), Boolean.TRUE, args};
            return super.execute("JCL_FacadeImplLamb", "execute", argsLam);
        } else {
            //Exec and register
            Object[] argsLam = {objectNickname, methodName, deviceProperty.host(), deviceProperty.port(),
                    deviceProperty.mac(), deviceProperty.portS(), jars.get(objectNickname), Boolean.TRUE, args};
            jarsSlaves.get(objectNickname).add(deviceProperty.sumAll());
            return super.execute("JCL_FacadeImplLamb", "executeAndRegister", argsLam);
        }
    }

    @Override
    public Future<JCL_result> execute(String objectNickname, String methodName, Object... args) {
        DeviceProperty deviceProperty;

        try {
            if (!JPF) {
                //Get host

                return executeInNotJPF(objectNickname, methodName, args);
            } else {
                //watch this method
                watchExecMeth = false;

                //Create bin task message
                JCL_task t = new JCL_taskImpl(null, objectNickname, methodName, args);
                Long ticket = super.createTicketH();
                t.setPort(this.port);
                msgTask.addTask(ticket, t);
                registerClass.add(objectNickname);

                //Send bin task
                executeInJPF();
                //watch this method
                watchExecMeth = true;

                return new JCLFuture<>(ticket);
            }

        } catch (Exception e) {
            System.err
                    .println("JCL facade problem in execute(String className, String methodName, Object... args)");

            return null;
        }
    }

    @Override
    public List<JCL_result> getAllResultBlocking(List<Future<JCL_result>> ID) {

        return ID.stream().map(this::apply).collect(Collectors.toList());

    }

    @Override
    public List<JCL_result> getAllResultUnblocking(List<Future<JCL_result>> ID) {
        //Vars
        List<JCL_result> resultF;
        List<Future<JCL_result>> Ids;

        Ids = ID.stream().map(t -> jcl.execute("JCL_FacadeImplLamb", "getResultUnblocking", applyIDs(t))).collect(Collectors.toList());
        resultF = Ids.stream().map(this::applyNull).collect(Collectors.toList());

        return resultF;
    }

    private Object[] applyIDs(Future<JCL_result> t) {
        JCL_result id = null;
        try {
            id = t.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.err
                    .println("problem in JCL facade getAllResultUnblocking(String ID) applayIDs");
        }
        Object[] res = (Object[]) id.getCorrectResult();
        return new Object[]{((JCLFuture) t).getTicket(), res[0], res[1], res[2], res[3], res[4]};

    }

    private JCL_result applyNull(Future<JCL_result> j) {
        try {
            if (j.get().getCorrectResult().equals("NULL")) j.get().setCorrectResult(null);
            return j.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.err
                    .println("problem in JCL facade getAllResultUnblocking(String ID) applyNull");
        }
        return null;
    }

    @Override
    public JCL_result removeResult(Future<JCL_result> ID) {
        try {
            //getResultUnblocking using lambari
            Long ticket = ((JCLPFuture) ID).getTicket();

            Object[] res = (Object[]) super.getResultBlocking(ticket).getCorrectResult();
            Object[] arg = {ticket, res[0], res[1], res[2], res[3], res[4]};
            Future<JCL_result> t = jcl.execute("JCL_FacadeImplLamb", "removeResult", arg);

            super.removeResult(ticket);
            return t.get();

        } catch (Exception e) {
            System.err
                    .println("problem in JCL facade removeResult(Future<JCL_result> ID)");
            JCL_result jclr = new JCL_resultImpl();
            jclr.setErrorResult(e);
            e.printStackTrace();

            return jclr;
        }
    }

    @Override
    public boolean instantiateGlobalVar(Object key, String nickName, File[] jar, Object[] args) {
        return instantiateGlobalVarOnDevice(null, key, nickName, jar, args);
    }

    static void jarsSlavesValidationDuplicate(File[] jar, String nickName, ConcurrentMap<String, List<String>> jarsSlaves, ConcurrentMap<String, JCL_message_register> jars) {
        if (!jarsSlaves.containsKey(nickName)) {
            // Local register
            JCL_message_register msg = new MessageRegisterImpl();
            msg.setJars(jar);
            msg.setJarsNames(jar);
            msg.setClassName(nickName);
            msg.setType(1);
            jars.put(nickName, msg);
            jarsSlaves.put(nickName, new ArrayList<>());
        }
    }

    @Override
    public boolean instantiateGlobalVar(Object key, Object instance) {
        return instantiateGlobalVarOnDevice(null, key, instance);
    }

    protected static boolean instantiateGlobalVar(Set<Map.Entry<?, ?>> set, String gvname) {
        lock.readLock().lock();
        try {
            Map<Integer, JCL_message_list_global_var> gvList = new HashMap<>();
            Schema<ObjectWrap> scow = RuntimeSchema.getSchema(ObjectWrap.class);
            LinkedBuffer buffer = LinkedBuffer.allocate(1048576);

            //Create bin of global vars
            for (Map.Entry<?, ?> ent : set) {
                Object key = (ent.getKey().toString() + "�Map�" + gvname);
                Object value = ent.getValue();
                int hostId = rand.nextInt(0, key.hashCode(), devicesStorage.size());

                SerializeWrapSup serializeWrapSup = new SerializeWrapSup(key, value);
                serializeWrapSup.magic();

                value = serializeWrapSup.value();
                key = serializeWrapSup.key();


                if (gvList.containsKey(hostId)) {
                    JCL_message_list_global_var gvm = gvList.get(hostId);
                    gvm.putVarKeyInstance(key, value);
                } else {
                    JCL_message_list_global_var gvm = new MessageListGlobalVarImpl(key, value);
                    gvm.setType(35);
                    gvList.put(hostId, gvm);
                }
            }

            List<Future<JCL_result>> tick = new ArrayList<>();

            //Create on host using lambari
            //Get Host
            //instantiateGlobalVar using lambari
            gvList.entrySet().forEach(ent -> {
                Integer key = ent.getKey();
                JCL_message_list_global_var value = ent.getValue();

                DeviceProperty deviceProperty = new DeviceProperty(devicesStorage.get(key));
                Object[] argsLam = {deviceProperty.host(), deviceProperty.port(),
                        deviceProperty.mac(), deviceProperty.portS(), value, key};
                tick.add(jcl.execute("JCL_FacadeImplLamb", "instantiateGlobalVar", argsLam));
            });

            List<JCL_result> result = jcl.getAllResultBlocking(tick);

            return result.stream().allMatch(res -> (Boolean) res.getCorrectResult());
        } catch (Exception e) {
            System.err
                    .println("problem in JCL facade instantiateGlobalVar JCLHashMap.");
            e.printStackTrace();
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean deleteGlobalVar(Object key) {
        return false;
    }

    @Override
    public boolean setValueUnlocking(Object key, Object value) {
        return false;
    }

    @Override
    public JCL_result getValue(Object key) {
        return null;
    }

    @Override
    public JCL_result getValueLocking(Object key) {
        return null;
    }

    @Override
    public void destroy() {

    }

    @Override
    public boolean containsTask(String nickName) {
        return false;
    }

    @Override
    public boolean containsGlobalVar(Object key) {
        return false;
    }

    @Override
    public boolean isLock(Object key) {
        return false;
    }

    @Override
    public boolean cleanEnvironment() {
        return false;
    }

    private List<Future<JCL_result>> executeAll(String objectNickname, String methodName, Object args, Boolean bidimensinalArgs) {
        List<Map.Entry<String, String>> hosts;
        List<Future<JCL_result>> tickets = new ArrayList<>();
        Object[] singleArgs = new Object[0];
        Object[][] bidimArgs = new Object[0][];
        if (bidimensinalArgs)
            bidimArgs = (Object[][]) args;
        else
            singleArgs = (Object[]) args;
        try {

            //get all host
            int[] d = {2, 3, 6, 7};
            hosts = this.getDevices(d);

            //Exec in all host
            for (int i = 0; i < hosts.size(); i++) {
                tickets.add(this.executeOnDevice(hosts.get(i), objectNickname, methodName, (bidimensinalArgs) ? bidimArgs[i] : singleArgs));
            }

            return tickets;
        } catch (Exception e) {
            if (bidimensinalArgs)
                System.err
                        .println("JCL facade problem in executeAll(String objectNickname, Object[][] args)");
            else
                System.err
                        .println("JCL facade problem in executeAll(String className, Object... args)");
            return null;
        }
    }

    @Override
    public List<Future<JCL_result>> executeAll(String objectNickname, Object... args) {
        return executeAll(objectNickname, null, args, false);
    }

    @Override
    public List<Future<JCL_result>> executeAll(String objectNickname, Object[][] args) {
        return executeAll(objectNickname, null, args, true);
    }

    @Override
    public List<Future<JCL_result>> executeAllCores(String objectNickname, String methodName, Object... args) {
        List<Future<JCL_result>> tickets;
        List<Map.Entry<String, String>> hosts;
        tickets = new ArrayList<Future<JCL_result>>();
        try {
            //get all host
            int[] d = {2, 3, 6, 7};
            hosts = this.getDevices(d);

            //Exec in all host
            //Execute o same host all cores
            hosts.forEach(device -> {
                int core = this.getDeviceCore(device);
                IntStream.range(0, core).mapToObj(j -> this.executeOnDevice(device, objectNickname, methodName, args)).forEach(tickets::add);
            });

            return tickets;
        } catch (Exception e) {
            System.err
                    .println("JCL facade problem in executeAllCores(String objectNickname,String methodName, Object... args)");
            return null;
        }
    }

    @Override
    public List<Future<JCL_result>> executeAllCores(String objectNickname, Object... args) {
        return executeAllCores(objectNickname, null, args);
    }

    @Override
    public List<Future<JCL_result>> executeAllCores(String objectNickname, Object[][] args) {
        return executeAllCores(objectNickname, null, args);
    }

    @Override
    public List<Future<JCL_result>> executeAll(String objectNickname, String methodName, Object... args) {
        return executeAll(objectNickname, methodName, args, false);

    }

    @Override
    public List<Future<JCL_result>> executeAllCores(String objectNickname, String methodName, Object[][] args) {
        List<Future<JCL_result>> tickets = new ArrayList<>();
        int[] d = {2, 3, 6, 7};
        List<Map.Entry<String, String>> hosts = this.getDevices(d);
        try {
            //Exec in all host
            int cont = 0;
            for (Map.Entry<String, String> device : hosts) {
                //Execute o same host all cores
                int core = this.getDeviceCore(device);
                for (int j = 0; j < core; j++) {
                    tickets.add(this.executeOnDevice(device, objectNickname, methodName, args[cont]));
                    ++cont;
                }
            }

            return tickets;
        } catch (Exception e) {
            System.err
                    .println("JCL facade problem in executeAllCores(String objectNickname,String methodName, Object[][] args)");
            return null;
        }
    }

    @Override
    public List<Future<JCL_result>> executeAll(String objectNickname, String methodName, Object[][] args) {
        return executeAll(objectNickname, methodName, args, true);
    }

    @Override
    public <T extends Map.Entry<String, String>> List<T> getDevices() {
        return null;
    }

    @Override
    public int getDeviceCore(Map.Entry<String, String> device) {
        return 0;
    }

    @Override
    public <T extends Map.Entry<String, String>> Map<T, Integer> getAllDevicesCores() {
        return null;
    }

    @Override
    public int getClusterCores() {
        return 0;
    }

    @Override
    public boolean instantiateGlobalVarOnDevice(Map.Entry<String, String> device, Object key, String className, File[] jar,
                                                Object[] args) {
        if (containsGlobalVar(key) || key == null) return false;
        try {
            Map<String, String> hostPort = null;
            DeviceProperty deviceProperty;

            if (device == null) {
                int hostId = rand.nextInt(0, key.hashCode(), devicesStorage.size());
                deviceProperty = new DeviceProperty(devicesStorage.get(hostId));
            } else {
                deviceProperty = new DeviceProperty(this.getDeviceMetadata(device));
                hostPort = this.getDeviceMetadata(device);
            }


            JCL_FacadeImplSS.jarsSlavesValidationDuplicate(jar, className, jarsSlaves, jars);

            if (device == null) {
                Object[] argsLam = new Object[]{hostPort, key, serverAdd, serverPort};
                Future<JCL_result> tS = jcl.execute("JCL_FacadeImplLamb", "instantiateGlobalVarOnHost", argsLam);

                if (!(boolean) tS.get().getCorrectResult()) {
                    System.err
                            .println("problem in JCL facade instantiateGlobalVarOnHost(String host, String nickName, String varName, File[] jars, Object[] defaultVarValue)");
                    System.err
                            .println("Erro in Server Register!!!!");
                    return false;
                }
            }

            if (jarsSlaves.get(className).contains(deviceProperty.sumAll())) {
                //instantiateGlobalVar using lambari
                Object[] argsLam = {key, className, args, deviceProperty.host(), deviceProperty.port(),
                        deviceProperty.mac(), deviceProperty.portS(), 0};
                Future<JCL_result> t = jcl.execute("JCL_FacadeImplLamb", "instantiateGlobalVar", argsLam);
                return (Boolean) (t.get()).getCorrectResult();
            } else {
                //instantiateGlobalVar using lambari
                Object[] argsLam = {key, className, jars.get(className), args, deviceProperty.host(), deviceProperty.port(),
                        deviceProperty.mac(), deviceProperty.portS(), 0};
                Future<JCL_result> t = jcl.execute("JCL_FacadeImplLamb", "instantiateGlobalVarAndReg", argsLam);
                jarsSlaves.get(className).add(deviceProperty.sumAll());
                return (Boolean) (t.get()).getCorrectResult();
            }

        } catch (Exception e) {
            System.err
                    .println("problem in JCL facade instantiateGlobalVarOnHost(String host, String nickName, String varName, File[] jars, Object[] defaultVarValue)");
            return false;
        }
    }

    @Override
    public boolean instantiateGlobalVarOnDevice(Map.Entry<String, String> device, Object key, Object instance) {
        lock.readLock().lock();
        int hostId = 0;
        if (device != null)
            for (int i = 0; i < devicesStorage.size(); i++) {
                if (devicesStorage.get(i).getKey().equals(device.getKey())) hostId = i;
            }
        else
            hostId = rand.nextInt(delta, key.hashCode(), devicesStorage.size());

        Map.Entry<String, Map<String, String>> deviceI = devicesStorage.get(hostId);
        Object[] argsLamS = {deviceI.getValue(), key, serverAdd, serverPort};

        if (device != null) {
            Future<JCL_result> tS = jcl.execute("JCL_FacadeImplLamb", "instantiateGlobalVarOnHost", argsLamS);

            try {
                if (!(boolean) tS.get().getCorrectResult()) {
                    System.err
                            .println("problem in JCL facade instantiateGlobalVarOnHost(String host, String nickName, String varName, File[] jars, Object[] defaultVarValue)");
                    System.err
                            .println("Erro in Server Register!!!!");
                    return false;
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

        }
        DeviceProperty deviceProperty = new DeviceProperty(deviceI);
        Object[] argsLam = {key, instance, deviceProperty.host(), deviceProperty.port(),
                deviceProperty.mac(), deviceProperty.portS(), 0};
        Future<JCL_result> t = jcl.execute("JCL_FacadeImplLamb", "instantiateGlobalVar", argsLam);
        try {
            return (Boolean) (t.get()).getCorrectResult();
        } catch (InterruptedException | ExecutionException e) {
            System.err
                    .println("problem in JCL facade instantiateGlobalVar(Object key, Object instance)");
            e.printStackTrace();
        } finally {
            lock.readLock().unlock();
        }
        return false;
    }

    @Override
    public Future<Boolean> instantiateGlobalVarAsy(Object key, Object instance) {
        return null;
    }

    @Override
    public Future<Boolean> instantiateGlobalVarAsy(Object key, String className, File[] jars, Object[] args) {
        return null;
    }

    @Override
    public Future<JCL_result> executeOnDevice(Map.Entry<String, String> device, String objectNickname, String
            methodName, Object... args) {
        DeviceProperty deviceProperty;
        try {
            if (jars.containsKey(objectNickname)) {
                // Get host

                deviceProperty = new DeviceProperty(this.getDeviceMetadata(device));
            } else {

                Object[] argsLam = {serverAdd, String.valueOf(serverPort), device.getKey(), device.getValue(), objectNickname};
                Future<JCL_result> ticket = jcl.execute("JCL_FacadeImplLamb", "registerByServer", argsLam);

                deviceProperty = new DeviceProperty((Map<String, String>) ticket.get().getCorrectResult());

                if (deviceProperty.getSize() == 0) {
                    System.err.println("No class Found!!!");
                }


                List<String> js = new ArrayList<String>();
                js.add(deviceProperty.sumAll());
                jarsSlaves.put(objectNickname, js);
            }

            //Test if host contain jar
            if (jarsSlaves.get(objectNickname).contains(deviceProperty.sumAll())) {
                //Just exec
                Object[] argsLam;
                if (methodName == null)
                    argsLam = new Object[]{objectNickname, deviceProperty.host(), deviceProperty.port(),
                            deviceProperty.mac(), deviceProperty.portS(), Boolean.FALSE, args};
                else
                    argsLam = new Object[]{objectNickname, methodName, deviceProperty.host(), deviceProperty.port(),
                            deviceProperty.mac(), deviceProperty.portS(), Boolean.FALSE, args};
                return super.execute("JCL_FacadeImplLamb", "execute", argsLam);
            } else {

                //Exec and register
                Object[] argsLam;
                if (methodName == null)
                    argsLam = new Object[]{objectNickname, deviceProperty.host(), deviceProperty.port(),
                            deviceProperty.mac(), deviceProperty.portS(), jars.get(objectNickname), Boolean.FALSE, args};
                else
                    argsLam = new Object[]{objectNickname, methodName, deviceProperty.host(), deviceProperty.port(),
                            deviceProperty.mac(), deviceProperty.portS(), jars.get(objectNickname), Boolean.FALSE, args};
                jarsSlaves.get(objectNickname).add(deviceProperty.sumAll());
                return super.execute("JCL_FacadeImplLamb", "executeAndRegister", argsLam);
            }

        } catch (Exception e) {
            System.err
                    .println("JCL facade problem in executeOnDevice(String host,String className, String methodName, Object... args)");
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Future<JCL_result> executeOnDevice(Map.Entry<String, String> device, String registerClass, Object...
            args) {
        return this.executeOnDevice(device, registerClass, null, args);
    }

    @Override
    public Map<String, String> getDeviceMetadata(Map.Entry<String, String> device) {
        try {
            for (Map<String, Map<String, String>> ids : devices.values()) {
                for (Map.Entry<String, Map<String, String>> d : ids.entrySet()) {
                    if (d.getKey().equals(device.getKey()))
                        return d.getValue();
                }
            }

            System.err.println("Device not found!!!");
            return null;

        } catch (Exception e) {
            System.err.println("problem in JCL facade getHosts()");
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean setDeviceMetadata(Map.Entry<String, String> device, Map<String, String> metadata) {
        try {
            return devices.values().stream().flatMap(ids -> ids.entrySet().stream()).anyMatch(d -> d.getKey().equals(device.getKey()));
        } catch (Exception e) {
            System.err.println("problem in JCL facade getHosts()");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Map<String, String> getDeviceConfig(Map.Entry<String, String> deviceNickname) {
        return null;
    }

    @Override
    public boolean setDeviceConfig(Map.Entry<String, String> deviceNickname, Map<String, String> metadata) {
        return false;
    }

    public List<Map.Entry<String, String>> getDevices(int type[]) {
        try {
            //getHosts

            List<Map.Entry<String, String>> result = new ArrayList<>();
            Arrays.stream(type).forEach(ids -> devices.get(ids).forEach((key, value) -> result.add(new Entry<>(key, value.get("DEVICE_ID")))));
            return result;

        } catch (Exception e) {
            System.err.println("problem in JCL facade getHosts()");
            e.printStackTrace();
            return null;
        }
    }
}
