/*
* @(#)FullThreadDump.java 1.5 05/11/17
*
* Copyright (c) 2006 Sun Microsystems, Inc. All Rights Reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* -Redistribution of source code must retain the above copyright notice, this
* list of conditions and the following disclaimer.
*
* -Redistribution in binary form must reproduce the above copyright notice,
* this list of conditions and the following disclaimer in the documentation
* and/or other materials provided with the distribution.
*
* Neither the name of Sun Microsystems, Inc. or the names of contributors may
* be used to endorse or promote products derived from this software without
* specific prior written permission.
*
* This software is provided "AS IS," without a warranty of any kind. ALL
* EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
* ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
* OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MIDROSYSTEMS, INC. ("SUN")
* AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
* AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
* DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
* REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
* INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
* OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
* EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
*
* You acknowledge that this software is not designed, licensed or intended
* for use in the design, construction, operation or maintenance of any
* nuclear facility.
*/
/*
* @(#)FullThreadDump.java 1.5 05/11/17
*/
import static java.lang.management.ManagementFactory.THREAD_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.getThreadMXBean;
import static java.lang.management.ManagementFactory.newPlatformMXBeanProxy;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.apache.commons.cli.*;
/*
* This FullThreadDump class connects to a JMX agent, creates a ThreadMonitor class, and creates a threadDump
*/
public class fullThreadDump {
private MBeanServerConnection server;
private JMXConnector jmxConnector;
private JMXServiceURL serviceURL;
private boolean stats, jbossRemotingJMX;
public fullThreadDump(boolean jbossRemotingJMX, String hostname, String user, String passwd, boolean stats) {
	connect(jbossRemotingJMX, hostname, user, passwd);
	this.stats = stats;
	this.jbossRemotingJMX = jbossRemotingJMX;
}
public void dump(String file) throws IOException {
	ThreadMonitor monitor = new ThreadMonitor(server, stats);
	writeDump(file, monitor.threadDump());
	disconnect();
}
public void dump() throws IOException {
	ThreadMonitor monitor = new ThreadMonitor(server, stats);
	System.out.print(monitor.threadDump());
	disconnect();
}
private void disconnect() throws IOException{
	jmxConnector.close();
}
/*
* Connect to a JMX agent of a given URL.
*/
private void connect(boolean jbossRemotingJMX, String hostname, String user, String passwd) {
	String urlString;
	try {
		HashMap<String,String[]> env = new HashMap<String,String[]>();
		String[] creds = new String[2];
		creds[0] = user;
		creds[1] = passwd;
		env.put(JMXConnector.CREDENTIALS, creds);
		if (jbossRemotingJMX){
			urlString ="service:jmx:remoting-jmx://" + hostname;
			this.serviceURL = new JMXServiceURL(urlString);
			System.out.println("\n\nConnecting to "+urlString);
		}
		else { 
			urlString ="/jndi/rmi://" + hostname + "/jmxrmi";
			this.serviceURL = new JMXServiceURL("rmi", "", 0, urlString);
			System.out.println("\n\nConnecting to "+urlString);
		}
	    
	    //this.jmxConnector = JMXConnectorFactory.connect(serviceURL, null);
	    this.jmxConnector = JMXConnectorFactory.connect(serviceURL, env);
	    this.server = jmxConnector.getMBeanServerConnection(); 
	} catch (MalformedURLException e) {
		// should not reach here
	} catch (IOException e) {
		System.err.println("\nCommunication error: " + e.getMessage());
		System.exit(1);
	}
}
public void writeDump(String file, String data) throws IOException {
	// choose our dump-file
	Writer writer = null;
	File dumpFile = null;
	String date = new SimpleDateFormat("MMddyyyy").format(new Date(System.currentTimeMillis()));
	try {
	    File tmp = new File(file);
	    if (tmp.exists() && !tmp.isDirectory() && tmp.canWrite()){
		    writer = new BufferedWriter(new FileWriter(tmp, true));
		    writer.write(data);
	    }
		if (tmp.isDirectory() && tmp.canWrite()){
	    	dumpFile = new File(file + File.separator +"threadDump."+Long.toString(System.currentTimeMillis()));
	    	writer = new BufferedWriter(new FileWriter(dumpFile, true));
		    writer.write(data);
	    } else {
	    	writer = new BufferedWriter(new FileWriter(tmp, true));
		    writer.write(data);
	    }
	} catch (IOException e){
		System.out.print(e);
	} finally {
		writer.close();
	}
}
public static void main(String[] args) throws IOException {
	if (args.length < 2 && ! args.toString().contains("-h")) {
		usage();
	}
		Options options = new Options();
		options.addOption("h", "host", true, "jmx host:port");
		options.addOption("j", "remoting-jmx", false, "jboss use remoting-jmx subsystem");
		options.addOption("u", "user", true, "username");
		options.addOption("p", "password", true, "password");
		options.addOption("s", "stats", false, "include jmx stats in threaddump");
		options.addOption("f", "file", true, "output file");
	    String hostname = null, user=null, passwd=null, file=null;
	    boolean jbossRemotingJMX = false, stats = false;
	    
		CommandLineParser parser = new PosixParser();
		try {
			CommandLine line = parser.parse( options, args);
			if(line.hasOption("h")){
				hostname = line.getOptionValue("h");
			}
			if(line.hasOption("u")){
				user = line.getOptionValue("u");
			}
			if(line.hasOption("p")){
				passwd = line.getOptionValue("p");
			}
			if(line.hasOption("f")){
				file = line.getOptionValue("f");
			}
			if(line.hasOption("s")){
				stats = true;
			}
			if(line.hasOption("j")){
				jbossRemotingJMX = true;
			}
			if(hostname == null){
				System.out.println("Enter jmx Hostname");
			}
		}
		catch( ParseException exp) {
			System.out.println( "Unexpected exception:" + exp.getMessage());
		}
	fullThreadDump ftd = new fullThreadDump(jbossRemotingJMX, hostname, user, passwd, stats);
	if ( file != null){
		ftd.dump(file);
	} else {
		ftd.dump();
	}
}

private static void usage() {
	System.out.println("Description: Create a thread dump using JMX");
	System.out.println("Usage: \tjava -jar tdump.jar -h <hostname>:<jmxPort> [OPTIONS]");
	System.out.println("\nOPTIONS:");
	System.out.println("  -u, --user \n\tusername if security is enabled");
	System.out.println("  -p, --password \n\tpassword if security is enabled");
	System.out.println("  -s, --stats \n\tgather additional statistics on each thread (cpu, wait, and block seconds).");
	System.out.println("  -j, --remoting-jmx \n\tConnect with Jboss remoting-jmx subsystem. (Default: /jndi/rmi://)");
	System.out.println("  -f, --file \n\twrite thread dump to <file>_date.tdump.  You can write to the same file multiple times.");
	System.out.println("Examples:");
	System.out.println("  Jboss(remoting-jmx): java -jar tdump.jar -j -h <hostname>:<port> -u user -p passwd \n");
	System.out.println("  Default(rmi): java -jar tdump.jar -h <hostname>:<port> -u user -p passwd \n");
	System.exit(1);
}
}
/*
* @(#)ThreadMonitor.java 1.6 05/12/22
*
* Copyright (c) 2006 Sun Microsystems, Inc. All Rights Reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* -Redistribution of source code must retain the above copyright notice, this
* list of conditions and the following disclaimer.
*
* -Redistribution in binary form must reproduce the above copyright notice,
* this list of conditions and the following disclaimer in the documentation
* and/or other materials provided with the distribution.
*
* Neither the name of Sun Microsystems, Inc. or the names of contributors may
* be used to endorse or promote products derived from this software without
* specific prior written permission.
*
* This software is provided "AS IS," without a warranty of any kind. ALL
* EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING ANY
* IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
* NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MIDROSYSTEMS, INC. ("SUN") AND ITS
* LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A
* RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
* IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT
* OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR
* PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
* ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
* BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
*
* You acknowledge that this software is not designed, licensed or intended for
* use in the design, construction, operation or maintenance of any nuclear
* facility.
*/
/*
* @(#)ThreadMonitor.java 1.6 05/12/22
*/
/**
* Example of using the java.lang.management API to dump stack trace and to
* perform deadlock detection.
*
* @author Mandy Chung
* @version %% 12/22/05
*/
class ThreadMonitor {
private MBeanServerConnection server;
private ThreadMXBean tmbean;
private ObjectName objname;
private static String INDENT = "\t";
// default - JDK 6+ VM
private String findDeadlocksMethodName = "findDeadlockedThreads";
private boolean canDumpLocks = true, stats = false;
//private boolean canDumpLocks = false;
/**
* Constructs a ThreadMonitor object to get thread information in a remote
* JVM.
 * @throws IOException 
*/
public ThreadMonitor(MBeanServerConnection server, boolean stats) throws IOException {
this.server = server;
this.stats = stats;
this.tmbean = newPlatformMXBeanProxy(server, THREAD_MXBEAN_NAME, ThreadMXBean.class);
	try {
		objname = new ObjectName(THREAD_MXBEAN_NAME);
	} catch (MalformedObjectNameException e) {
		// should not reach here
		InternalError ie = new InternalError(e.getMessage());
		ie.initCause(e);
		throw ie;
	}
	parseMBeanInfo();
}
/**
* Constructs a ThreadMonitor object to get thread information in the local
* JVM.
*/
public ThreadMonitor() {
	this.tmbean = getThreadMXBean();
}
/**
* Prints the thread dump information to System.out.
 * @return 
 * @throws IOException 
*/

public String threadDump() {
	if (canDumpLocks) {
		if (tmbean.isObjectMonitorUsageSupported() && tmbean.isSynchronizerUsageSupported()) {
			// Print lock info if both object monitor usage
			// and synchronizer usage are supported.
			// This sample code can be modified to handle if
			// either monitor usage or synchronizer usage is supported.
			return dumpThreadInfoWithLocks();
		}
	}
	return dumpThreadInfo();
}
private String dumpThreadInfo() {
		String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis()));
		System.out.print(date+" Creating Full thread dump ");
		StringBuilder threadDump = new StringBuilder(date+"\nFull thread dump");
		long[] tids = tmbean.getAllThreadIds();
		ThreadInfo[] tinfos = tmbean.getThreadInfo(tids, Integer.MAX_VALUE);
		for (ThreadInfo ti : tinfos) {
			threadDump.append("\n" + printThreadInfo(ti));
			System.out.print(".");
		}
		threadDump.append("\"VM Periodic Task Thread\" prio=10 tid=0x00002aab64f05800 nid=0x7660 waiting on condition");
		System.out.print("COMPLETED!");
		if (findDeadlock() != null){
			threadDump.append(findDeadlock());
		}
		return threadDump.toString();
}
/**
* Prints the thread dump information with locks info to System.out.
 * @return 
 * @throws IOException 
*/
private String dumpThreadInfoWithLocks() {
	String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis()));
	StringBuilder threadDump = new StringBuilder("\n"+date+"\nFull thread dump with locks info");
	System.out.print(date+" Creating Full thread dump with locks info ");
	ThreadInfo[] tinfos = tmbean.dumpAllThreads(true, true);
	for (ThreadInfo ti : tinfos) {
		threadDump.append("\n" + printThreadInfo(ti));
		LockInfo[] syncs = ti.getLockedSynchronizers();
		threadDump.append(printLockInfo(syncs));
		System.out.print(".");
	}
	threadDump.append("\"VM Periodic Task Thread\" prio=10 tid=0x00002aab64f05800 nid=0x7660 waiting on condition\n");
	System.out.print("COMPLETED!");
	if (findDeadlock() != null){
		threadDump.append(findDeadlock());
	}
	return threadDump.toString();
	
}


