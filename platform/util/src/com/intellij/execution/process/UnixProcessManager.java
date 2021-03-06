/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Processor;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @author traff
 */
public class UnixProcessManager {
  public static final int SIGINT = 2;
  public static final int SIGKILL = 9;
  public static final int SIGTERM = 15;
  public static final int SIGCONT = 19;

  private static CLib C_LIB;

  static {
    try {
      if (!Platform.isWindows()) {
        C_LIB = ((CLib)Native.loadLibrary("c", CLib.class));
      }
    }
    catch (Exception e) {
      Logger log = Logger.getInstance(UnixProcessManager.class);
      log.warn("Can't load c library", e);
      C_LIB = null;
    }
  }

  private UnixProcessManager() {
  }

  public static int getProcessPid() {
    checkCLib();
    return C_LIB.getpid();
  }

  public static int getProcessPid(Process process) {
    try {
      Field f = process.getClass().getDeclaredField("pid");
      f.setAccessible(true);
      return ((Number)f.get(process)).intValue();
    }
    catch (NoSuchFieldException e) {
      throw new IllegalStateException("system is not unix", e);
    }
    catch (IllegalAccessException e) {
      throw new IllegalStateException("system is not unix", e);
    }
  }

  public static void sendSignal(Process process, int signal) {
    int process_pid = getProcessPid(process);
    sendSignal(process_pid, signal);
  }

  public static void sendSignal(int pid, int signal) {
    checkCLib();
    C_LIB.kill(pid, signal);
  }

  private static void checkCLib() {
    if (C_LIB == null) {
      throw new IllegalStateException("System is not unix(couldn't load c library)");
    }
  }

  public static boolean sendSigIntToProcessTree(Process process) {
    return sendSignalToProcessTree(process, SIGINT);
  }

  public static boolean sendSigKillToProcessTree(Process process) {
    return sendSignalToProcessTree(process, SIGKILL);
  }

  /**
   * Sends signal to every child process of a tree root process
   *
   * @param process tree root process
   */
  public static boolean sendSignalToProcessTree(Process process, int signal) {
    checkCLib();

    final int our_pid = C_LIB.getpid();
    final int process_pid = getProcessPid(process);

    final Ref<Integer> foundPid = new Ref<Integer>();
    final ProcessInfo processInfo = new ProcessInfo();
    final List<Integer> childrenPids = new ArrayList<Integer>();

    processPSOutput(getPSCmd(false), new Processor<String>() {
      @Override
      public boolean process(String s) {
        StringTokenizer st = new StringTokenizer(s, " ");

        int parent_pid = Integer.parseInt(st.nextToken());
        int pid = Integer.parseInt(st.nextToken());

        processInfo.register(pid, parent_pid);

        if (parent_pid == process_pid) {
          childrenPids.add(pid);
        }

        if (pid == process_pid) {
          if (parent_pid == our_pid) {
            foundPid.set(pid);
          }
          else {
            throw new IllegalStateException("process is not our child");
          }
        }
        return false;
      }
    });

    boolean result;
    if (!foundPid.isNull()) {
      processInfo.killProcTree(foundPid.get(), signal, UNIX_KILLER);
      result = true;
    }
    else {
      for (Integer pid : childrenPids) {
        processInfo.killProcTree(pid, signal, UNIX_KILLER);
      }
      result = false;
    }

    //TODO[traff]: check that processes were really terminated.
    return result;
  }

  public static void processPSOutput(String[] cmd, Processor<String> processor) {
    try {
      Process p = Runtime.getRuntime().exec(cmd);

      processPSOutput(p, processor);
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void processPSOutput(Process psProcess, Processor<String> processor) throws IOException {
    @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
    BufferedReader stdOutput = new BufferedReader(new
                                                  InputStreamReader(psProcess.getInputStream()));
    BufferedReader stdError = new BufferedReader(new
                                                 InputStreamReader(psProcess.getErrorStream()));

    try {
      String s;
      stdOutput.readLine(); //ps output header
      while ((s = stdOutput.readLine()) != null) {
        processor.process(s);
      }

      StringBuilder errorStr = new StringBuilder();
      while ((s = stdError.readLine()) != null) {
        errorStr.append(s).append("\n");
      }
      if (errorStr.length() > 0) {
        throw new IllegalStateException("error:" + errorStr.toString());
      }
    }
    finally {
      stdOutput.close();
      stdError.close();
    }
  }

  public static String[] getPSCmd(boolean commandLineOnly) {
    return getPSCmd(commandLineOnly, false);
  }

  public static String[] getPSCmd(boolean commandLineOnly, boolean isShortenCommand) {
    String psCommand = "/bin/ps";
    if (!new File(psCommand).isFile()) {
      psCommand = "ps";
    }
    if (SystemInfo.isLinux) {
      return new String[]{psCommand, "-e", "--format", commandLineOnly ? "%a" : "%P%p%a"};
    }
    else if (SystemInfo.isMac || SystemInfo.isFreeBSD) {
      final String command = isShortenCommand ? "comm" : "command";
      return new String[]{psCommand, "-ax", "-o", commandLineOnly ? command : "ppid,pid," + command};
    }
    else {
      throw new IllegalStateException(System.getProperty("os.name") + " is not supported.");
    }
  }

  @NotNull
  public static String readProcEnviron(int child_pid) throws FileNotFoundException {
    StringBuffer res = new StringBuffer();
    Scanner s = new Scanner(new File("/proc/" + child_pid + "/environ"));
    while (s.hasNextLine()) {
      res.append(s).append("\n");
    }
    return res.toString();
  }


  public interface CLib extends Library {
    int getpid();

    int kill(int pid, int signal);
  }

  public static class ProcessInfo {
    private Map<Integer, List<Integer>> BY_PARENT = new TreeMap<Integer, List<Integer>>(); // pid -> list of children pids

    public void register(Integer pid, Integer parentPid) {
      List<Integer> children = BY_PARENT.get(parentPid);
      if (children == null) children = new LinkedList<Integer>();
      children.add(pid);
      BY_PARENT.put(parentPid, children);
    }

    public void killProcTree(int pid, int signal, ProcessKiller killer) {
      List<Integer> children = BY_PARENT.get(pid);
      if (children != null) {
        for (int child : children) killProcTree(child, signal, killer);
      }
      killer.kill(pid, signal);
    }
  }

  public interface ProcessKiller {
    void kill(int pid, int signal);
  }

  private final static ProcessKiller UNIX_KILLER = new ProcessKiller() {
    @Override
    public void kill(int pid, int signal) {
      sendSignal(pid, signal);
    }
  };
}