private String printThreadInfo(ThreadInfo ti){
	// print stack trace with locks
	StackTraceElement[] stacktrace = ti.getStackTrace();
	MonitorInfo[] monitors = ti.getLockedMonitors();
	currentThreadInfo result = new currentThreadInfo(ti);
	StringBuilder threadOutput = new StringBuilder(result.getThreadName());
	threadOutput.append(result.getThreadStateDesc());
	if ( stats == true){
		threadOutput.append(result.getThreadStats());
	}
	for (int i = 0; i < stacktrace.length; i++) {
		StackTraceElement ste = stacktrace[i];
		if ( i == 0){
			threadOutput.append("\n    java.lang.Thread.State: "+result.getThreadState());
			threadOutput.append("\n"+ INDENT + "at " + ste.toString());
			if ( ste.toString().contains("java.lang.Object.wait(Native Method)") && result.getLockName() != null){
				threadOutput.append("\n"+ INDENT + "- waiting on " + result.getLockName());
			}
			if ( ste.toString().contains("sun.misc.Unsafe.park(Native Method)") && result.getLockName() != null){
				threadOutput.append("\n"+ INDENT + "- parking to wait for " + result.getLockName());
			}
			if ( result.getThreadStateDesc().contains("BLOCKED") && result.getLockName() != null){
				threadOutput.append("\n"+ INDENT + "- waiting to lock " + result.getLockName());
			}
		}
		else {
			threadOutput.append("\n"+ INDENT + "at " + ste.toString());
		}
		for (MonitorInfo mi : monitors) {
			if (mi.getLockedStackDepth() == i) {
				threadOutput.append("\n" + INDENT + " - locked " + mi);
			}
		}
	}
	threadOutput.append("\n");
	//System.out.print(threadOutput.toString());
	//printMonitorInfo(ti, monitors);
	return threadOutput.toString();
}


private void printMonitorInfo(ThreadInfo ti, MonitorInfo[] monitors) {
	System.out.println(INDENT + "Locked monitors: count = " + monitors.length);
	for (MonitorInfo mi : monitors) {
		System.out.println(INDENT + " - " + mi + " locked at ");
		System.out.println(INDENT + " " + mi.getLockedStackDepth() + " "
		+ mi.getLockedStackFrame());
	}
}
private String printLockInfo(LockInfo[] locks) {
	StringBuilder lockOutput = new StringBuilder(INDENT + "Locked synchronizers: count = " + locks.length + "\n");
	for (LockInfo li : locks) {
		lockOutput.append(INDENT + " - " + li + "\n");
	}
	//lockOutput.append("\n");
	return lockOutput.toString();
}
/**
* Checks if any threads are deadlocked. If any, print the thread dump
* information.
*/
public String findDeadlock() {
	StringBuilder deadlock = new StringBuilder();
	long[] tids;
	if (findDeadlocksMethodName.equals("findDeadlockedThreads")
	&& tmbean.isSynchronizerUsageSupported()) {
		tids = tmbean.findDeadlockedThreads();
		if (tids == null) {
			return null;
		}
		deadlock.append("Deadlock found :-");
		//System.out.println("Deadlock found :-");
		ThreadInfo[] infos = tmbean.getThreadInfo(tids, true, true);
		for (ThreadInfo ti : infos) {
			deadlock.append(printThreadInfo(ti));
			deadlock.append(ti.getLockedSynchronizers());
			//printLockInfo(ti.getLockedSynchronizers());
			deadlock.append("\n");
		}
		return deadlock.toString();
	} else {
		tids = tmbean.findMonitorDeadlockedThreads();
		if (tids == null) {
			return null;
		}
		ThreadInfo[] infos = tmbean.getThreadInfo(tids, Integer.MAX_VALUE);
		for (ThreadInfo ti : infos) {
			// print thread information
			deadlock.append(printThreadInfo(ti));
		}
		return deadlock.toString();
	}
}

private void parseMBeanInfo() throws IOException {
	try {
		MBeanOperationInfo[] mopis = server.getMBeanInfo(objname).getOperations();
		// look for findDeadlockedThreads operations;
		boolean found = false;
		for (MBeanOperationInfo op : mopis) {
			if (op.getName().equals(findDeadlocksMethodName)) {
				found = true;
				break;
			}
		}
		if (!found) {
			// if findDeadlockedThreads operation doesn't exist,
			// the target VM is running on JDK 5 and details about
			// synchronizers and locks cannot be dumped.
			findDeadlocksMethodName = "findMonitorDeadlockedThreads";
			canDumpLocks = false;
		}
	} catch (IntrospectionException e) {
			InternalError ie = new InternalError(e.getMessage());
			ie.initCause(e);
			throw ie;
	} catch (InstanceNotFoundException e) {
			InternalError ie = new InternalError(e.getMessage());
			ie.initCause(e);
			throw ie;
	} catch (ReflectionException e) {
			InternalError ie = new InternalError(e.getMessage());
			ie.initCause(e);
			throw ie;
	}
}
//
//CTL Class added to create TDA compatible thread dump
//
final class currentThreadInfo {
	private final ThreadInfo ti;
	private final String INDENT = "\t";
	
	public currentThreadInfo(ThreadInfo ti){
		this.ti = ti;
		
	}
	public String getLockName() {
		if (ti.getLockName() != null) {
			return ti.getLockName();
		}
		else { return null; }
	}
	public String getThreadState(){
		return ti.getThreadState().toString();
	}
	public String getThreadStateDesc(){
		StackTraceElement[] stacktrace = ti.getStackTrace();
		StringBuilder threadStateDesc = new StringBuilder();
		if ( stacktrace.length > 0){
			StackTraceElement topOfStack = stacktrace[0];
			if (topOfStack.toString().contains("java.lang.Object.wait(Native Method)")){
				//threadStateDesc = new StringBuilder("in Object.wait()");
				threadStateDesc.append("in Object.wait()");
			}
			else {
				threadStateDesc.append(ti.getThreadState().toString());
			}
		}
		if (ti.isSuspended()) {
			threadStateDesc.append(" (suspended)");
		}
		if (ti.isInNative()) {
			threadStateDesc.append(" (JNI Native Code)");
		}
		if (ti.getLockOwnerName() != null) {
			threadStateDesc.append(INDENT + " owned by " + ti.getLockOwnerName() + " Id="+ ti.getLockOwnerId());
		}
		return threadStateDesc.toString();
	}
	public String getThreadName() {
		//String INDENT = "\t";
		String tidHex= Integer.toHexString((int)ti.getThreadId());	
		StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"" +" prio=5 tid=0x" + ti.getThreadId() + " nid=0x"+ tidHex + " ");
		return sb.toString();
	}
	public String getThreadStats() {
		StringBuilder sb = new StringBuilder(" - stats:");
		if (tmbean.isThreadCpuTimeSupported()){
			long cpu = (tmbean.getThreadCpuTime(ti.getThreadId()) / 1000000L);
			//long user = (tmbean.getThreadUserTime(ti.getThreadId()) / 1000000L);
			sb.append(" cpu="+cpu);
		}
		sb.append(" blk="+ti.getBlockedTime()+ " wait="+ti.getWaitedTime());
		return sb.toString();
	}
}

}
