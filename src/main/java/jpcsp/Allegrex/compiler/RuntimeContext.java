/*
This file is part of jpcsp.

Jpcsp is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Jpcsp is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Jpcsp.  If not, see <http://www.gnu.org/licenses/>.
 */
package jpcsp.Allegrex.compiler;

import static jpcsp.Emulator.exitCalled;
import static jpcsp.Memory.addressMask;
import static jpcsp.util.Utilities.addAddressHex;
import static jpcsp.util.Utilities.addHex;
import static jpcsp.util.Utilities.sleep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jpcsp.Emulator;
import jpcsp.Memory;
import jpcsp.MemoryMap;
import jpcsp.Processor;
import jpcsp.State;
import jpcsp.Allegrex.Common;
import jpcsp.Allegrex.CpuState;
import jpcsp.Allegrex.Decoder;
import jpcsp.Allegrex.Instructions;
import jpcsp.Allegrex.Common.Instruction;
import jpcsp.HLE.Modules;
import jpcsp.HLE.PspString;
import jpcsp.HLE.SyscallHandler;
import jpcsp.HLE.TPointer;
import jpcsp.HLE.kernel.managers.IntrManager;
import jpcsp.HLE.kernel.types.SceKernelThreadInfo;
import jpcsp.HLE.modules.ThreadManForUser;
import jpcsp.HLE.modules.reboot;
import jpcsp.HLE.modules.sceDisplay;
import jpcsp.graphics.RE.externalge.ExternalGE;
import jpcsp.mediaengine.MEProcessor;
import jpcsp.memory.DebuggerMemory;
import jpcsp.memory.mmio.MMIOHandlerDisplayController;
import jpcsp.scheduler.Scheduler;
import jpcsp.settings.AbstractBoolSettingsListener;
import jpcsp.settings.Settings;
import jpcsp.sound.SoundChannel;
import jpcsp.util.CpuDurationStatistics;
import jpcsp.util.DurationStatistics;
import jpcsp.util.Utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gid15
 *
 */
public class RuntimeContext {
    public  static Logger log = LoggerFactory.getLogger("runtime");
	private static boolean compilerEnabled = true;
	public  static float[] fpr;
	public  static float[] vprFloat;
	public  static int[] vprInt;
	public  static int[] memoryInt;
	public  static Processor processor;
	public  static CpuState cpu;
	public  static int syscallRa;
	public  static Memory memory;
	public  static       boolean enableDebugger = true;
	public  static final String debuggerName = "syncDebugger";
	public  static       boolean debugCodeBlockCalls = false;
	public  static final String debugCodeBlockStart = "debugCodeBlockStart";
	public  static final String debugCodeBlockEnd = "debugCodeBlockEnd";
	private static final int debugCodeBlockNumberOfParameters = 6;
	private static final Map<Integer, Integer> debugCodeBlocks = new HashMap<Integer, Integer>();
	public  static final boolean debugCodeInstruction = false;
	public  static final String debugCodeInstructionName = "debugCodeInstruction";
	public  static final boolean debugMemoryRead = false;
	public  static final boolean debugMemoryWrite = false;
	public  static final boolean debugMemoryReadWriteNoSP = true;
	public  static final boolean enableInstructionTypeCounting = false;
	public  static final String instructionTypeCount = "instructionTypeCount";
	public  static final String logError = "logError";
	public  static final String pauseEmuWithStatus = "pauseEmuWithStatus";
	public  static final boolean enableLineNumbers = true;
	public  static final boolean checkCodeModification = false;
	private static final boolean invalidateAllCodeBlocks = false;
	private static final int idleSleepMicros = 1000;
	private static final Map<Integer, CodeBlock> codeBlocks = Collections.synchronizedMap(new HashMap<Integer, CodeBlock>());
	private static int codeBlocksLowestAddress = Integer.MAX_VALUE;
	private static int codeBlocksHighestAddress = Integer.MIN_VALUE;
	// A fast lookup array for executables (to improve the performance of the Allegrex instruction jalr)
	private static IExecutable[] fastExecutableLookup;
	// A fast lookup for the Allegrex instruction ICACHE HIT INVALIDATE
	private static CodeBlockList[] fastCodeBlockLookup;
	private static final int fastCodeBlockLookupShift = 8;
	private static final int fastCodeBlockSize = 64; // Matching the size used by the Allegrex instruction ICACHE HIT INVALIDATE
	private static final Map<SceKernelThreadInfo, RuntimeThread> threads = Collections.synchronizedMap(new HashMap<SceKernelThreadInfo, RuntimeThread>());
	private static final Map<SceKernelThreadInfo, RuntimeThread> toBeStoppedThreads = Collections.synchronizedMap(new HashMap<SceKernelThreadInfo, RuntimeThread>());
	private static final Map<SceKernelThreadInfo, RuntimeThread> alreadyStoppedThreads = Collections.synchronizedMap(new HashMap<SceKernelThreadInfo, RuntimeThread>());
	private static final List<Thread> alreadySwitchedStoppedThreads = Collections.synchronizedList(new ArrayList<Thread>());
	private static final Map<SceKernelThreadInfo, RuntimeThread> toBeDeletedThreads = Collections.synchronizedMap(new HashMap<SceKernelThreadInfo, RuntimeThread>());
	public  static volatile SceKernelThreadInfo currentThread = null;
	private static volatile RuntimeThread currentRuntimeThread = null;
	private static final Object waitForEnd = new Object();
	private static volatile Emulator emulator;
	private static volatile boolean isIdle = false;
	private static volatile boolean reset = false;
	public  static CpuDurationStatistics idleDuration = new CpuDurationStatistics("Idle Time");
	private static Map<Instruction, Integer> instructionTypeCounts = Collections.synchronizedMap(new HashMap<Instruction, Integer>());
	public  static final boolean enableDaemonThreadSync = true;
	public  static final String syncName = "sync";
	public  static volatile boolean wantSync = false;
	private static RuntimeSyncThread runtimeSyncThread = null;
	private static RuntimeThread syscallRuntimeThread;
	private static sceDisplay sceDisplayModule;
	private static final Object idleSyncObject = new Object();
	public static int firmwareVersion;
	private static boolean isHomebrew = false;
	public static boolean javaThreadScheduling = true;

	private static class CompilerEnabledSettingsListerner extends AbstractBoolSettingsListener {
		@Override
		protected void settingsValueChanged(boolean value) {
			setCompilerEnabled(value);
		}
	}

	private static class CodeBlockList extends LinkedList<CodeBlock> {
		private static final long serialVersionUID = 7370950118403866860L;
	}

	private static void setCompilerEnabled(boolean enabled) {
		compilerEnabled = enabled;
	}

	public static boolean isCompilerEnabled() {
		return compilerEnabled;
	}

	public static void execute(Instruction insn, int opcode) {
		insn.interpret(processor, opcode);
	}

	private static int jumpCall(int address) throws Exception {
        IExecutable executable = getExecutable(address);
        if (executable == null) {
            // TODO Return to interpreter
        	String msg = String.format("jumpCall - Cannot find executable 0x%08X", address);
            log.error(msg);
            throw new RuntimeException(msg);
        }

		int returnValue;
		int sp = cpu._sp;
		RuntimeThread stackThread = currentRuntimeThread;

		if (stackThread != null && stackThread.isStackMaxSize()) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("jumpCall stack already reached maxSize, returning 0x%08X", address));
			}
			throw new StackPopException(address);
		}

		try {
			if (stackThread != null) {
				stackThread.increaseStackSize();
			}
			returnValue = executable.exec();
		} catch (StackOverflowError e) {
			log.error(String.format("StackOverflowError stackSize=%d", stackThread.getStackSize()));
			throw e;
		} finally {
			if (stackThread != null) {
				stackThread.decreaseStackSize();
			}
		}

		if (stackThread != null && stackThread.isStackMaxSize() && cpu._sp > sp) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("jumpCall returning 0x%08X with $sp=0x%08X, start $sp=0x%08X", returnValue, cpu._sp, sp));
			}
			throw new StackPopException(returnValue);
		}

		if (debugCodeBlockCalls && log.isDebugEnabled()) {
        	log.debug(String.format("jumpCall returning 0x%08X, $v0=0x%08X", returnValue, cpu._v0));
        }

        return returnValue;
	}

	public static void jump(int address, int returnAddress) throws Exception {
		if (debugCodeBlockCalls && log.isDebugEnabled()) {
			log.debug(String.format("jump starting address=0x%08X, returnAddress=0x%08X, $sp=0x%08X", address, returnAddress, cpu._sp));
		}

		int sp = cpu._sp;
		while ((address & addressMask) != (returnAddress & addressMask)) {
			try {
				address = jumpCall(address);
			} catch (StackPopException e) {
				if (log.isDebugEnabled()) {
					log.debug(String.format("jump catching StackPopException 0x%08X with $sp=0x%08X, start $sp=0x%08X", e.getRa(), cpu._sp, sp));
				}
				if ((e.getRa() & addressMask) != (returnAddress & addressMask)) {
					throw e;
				}
				break;
			}
		}

		if (debugCodeBlockCalls && log.isDebugEnabled()) {
			log.debug(String.format("jump returning address=0x%08X, returnAddress=0x%08X, $sp=0x%08X", address, returnAddress, cpu._sp));
		}
	}

    public static int call(int address) throws Exception {
		if (debugCodeBlockCalls && log.isDebugEnabled()) {
			log.debug(String.format("call address=0x%08X, $ra=0x%08X", address, cpu._ra));
		}

		// We are rebooting in LLE, make sure that the thread name is reset to "root"
		if (address == 0x88600000 || address == 0x88FC0000) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("Rebooting at address 0x%08X", address));
			}
			Emulator.getInstance().onReboot();
		}

        int returnValue = jumpCall(address);

        return returnValue;
    }

	public static int executeInterpreter(int address) throws Exception {
		if (debugCodeBlockCalls && log.isDebugEnabled()) {
			log.debug(String.format("executeInterpreter address=0x%08X", address));
		}

		boolean useMMIO = false;
		if (!Memory.isAddressGood(address) || Compiler.getInstance().isUsingMMIO(address)) {
			Memory mmio = RuntimeContextLLE.getMMIO();
			if (mmio != null) {
				useMMIO = true;
				cpu.setMemory(mmio);
			}
		}

		boolean interpret = true;
		cpu.pc = address;
		int returnValue = 0;
		while (interpret) {
			processor.interpret();

			Instruction insn = processor.getInstruction();
			if (insn.hasFlags(Instruction.FLAG_STARTS_NEW_BLOCK)) {
				if (useMMIO) {
					cpu.setMemory(memory);
				}
				cpu.pc = jumpCall(cpu.pc);
				if (useMMIO) {
					cpu.setMemory(RuntimeContextLLE.getMMIO());
				}
			} else if (insn.hasOneFlag(Instruction.FLAG_ENDS_BLOCK | Instruction.FLAG_TRIGGERS_EXCEPTION) && !insn.hasFlags(Instruction.FLAG_IS_CONDITIONAL)) {
				interpret = false;
				returnValue = cpu.pc;
			}
		}

		if (useMMIO) {
			cpu.setMemory(memory);
		}

		return returnValue;
	}

	public static void execute(int opcode) {
		Instruction insn = Decoder.instruction(opcode);
		execute(insn, opcode);
	}

	private static String getDebugCodeBlockStart(CpuState cpu, int address) {
		// Do not build the string using "String.format()" for improved performance of this time-critical function
		StringBuilder s = new StringBuilder("Starting CodeBlock 0x");
		addAddressHex(s, address);

		int syscallAddress = address + 4;
		if (Memory.isAddressGood(syscallAddress)) {
    		int syscallOpcode = cpu.memory.read32(syscallAddress);
    		Instruction syscallInstruction = Decoder.instruction(syscallOpcode);
    		if (syscallInstruction == Instructions.SYSCALL) {
        		String syscallDisasm = syscallInstruction.disasm(syscallAddress, syscallOpcode);
    			s.append(syscallDisasm.substring(19));
    		}
		}

		int numberOfParameters = debugCodeBlockNumberOfParameters;
		if (!debugCodeBlocks.isEmpty()) {
			Integer numberOfParametersValue = debugCodeBlocks.get(address);
			if (numberOfParametersValue != null) {
				numberOfParameters = numberOfParametersValue.intValue();
			}
		}

		if (numberOfParameters > 0) {
			int maxRegisterParameters = Math.min(numberOfParameters, 8);
			for (int i = 0; i < maxRegisterParameters; i++) {
				int register = Common._a0 + i;
				int parameterValue = cpu.getRegister(register);

				s.append(", ");
				s.append(Common.gprNames[register]);
				s.append("=0x");
				if (Memory.isAddressGood(parameterValue)) {
					addAddressHex(s, parameterValue);
				} else {
					addHex(s, parameterValue);
				}
			}
		}

		s.append(", $ra=0x");
		addAddressHex(s, cpu._ra);
		s.append(", $sp=0x");
		addAddressHex(s, cpu._sp);

		return s.toString();
	}

	public static void debugCodeBlockStart(int address) {
		if (!debugCodeBlocks.isEmpty() && debugCodeBlocks.containsKey(address)) {
			if (log.isInfoEnabled()) {
				log.info(getDebugCodeBlockStart(cpu, address));
			}
		} else if (log.isDebugEnabled()) {
    		log.debug(getDebugCodeBlockStart(cpu, address));
    	}
    }

	public static void debugCodeBlockStart(CpuState cpu, int address) {
		if (!debugCodeBlocks.isEmpty() && debugCodeBlocks.containsKey(address)) {
			if (log.isInfoEnabled()) {
				log.info(getDebugCodeBlockStart(cpu, address));
			}
		} else if (log.isDebugEnabled()) {
    		log.debug(getDebugCodeBlockStart(cpu, address));
    	}
    }

    public static void debugCodeBlockEnd(int address, int returnAddress) {
    	if (log.isDebugEnabled()) {
    		debugCodeBlockEnd(cpu, address, returnAddress);
    	}
    }

    public static void debugCodeBlockEnd(CpuState cpu, int address, int returnAddress) {
    	if (log.isDebugEnabled()) {
    		// Do not build the string using "String.format()" for improved performance of this time-critical function
    		StringBuilder s = new StringBuilder("Returning from CodeBlock 0x");
    		addAddressHex(s, address);
    		s.append(" to 0x");
    		addAddressHex(s, returnAddress);
    		s.append(", $sp=0x");
    		addAddressHex(s, cpu._sp);
    		s.append(", $v0=0x");
    		addAddressHex(s, cpu._v0);
    		log.debug(s.toString());
    	}
    }

    public static void debugCodeInstruction(int address, int opcode) {
    	if (log.isTraceEnabled()) {
    		cpu.pc = address;
    		Instruction insn = Decoder.instruction(opcode);

    		// Do not build the string using "String.format()" for improved performance of this time-critical function
    		StringBuilder s = new StringBuilder("Executing 0x");
    		addAddressHex(s, address);
    		s.append(insn.hasFlags(Instruction.FLAG_INTERPRETED) ? " I - " : " C - ");
    		s.append(insn.disasm(address, opcode));
    		log.trace(s.toString());
    	}
    }

    private static void initialiseDebugger() {
        if (State.debugger != null || (memory instanceof DebuggerMemory) || debugMemoryRead || debugMemoryWrite) {
        	enableDebugger = true;
        } else {
        	enableDebugger = false;
        }
    }

    public static boolean initialise() {
        if (!compilerEnabled) {
            return false;
        }

        if (enableDaemonThreadSync && runtimeSyncThread == null) {
        	runtimeSyncThread = new RuntimeSyncThread();
        	runtimeSyncThread.setName("Sync Daemon");
        	runtimeSyncThread.setDaemon(true);
        	runtimeSyncThread.start();
        }

        updateMemory();

        initialiseDebugger();

        Profiler.initialise();

        sceDisplayModule = Modules.sceDisplayModule;

        fastExecutableLookup = new IExecutable[MemoryMap.SIZE_RAM >> 2];
        fastCodeBlockLookup = new CodeBlockList[MemoryMap.SIZE_RAM >> fastCodeBlockLookupShift];

		return true;
    }

    public static boolean canExecuteCallback(SceKernelThreadInfo callbackThread) {
    	if (!compilerEnabled) {
    		return true;
    	}

    	// Can the callback be executed in any thread (e.g. is an interrupt)?
    	if (callbackThread == null) {
    		return true;
    	}

    	if (Modules.ThreadManForUserModule.isIdleThread(callbackThread)) {
    		return true;
    	}

    	Thread currentThread = Thread.currentThread();
    	if (currentThread instanceof RuntimeThread) {
    		RuntimeThread currentRuntimeThread = (RuntimeThread) currentThread;
    		if (callbackThread == currentRuntimeThread.getThreadInfo()) {
    			return true;
    		}
    	}

    	return false;
    }

    private static void checkPendingCallbacks() {
    	if (Modules.ThreadManForUserModule.checkPendingActions()) {
        	// if some action has been executed, the current thread might be changed. Resync.
    		if (log.isDebugEnabled()) {
    			log.debug(String.format("A pending action has been executed for the thread"));
    		}
    		wantSync = true;
    	}

    	Modules.ThreadManForUserModule.checkPendingCallbacks();
    }

    public static void executeCallback() {
    	int pc = cpu.pc;

		if (log.isDebugEnabled()) {
			log.debug(String.format("Start of Callback 0x%08X", pc));
		}

		// Switch to the real active thread, even if it is an idle thread
		switchRealThread(Modules.ThreadManForUserModule.getCurrentThread());

        boolean callbackExited = executeFunction(pc);

		if (log.isDebugEnabled()) {
			log.debug(String.format("End of Callback 0x%08X", pc));
		}

        if (cpu.pc == ThreadManForUser.CALLBACK_EXIT_HANDLER_ADDRESS || callbackExited) {
            Modules.ThreadManForUserModule.hleKernelExitCallback(Emulator.getProcessor());

            // Re-sync the runtime, the current thread might have been rescheduled
            wantSync = true;
        }

        update();
    }

    private static void updateStaticVariables() {
		emulator = Emulator.getInstance();
		processor = Emulator.getProcessor();
		cpu = processor.cpu;
		if (cpu != null) {
		    fpr = processor.cpu.fpr;
		    vprFloat = processor.cpu.vprFloat;
		    vprInt = processor.cpu.vprInt;
		}
    }

    public static void updateMemory() {
        memory = Emulator.getMemory();
        memoryInt = memory.getMemoryInt(0);
    }

    public static void update() {
        if (!compilerEnabled) {
            return;
        }

        updateStaticVariables();

        ThreadManForUser threadMan = Modules.ThreadManForUserModule;
        if (currentThread == null || IntrManager.getInstance().canExecuteInterruptNow()) {
            SceKernelThreadInfo newThread = threadMan.getCurrentThread();
            if (newThread != null && newThread != currentThread) {
                switchThread(newThread);
            }
        }
	}

    private static void switchRealThread(SceKernelThreadInfo threadInfo) {
    	RuntimeThread thread = threads.get(threadInfo);
    	if (thread == null) {
    		thread = new RuntimeThread(threadInfo);
    		threads.put(threadInfo, thread);
    		thread.start();
    	}

    	currentThread = threadInfo;
    	currentRuntimeThread = thread;
        isIdle = false;
    }

    private static void switchThread(SceKernelThreadInfo threadInfo) {
    	if (log.isDebugEnabled()) {
    		String name;
    		if (threadInfo == null) {
    			name = "Idle";
    		} else {
    			name = threadInfo.name;
    		}

    		if (currentThread == null) {
        		log.debug("Switching to Thread " + name);
    		} else {
        		log.debug("Switching from Thread " + currentThread.name + " to " + name);
    		}
    	}

    	if (threadInfo == null || Modules.ThreadManForUserModule.isIdleThread(threadInfo)) {
    	    isIdle = true;
    	    currentThread = null;
    	    currentRuntimeThread = null;
    	} else if (toBeStoppedThreads.containsKey(threadInfo)) {
    		// This thread must stop immediately
    		isIdle = true;
    		currentThread = null;
    		currentRuntimeThread = null;
    	} else {
    		switchRealThread(threadInfo);
    	}
    }

    private static void syncIdle() throws StopThreadException {
        if (isIdle) {
        	ThreadManForUser threadMan = Modules.ThreadManForUserModule;
            Scheduler scheduler = Emulator.getScheduler();

            log.debug("Starting Idle State...");
            idleDuration.start();
			while (isIdle) {
				checkStoppedThread();
				{
					// Do not take the duration of sceDisplay into idleDuration
					idleDuration.end();
					syncEmulator(true);
					idleDuration.start();
				}
				syncPause();
				checkPendingCallbacks();
				scheduler.step();
				if (threadMan.isIdleThread(threadMan.getCurrentThread())) {
					threadMan.checkCallbacks();
					threadMan.hleRescheduleCurrentThread();
				}

				if (isIdle) {
					idleSleepInterruptable();
				}
			}
            idleDuration.end();
            log.debug("Ending Idle State");
        }
    }

    /*
     * While being idle, try to reduce the load on the host CPU
     * by sleeping as much as possible.
     * We can now sleep until the next scheduler action needs to be executed.
     *
     * If the scheduler is receiving, from another thread, a new action
     * to be executed earlier, the wait state of this thread
     * will be interrupted (see onNextScheduleModified()).
     * This is for example the case when a GE list is ending (FINISH/SIGNAL + END)
     * and a GE callback has to be executed immediately.
     *
     * When LLE is enabled, the wait state can also be interrupted
     * when a different thread is triggering an exception
     * (see onLLEInterrupt()).
     * This is for example the case when a Dmac thread is finishing its
     * action and triggering a PSP_DMA0_INTR interrupt.
     */
    private static void idleSleepInterruptable() {
        Scheduler scheduler = Emulator.getScheduler();

        long delay = scheduler.getNextActionDelay(idleSleepMicros);
		if (delay > 0) {
			int intDelay;
			if (delay >= idleSleepMicros) {
				intDelay = idleSleepMicros;
			} else {
				intDelay = (int) delay;
			}

			try {
				// Wait for intDelay milliseconds.
				// The wait state will be terminated whenever the scheduler
				// is receiving a new scheduler action (see onNextScheduleModified()),
				// or whenever an interrupt has been triggered ().
				synchronized (idleSyncObject) {
					idleSyncObject.wait(intDelay / 1000, (intDelay % 1000) * 1000);
				}
			} catch (InterruptedException e) {
				// Ignore exception
			}
		}
    }

    private static void syncThreadImmediately() throws StopThreadException {
        Thread currentThread = Thread.currentThread();
    	if (currentRuntimeThread != null &&
                currentThread != currentRuntimeThread && !alreadySwitchedStoppedThreads.contains(currentThread)) {
    		currentRuntimeThread.continueRuntimeExecution();

    		if (currentThread instanceof RuntimeThread) {
    			RuntimeThread runtimeThread = (RuntimeThread) currentThread;
    			if (!alreadyStoppedThreads.containsValue(runtimeThread)) {
	    			log.debug("Waiting to be scheduled...");
					runtimeThread.suspendRuntimeExecution();
	    			log.debug("Scheduled, restarting...");
	    	        checkStoppedThread();

	    	        updateStaticVariables();
    			} else {
    				alreadySwitchedStoppedThreads.add(currentThread);
    			}
    		}
    	}

    	checkPendingCallbacks();
    }

    private static void syncThread() throws StopThreadException {
        syncIdle();

        if (toBeDeletedThreads.containsValue(getRuntimeThread())) {
        	return;
        }

        Thread currentThread = Thread.currentThread();
    	if (log.isDebugEnabled()) {
    		log.debug("syncThread currentThread=" + currentThread.getName() + ", currentRuntimeThread=" + currentRuntimeThread.getName());
    	}
    	syncThreadImmediately();
    }

    public static RuntimeThread getRuntimeThread() {
    	Thread currentThread = Thread.currentThread();
		if (currentThread instanceof RuntimeThread) {
			return (RuntimeThread) currentThread;
		}

		return null;
    }

    private static boolean isStoppedThread() {
    	if (toBeStoppedThreads.isEmpty()) {
    		return false;
    	}

		RuntimeThread runtimeThread = getRuntimeThread();
		if (runtimeThread != null && toBeStoppedThreads.containsValue(runtimeThread)) {
			if (!alreadyStoppedThreads.containsValue(runtimeThread)) {
				return true;
			}
		}

		return false;
    }

    private static void checkStoppedThread() throws StopThreadException {
    	if (isStoppedThread()) {
			throw new StopThreadException("Stopping Thread " + Thread.currentThread().getName());
		}
    }

    private static void syncPause() throws StopThreadException {
    	if (Emulator.pause) {
	        Emulator.getClock().pause();
	        try {
	            synchronized(emulator) {
	               while (Emulator.pause) {
	                   checkStoppedThread();
	                   emulator.wait();
	               }
	           }
	        } catch (InterruptedException e){
	        	// Ignore Exception
	        } finally {
	        	Emulator.getClock().resume();
	        }
    	}
    }

    public static void syncDebugger(int pc) throws StopThreadException {
		processor.cpu.pc = pc;
    	if (State.debugger != null) {
    		syncDebugger();
    		syncPause();
    	} else if (Emulator.pause) {
    		syncPause();
    	}
    }

    private static void syncDebugger() {
        if (State.debugger != null) {
            State.debugger.step();
        }
    }

    private static void syncEmulator(boolean immediately) {
        if (log.isDebugEnabled()) {
            log.debug("syncEmulator immediately=" + immediately);
        }

        Modules.sceGe_userModule.step();
		Modules.sceDisplayModule.step(immediately);
    }

    private static void syncFast() {
        // Always sync the display to trigger the GE list processing
        Modules.sceDisplayModule.step();
    }

    public static void sync() throws StopThreadException {
    	do {
    		wantSync = false;

	    	if (!IntrManager.getInstance().canExecuteInterruptNow() && javaThreadScheduling) {
	    		syncFast();
	    	} else {
		    	syncPause();
				Emulator.getScheduler().step();
				if (processor.isInterruptsEnabled()) {
					Modules.ThreadManForUserModule.hleRescheduleCurrentThread();
				}
				syncThread();
				syncEmulator(false);
		        syncDebugger();
		    	syncPause();
		    	checkStoppedThread();
	        }
    	// Check if a new sync request has been received in the meantime
    	} while (wantSync);
    }

    public static void preSyscall() throws StopThreadException {
    	if (IntrManager.getInstance().canExecuteInterruptNow()) {
	    	syscallRuntimeThread = getRuntimeThread();
	    	if (syscallRuntimeThread != null) {
	    		syscallRuntimeThread.setInSyscall(true);
	    	}
	    	checkStoppedThread();
	    	syncPause();
    	}
    }

    public static void postSyscall() throws StopThreadException {
    	if (!IntrManager.getInstance().canExecuteInterruptNow()) {
    		postSyscallFast();
    	} else {
	    	checkStoppedThread();
	    	sync();
	    	if (syscallRuntimeThread != null) {
	    		syscallRuntimeThread.setInSyscall(false);
	    	}
    	}
    }

    /**
     * When calling an HLE module function from another HLE module, the call
     * has to be encapsulated into this hleSyscall() so that a thread switching
     * can occur if required.
     * E.g.:
     *   int result = hleSyscall(Modules.IoFileMgrForUserModule.sceIoRead(id, buffer, size));
     *
     * @param result the result value received from the HLE syscall function.
     *
     * @return the result value received, after having performed a thread switch if required.
     * @throws StopThreadRuntimeException 
     */
    public static int hleSyscall(int result) throws StopThreadRuntimeException {
    	if (result >= 0) {
			try {
				postSyscall();
			} catch (StopThreadException e) {
				// For convenience, throw an exception not requiring a "throws"
				throw new StopThreadRuntimeException(e.getMessage());
			}
    	}

    	return result;
    }

    public static void postSyscallFast() {
    	syncFast();
    }

    public static void postSyscallLLE() {
        Modules.sceDisplayModule.step();
    	checkSync();
    }

    public static int syscallFast(int code, boolean inDelaySlot) throws Exception {
		// Fast syscall: no context switching
    	int continueAddress = SyscallHandler.syscall(code, inDelaySlot);
    	postSyscallFast();

    	return continueAddress;
    }

    public static int syscall(int code, boolean inDelaySlot) throws Exception {
    	preSyscall();
    	int continueAddress = SyscallHandler.syscall(code, inDelaySlot);
    	postSyscall();

    	return continueAddress;
    }

    public static int syscallLLE(int code, boolean inDelaySlot) throws Exception {
    	int continueAddress = SyscallHandler.syscall(code, inDelaySlot);
    	postSyscallLLE();

    	return continueAddress;
    }

    private static void execWithReturnAddress(IExecutable executable, int returnAddress) throws Exception {
    	while (true) {
    		try {
    			int address = executable.exec();
    			if (address != returnAddress) {
    				jump(address, returnAddress);
    			}
    			break;
			} catch (StackPopException e) {
				log.debug("Stack exceeded maximum size, shrinking to top level");

				executable = getExecutable(e.getRa());
				if (executable == null) {
					throw e;
				}
			} catch (ResetException e) {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Processor has to been reset to pc=0x%08X", e.getPc()));
				}

				executable = getExecutable(e.getPc());
				if (executable == null) {
					throw e;
				}
			}
		}
    }

    public static boolean executeFunction(int address) {
		IExecutable executable = getExecutable(address);
		if (executable == null) {
			return false;
		}

		int newPc = 0;
        int returnAddress = cpu._ra;
        boolean exception = false;
		try {
			execWithReturnAddress(executable, returnAddress);
			newPc = returnAddress;
		} catch (StopThreadException e) {
			// Ignore exception
		} catch (StopThreadRuntimeException e) {
			// Ignore exception
		} catch (Exception e) {
			log.error("Catched Throwable in executeCallback:", e);
			exception = true;
		}
    	cpu.pc = newPc;
    	cpu.npc = newPc; // npc is used when context switching

    	return exception;
    }

    public static void runThread(RuntimeThread thread) {
    	setLog4jMDC();
    	SoundChannel.setThreadInitContext();

    	thread.setInSyscall(true);

    	if (isStoppedThread()) {
			// This thread has already been stopped before it is really starting...
        	SoundChannel.clearThreadInitContext();
    		return;
    	}

		thread.suspendRuntimeExecution();

    	if (isStoppedThread()) {
			// This thread has already been stopped before it is really starting...
        	SoundChannel.clearThreadInitContext();
    		return;
    	}

    	thread.onThreadStart();

        ThreadManForUser threadMan = Modules.ThreadManForUserModule;

        IExecutable executable = getExecutable(processor.cpu.pc);
		thread.setInSyscall(false);
    	try {
    		updateStaticVariables();

    		// Execute any thread event handler for THREAD_EVENT_START
    		// in the thread context, before starting the thread execution.
    		threadMan.checkPendingCallbacks();

    		execWithReturnAddress(executable, ThreadManForUser.THREAD_EXIT_HANDLER_ADDRESS);
            // NOTE: When a thread exits by itself (without calling sceKernelExitThread),
            // it's exitStatus becomes it's return value.
    		threadMan.hleKernelExitThread(processor.cpu._v0);
    	} catch (StopThreadException e) {
    		// Ignore Exception
    	} catch (StopThreadRuntimeException e) {
    		// Ignore Exception
    	} catch (Throwable e) {
    		// Do not spam exceptions when exiting...
        	if (!exitCalled()) {
	    		// Log error in log file and command box
	    		log.error("Catched Throwable in RuntimeThread:", e);
	    		e.printStackTrace();
        	}
		}

    	SoundChannel.clearThreadInitContext();

		SceKernelThreadInfo threadInfo = thread.getThreadInfo();
    	alreadyStoppedThreads.put(threadInfo, thread);

    	if (log.isDebugEnabled()) {
    		log.debug("End of Thread " + threadInfo.name + " - stopped");
    	}

    	// Tell stopAllThreads that this thread is stopped.
    	thread.setInSyscall(true);

    	threads.remove(threadInfo);
		toBeStoppedThreads.remove(threadInfo);
		toBeDeletedThreads.remove(threadInfo);

		if (!reset) {
			// Switch to the currently active thread
			try {
		    	if (log.isDebugEnabled()) {
		    		log.debug("End of Thread " + threadInfo.name + " - sync");
		    	}

		    	// Be careful to not execute Interrupts or Callbacks by this thread,
		    	// as it is already stopped and the next active thread
		    	// will be resumed immediately.
	    		syncIdle();
	    		syncThreadImmediately();
			} catch (StopThreadException e) {
	    		// Ignore Exception
	    	} catch (StopThreadRuntimeException e) {
	    		// Ignore Exception
			}
		}

		alreadyStoppedThreads.remove(threadInfo);
		alreadySwitchedStoppedThreads.remove(thread);

		if (log.isDebugEnabled()) {
			log.debug("End of Thread " + thread.getName());
		}

		synchronized (waitForEnd) {
			waitForEnd.notify();
		}
    }

    private static void computeCodeBlocksRange() {
    	codeBlocksLowestAddress = Integer.MAX_VALUE;
    	codeBlocksHighestAddress = Integer.MIN_VALUE;
    	for (CodeBlock codeBlock : codeBlocks.values()) {
    		if (!codeBlock.isInternal()) {
        		int lowestAddress = codeBlock.getLowestAddress() & addressMask;
        		int highestAddress = codeBlock.getHighestAddress() & addressMask;
	    		codeBlocksLowestAddress = Math.min(codeBlocksLowestAddress, lowestAddress);
	    		codeBlocksHighestAddress = Math.max(codeBlocksHighestAddress, highestAddress);
    		}
    	}
    }

    public static void addCodeBlock(int address, CodeBlock codeBlock) {
    	int maskedAddress = address & addressMask;
    	CodeBlock previousCodeBlock = codeBlocks.put(maskedAddress, codeBlock);

    	if (!codeBlock.isInternal()) {
    		int lowestAddress = codeBlock.getLowestAddress() & addressMask;
    		int highestAddress = codeBlock.getHighestAddress() & addressMask;

	    	if (previousCodeBlock != null) {
	    		// One code block has been deleted, recompute the whole code blocks range
	    		computeCodeBlocksRange();

	    		int fastExecutableLoopukIndex = (maskedAddress - MemoryMap.START_RAM) >> 2;
	    		if (fastExecutableLoopukIndex >= 0 && fastExecutableLoopukIndex < fastExecutableLookup.length) {
	    			fastExecutableLookup[fastExecutableLoopukIndex] = null;
	    		}
	    	} else {
	    		// One new code block has been added, update the code blocks range
	    		codeBlocksLowestAddress = Math.min(codeBlocksLowestAddress, lowestAddress);
	    		codeBlocksHighestAddress = Math.max(codeBlocksHighestAddress, highestAddress);
	    	}

	    	int startIndex = (lowestAddress - MemoryMap.START_RAM) >> fastCodeBlockLookupShift;
    		int endIndex = (highestAddress - MemoryMap.START_RAM) >> fastCodeBlockLookupShift;
    		for (int i = startIndex; i <= endIndex; i++) {
    			if (i >= 0 && i < fastCodeBlockLookup.length) {
    				CodeBlockList codeBlockList = fastCodeBlockLookup[i];
    				if (codeBlockList != null) {
    					if (previousCodeBlock != null) {
    						codeBlockList.remove(previousCodeBlock);
    					}
    					int addr = (i << fastCodeBlockLookupShift) + MemoryMap.START_RAM;
    					int size = 1 << fastCodeBlockLookupShift;
    					if (codeBlock.isOverlappingWithAddressRange(addr, size)) {
    						codeBlockList.add(codeBlock);
    					}
    				}
    			}
    		}
    	}
    }

    public static CodeBlock getCodeBlock(int address) {
	    return codeBlocks.get(address & addressMask);
	}

    public static boolean hasCodeBlock(int address) {
        return codeBlocks.containsKey(address & addressMask);
    }

    public static Map<Integer, CodeBlock> getCodeBlocks() {
    	return codeBlocks;
    }

    public static void removeCodeBlocks(int address, int size) {
    	int[] addressesToBeRemoved = null;

		for (CodeBlock codeBlock : codeBlocks.values()) {
			if (codeBlock.isOverlappingWithAddressRange(address, size)) {
				addressesToBeRemoved = Utilities.add(addressesToBeRemoved, codeBlock.getStartAddress());
			}
    	}

		if (addressesToBeRemoved != null) {
			for (int addressToBeRemoved : addressesToBeRemoved) {
				CodeBlock codeBlock = codeBlocks.remove(addressToBeRemoved & addressMask);
				if (log.isDebugEnabled()) {
					log.debug(String.format("removeCodeBlocks address=0x%08X, size=0x%X, removing %s", address, size, codeBlock));
				}
			}
		}
    }

    public static IExecutable getExecutable(int address) {
    	int maskedAddress = address & addressMask;
    	// Check if we have already the executable in the fastExecutableLookup array
		int fastExecutableLoopukIndex = (maskedAddress - MemoryMap.START_RAM) >> 2;
		IExecutable executable;
		if (fastExecutableLoopukIndex >= 0 && fastExecutableLoopukIndex < fastExecutableLookup.length) {
			executable = fastExecutableLookup[fastExecutableLoopukIndex];
		} else {
			executable = null;
		}

		if (executable == null) {
	        CodeBlock codeBlock = getCodeBlock(address);
	        if (codeBlock == null) {
	            executable = Compiler.getInstance().compile(address);
	        } else {
	            executable = codeBlock.getExecutable();
	        }

	        // Store the executable in the fastExecutableLookup array
			if (fastExecutableLoopukIndex >= 0 && fastExecutableLoopukIndex < fastExecutableLookup.length) {
	    		fastExecutableLookup[fastExecutableLoopukIndex] = executable;
			}
		}

        return executable;
    }

    public static void start() {
    	Settings.getInstance().registerSettingsListener("RuntimeContext", "emu.compiler", new CompilerEnabledSettingsListerner());
    }

    public static void run() {
    	if (exitCalled()) {
    		return;
    	}
    	if (!initialise()) {
        	compilerEnabled = false;
        	return;
        }

        log.info("Using Compiler");

        while (!toBeStoppedThreads.isEmpty()) {
        	wakeupToBeStoppedThreads();
        	sleep(idleSleepMicros);
        }

        reset = false;

        if (currentRuntimeThread == null) {
        	try {
				syncIdle();
			} catch (StopThreadException e) {
				// Thread is stopped, return immediately
				return;
	    	} catch (StopThreadRuntimeException e) {
				// Thread is stopped, return immediately
				return;
			}

        	if (currentRuntimeThread == null) {
        		log.error("RuntimeContext.run: nothing to run!");
        		Emulator.PauseEmuWithStatus(Emulator.EMU_STATUS_UNKNOWN);
        		return;
        	}
        }

        update();

        if (processor.cpu.pc == 0) {
        	Emulator.PauseEmuWithStatus(Emulator.EMU_STATUS_UNKNOWN);
        	return;
        }

        currentRuntimeThread.continueRuntimeExecution();

        while (!threads.isEmpty() && !reset) {
	        synchronized(waitForEnd) {
	        	try {
					waitForEnd.wait();
				} catch (InterruptedException e) {
				}
	        }
        }

        log.debug("End of run");
    }

    private static List<RuntimeThread> wakeupToBeStoppedThreads() {
		List<RuntimeThread> threadList = new LinkedList<RuntimeThread>();
		synchronized (toBeStoppedThreads) {
    		for (Entry<SceKernelThreadInfo, RuntimeThread> entry : toBeStoppedThreads.entrySet()) {
    			threadList.add(entry.getValue());
    		}
		}

		// Trigger the threads to start execution again.
		// Loop on a local list to avoid concurrent modification on toBeStoppedThreads.
		for (RuntimeThread runtimeThread : threadList) {
			Thread.State threadState = runtimeThread.getState();
			log.debug("Thread " + runtimeThread.getName() + ", State=" + threadState);
			if (threadState == Thread.State.TERMINATED) {
				toBeStoppedThreads.remove(runtimeThread.getThreadInfo());
			} else if (threadState == Thread.State.WAITING) {
				runtimeThread.continueRuntimeExecution();
			}
		}

		synchronized (Emulator.getInstance()) {
			Emulator.getInstance().notifyAll();
		}

		return threadList;
    }

    public static void onThreadDeleted(SceKernelThreadInfo thread) {
    	RuntimeThread runtimeThread = threads.get(thread);
    	if (runtimeThread != null) {
    		if (log.isDebugEnabled()) {
    			log.debug("Deleting Thread " + thread.toString());
    		}
    		toBeStoppedThreads.put(thread, runtimeThread);
    		if (runtimeThread.isInSyscall() && Thread.currentThread() != runtimeThread) {
    			toBeDeletedThreads.put(thread, runtimeThread);
    			log.debug("Continue Thread " + runtimeThread.getName());
    			runtimeThread.continueRuntimeExecution();
    		}
    	}
    }

    public static void onThreadExit(SceKernelThreadInfo thread) {
    	RuntimeThread runtimeThread = threads.get(thread);
    	if (runtimeThread != null) {
    		if (log.isDebugEnabled()) {
    			log.debug("Exiting Thread " + thread.toString());
    		}
    		toBeStoppedThreads.put(thread, runtimeThread);
    		threads.remove(thread);
    	}
    }

    public static void onThreadStart(SceKernelThreadInfo thread) {
    	// The thread is starting, if a stop was still pending, cancel the stop.
    	toBeStoppedThreads.remove(thread);
    	toBeDeletedThreads.remove(thread);
    }

    private static void stopAllThreads() {
		synchronized (threads) {
			toBeStoppedThreads.putAll(threads);
    		threads.clear();
		}

		List<RuntimeThread> threadList = wakeupToBeStoppedThreads();

		// Wait for all threads to enter a syscall.
		// When a syscall is entered, the thread will exit
		// automatically by calling checkStoppedThread()
		boolean waitForThreads = true;
		while (waitForThreads) {
			waitForThreads = false;
			for (RuntimeThread runtimeThread : threadList) {
				if (!runtimeThread.isInSyscall()) {
					waitForThreads = true;
					break;
				}
			}

			if (waitForThreads) {
				sleep(idleSleepMicros);
			}
		}
    }

    public static void exit() {
        if (compilerEnabled) {
    		log.debug("RuntimeContext.exit");
        	stopAllThreads();
        	if (DurationStatistics.collectStatistics) {
        		log.info(idleDuration.toString());
        	}

            if (enableInstructionTypeCounting) {
            	long totalCount = 0;
            	for (Instruction insn : instructionTypeCounts.keySet()) {
            		int count = instructionTypeCounts.get(insn);
            		totalCount += count;
            	}

            	while (!instructionTypeCounts.isEmpty()) {
            		Instruction highestCountInsn = null;
            		int highestCount = -1;
                	for (Instruction insn : instructionTypeCounts.keySet()) {
                		int count = instructionTypeCounts.get(insn);
                		if (count > highestCount) {
                			highestCount = count;
                			highestCountInsn = insn;
                		}
                	}
                	instructionTypeCounts.remove(highestCountInsn);
            		log.info(String.format("  %10s %s %d (%2.2f%%)", highestCountInsn.name(), (highestCountInsn.hasFlags(Instruction.FLAG_INTERPRETED) ? "I" : "C"), highestCount, highestCount * 100.0 / totalCount));
            	}
            }
        }
    }

    public static void reset() {
    	if (compilerEnabled) {
    		log.debug("RuntimeContext.reset");
    		onReboot();
    		currentThread = null;
    		currentRuntimeThread = null;
    		reset = true;
    		stopAllThreads();
    		synchronized (waitForEnd) {
				waitForEnd.notify();
			}
    	}
    }

    public static void onReboot() {
    	invalidateAllCodeBlocks();
		haltCount = 0;
    }

    private static void invalidateAllCodeBlocks() {
		for (CodeBlock codeBlock : codeBlocks.values()) {
			codeBlock.free();
		}
        codeBlocks.clear();
		if (fastExecutableLookup != null) {
			Arrays.fill(fastExecutableLookup, null);
		}
		if (fastCodeBlockLookup != null) {
			Arrays.fill(fastCodeBlockLookup, null);
		}
        Compiler.getInstance().reset();
    }

    public static void invalidateAll() {
        if (compilerEnabled) {
    		if (invalidateAllCodeBlocks) {
    			// Simple method: invalidate all the code blocks,
    			// independently if their were modified or not.
        		log.debug("RuntimeContext.invalidateAll simple");
        		invalidateAllCodeBlocks();
    		} else {
    			// Advanced method: check all the code blocks for a modification
    			// of their opcodes and invalidate only those code blocks that
    			// have been modified.
        		log.debug("RuntimeContext.invalidateAll advanced");

        		// Make sure that the enableDebugger flag is set correctly as it is used by the code block validation
        		initialiseDebugger();

        		Compiler compiler = Compiler.getInstance();
	    		for (CodeBlock codeBlock : codeBlocks.values()) {
	    			boolean isNoLongerValid = codeBlock.isNoLongerValid();
	    			if (log.isDebugEnabled()) {
	    				log.debug(String.format("invalidateAll %s: isNoLongerValid %b", codeBlock, isNoLongerValid));
	    			}

	    			if (isNoLongerValid) {
	    				compiler.invalidateCodeBlock(codeBlock);
	    			}
	    		}
    		}
    	}
    }

    private static void invalidateRangeFullCheck(int addr, int size) {
		Compiler compiler = Compiler.getInstance();
    	for (CodeBlock codeBlock : codeBlocks.values()) {
			if (size == 0x4000 && codeBlock.getHighestAddress() >= addr) {
    			// Some applications do not clear more than 16KB as this is the size of the complete Instruction Cache.
    			// Be conservative in this case and check any code block above the given address.
				compiler.checkCodeBlockValidity(codeBlock);
			} else if (codeBlock.isOverlappingWithAddressRange(addr, size)) {
				compiler.checkCodeBlockValidity(codeBlock);
    		}
    	}
    }

    private static CodeBlockList fillFastCodeBlockList(int index) {
		int startAddr = (index << fastCodeBlockLookupShift)  + MemoryMap.START_RAM;
		int size = 1 << fastCodeBlockLookupShift;
		if (log.isDebugEnabled()) {
			log.debug(String.format("Creating new fastCodeBlockList for 0x%08X", startAddr));
		}

		CodeBlockList codeBlockList = new CodeBlockList();
		for (CodeBlock codeBlock : codeBlocks.values()) {
			if (codeBlock.isOverlappingWithAddressRange(startAddr, size)) {
				codeBlockList.add(codeBlock);
			}
		}
		fastCodeBlockLookup[index] = codeBlockList;

		return codeBlockList;
    }

    public static void invalidateRange(int addr, int size) {
        if (compilerEnabled) {
        	addr &= Memory.addressMask;

        	if (log.isDebugEnabled()) {
        		log.debug(String.format("RuntimeContext.invalidateRange(addr=0x%08X, size=%d)", addr, size));
        	}

        	// Fast check: if the address range is outside the largest code blocks range,
        	// there is noting to do.
        	if (addr + size < codeBlocksLowestAddress || addr > codeBlocksHighestAddress) {
        		return;
        	}

        	// Check if the code blocks located in the given range have to be invalidated
        	if (size == fastCodeBlockSize) {
        		// This is a fast track to avoid checking all the code blocks
        		int startIndex = (addr - MemoryMap.START_RAM) >> fastCodeBlockLookupShift;
				int endIndex = (addr + size - MemoryMap.START_RAM) >> fastCodeBlockLookupShift;
				if (startIndex >= 0 && endIndex < fastCodeBlockLookup.length) {
					for (int index = startIndex; index <= endIndex; index++) {
	        			CodeBlockList codeBlockList = fastCodeBlockLookup[index];
	        			if (codeBlockList == null) {
	        				codeBlockList = fillFastCodeBlockList(index);
	        			} else {
	        				if (log.isDebugEnabled()) {
	        					log.debug(String.format("Reusing fastCodeBlockList for 0x%08X (size=%d)", addr, codeBlockList.size()));
	        				}
	        			}

	            		Compiler compiler = Compiler.getInstance();
	            		for (CodeBlock codeBlock : codeBlockList) {
	            			if (codeBlock.isOverlappingWithAddressRange(addr, size)) {
	            				compiler.checkCodeBlockValidity(codeBlock);
	            			}
	            		}
					}
        		} else {
            		invalidateRangeFullCheck(addr, size);
        		}
        	} else {
        		invalidateRangeFullCheck(addr, size);
        	}
    	}
    }

    public static void instructionTypeCount(Instruction insn, int opcode) {
    	int count = 0;
    	if (instructionTypeCounts.containsKey(insn)) {
    		count = instructionTypeCounts.get(insn);
    	}
    	count++;
    	instructionTypeCounts.put(insn, count);
    }

    public static void pauseEmuWithStatus(int status) throws StopThreadException {
    	Emulator.PauseEmuWithStatus(status);
    	syncPause();
    }

    public static void logError(String message) {
    	log.error(message);
    }

    public static boolean checkMemoryPointer(int address) {
        if (!Memory.isAddressGood(address)) {
            if (!Memory.isRawAddressGood(Memory.normalizeAddress(address))) {
                return false;
            }
        }

        return true;
    }

    public static String readStringNZ(int address, int maxLength) {
    	if (address == 0) {
    		return null;
    	}
    	return Utilities.readStringNZ(address, maxLength);
    }

    public static PspString readPspStringNZ(int address, int maxLength, boolean canBeNull) {
    	return new PspString(address, maxLength, canBeNull);
    }

    public static int checkMemoryRead32(int address, int pc) throws StopThreadException {
        int rawAddress = address & Memory.addressMask;
        if (!Memory.isRawAddressGood(rawAddress)) {
        	if (memory.read32AllowedInvalidAddress(rawAddress)) {
        		rawAddress = 0;
        	} else {
                int normalizedAddress = Memory.normalizeAddress(address);
                if (Memory.isRawAddressGood(normalizedAddress)) {
                    rawAddress = normalizedAddress;
                } else {
		            processor.cpu.pc = pc;
		            memory.invalidMemoryAddress(address, "read32", Emulator.EMU_STATUS_MEM_READ);
		            syncPause();
		            rawAddress = 0;
                }
        	}
        }

        return rawAddress;
    }

    public static int checkMemoryRead16(int address, int pc) throws StopThreadException {
        int rawAddress = address & Memory.addressMask;
        if (!Memory.isRawAddressGood(rawAddress)) {
            int normalizedAddress = Memory.normalizeAddress(address);
            if (Memory.isRawAddressGood(normalizedAddress)) {
                rawAddress = normalizedAddress;
            } else {
	            processor.cpu.pc = pc;
	            memory.invalidMemoryAddress(address, "read16", Emulator.EMU_STATUS_MEM_READ);
	            syncPause();
	            rawAddress = 0;
            }
        }

        return rawAddress;
    }

    public static int checkMemoryRead8(int address, int pc) throws StopThreadException {
        int rawAddress = address & Memory.addressMask;
        if (!Memory.isRawAddressGood(rawAddress)) {
            int normalizedAddress = Memory.normalizeAddress(address);
            if (Memory.isRawAddressGood(normalizedAddress)) {
                rawAddress = normalizedAddress;
            } else {
	            processor.cpu.pc = pc;
	            memory.invalidMemoryAddress(address, "read8", Emulator.EMU_STATUS_MEM_READ);
	            syncPause();
	            rawAddress = 0;
            }
        }

        return rawAddress;
    }

    public static int checkMemoryWrite32(int address, int pc) throws StopThreadException {
        int rawAddress = address & Memory.addressMask;
        if (!Memory.isRawAddressGood(rawAddress)) {
            int normalizedAddress = Memory.normalizeAddress(address);
            if (Memory.isRawAddressGood(normalizedAddress)) {
                rawAddress = normalizedAddress;
            } else {
	            processor.cpu.pc = pc;
	            memory.invalidMemoryAddress(address, "write32", Emulator.EMU_STATUS_MEM_WRITE);
	            syncPause();
	            rawAddress = 0;
            }
        }

        sceDisplayModule.write32(rawAddress);

        return rawAddress;
    }

    public static int checkMemoryWrite16(int address, int pc) throws StopThreadException {
        int rawAddress = address & Memory.addressMask;
        if (!Memory.isRawAddressGood(rawAddress)) {
            int normalizedAddress = Memory.normalizeAddress(address);
            if (Memory.isRawAddressGood(normalizedAddress)) {
                rawAddress = normalizedAddress;
            } else {
	            processor.cpu.pc = pc;
	            memory.invalidMemoryAddress(address, "write16", Emulator.EMU_STATUS_MEM_WRITE);
	            syncPause();
	            rawAddress = 0;
            }
        }

        sceDisplayModule.write16(rawAddress);

        return rawAddress;
    }

    public static int checkMemoryWrite8(int address, int pc) throws StopThreadException {
        int rawAddress = address & Memory.addressMask;
        if (!Memory.isRawAddressGood(rawAddress)) {
            int normalizedAddress = Memory.normalizeAddress(address);
            if (Memory.isRawAddressGood(normalizedAddress)) {
                rawAddress = normalizedAddress;
            } else {
	            processor.cpu.pc = pc;
	            memory.invalidMemoryAddress(address, "write8", Emulator.EMU_STATUS_MEM_WRITE);
	            syncPause();
	            rawAddress = 0;
            }
        }

        sceDisplayModule.write8(rawAddress);

        return rawAddress;
    }

    public static void debugMemoryReadWrite(int address, int value, int pc, boolean isRead, int width) {
    	if (log.isTraceEnabled()) {
	    	StringBuilder message = new StringBuilder();
	    	message.append(String.format("0x%08X - ", pc));
	    	if (isRead) {
	    		message.append(String.format("read%d(0x%08X)=0x", width, address));
	    		if (width == 8) {
	    			message.append(String.format("%02X", memory.read8(address)));
	    		} else if (width == 16) {
	    			message.append(String.format("%04X", memory.read16(address)));
	    		} else if (width == 32) {
	    			message.append(String.format("%08X (%f)", memory.read32(address), Float.intBitsToFloat(memory.read32(address))));
	    		}
	    	} else {
	    		message.append(String.format("write%d(0x%08X, 0x", width, address));
	    		if (width == 8) {
	    			message.append(String.format("%02X", value));
	    		} else if (width == 16) {
	    			message.append(String.format("%04X", value));
	    		} else if (width == 32) {
	    			message.append(String.format("%08X (%f)", value, Float.intBitsToFloat(value)));
	    		}
	    		message.append(")");
	    	}
	    	log.trace(message.toString());
    	}
    }

    /*
     * Notify the thread waiting on the idleSyncObject that
     * the idle should be ended.
     */
    private static void interruptIdleSleep() {
    	synchronized (idleSyncObject) {
        	// Interrupt the thread waiting on the idleSyncObject
        	idleSyncObject.notifyAll();
		}
    }

    public static void onNextScheduleModified() {
		checkSync();

		interruptIdleSleep();
    }

    public static void checkSync() {
    	if (log.isTraceEnabled()) {
    		log.trace(String.format("checkSync wantSync=%b, now=0x%X", wantSync, Scheduler.getNow()));
    	}

    	if (!wantSync) {
    		long delay = Emulator.getScheduler().getNextActionDelay(idleSleepMicros);
    		if (delay <= 0) {
    			wantSync = true;

    			if (log.isTraceEnabled()) {
    	    		log.trace(String.format("checkSync wantSync=%b, now=0x%X", wantSync, Scheduler.getNow()));
    	    	}
    		}
    	}
    }

    public static void checkSyncWithSleep() {
    	long delay = Emulator.getScheduler().getNextActionDelay(idleSleepMicros);

//    	if (log.isTraceEnabled()) {
//    		log.debug(String.format("checkSyncWithSleep delay=0x%X", delay));
//    	}

    	if (delay > 0) {
    		if (ExternalGE.isActive() || Modules.sceDisplayModule.isUsingSoftwareRenderer()) {
        		idleSleepInterruptable();
    		} else {
    			//
    			// When using the OpenGL renderer, the Java "Object.wait(1, 0)" method
    			// is sometimes taking several milliseconds to complete (up to 15ms)
    			// even though we are requesting to only wait for 1ms or less.
    			// These long delays are happening sporadically only when two different
    			// threads are using the Object.wait() method at the same time.
    			//
    			// I don't know why, but this behaviour is only happening using the OpenGL
    			// renderer, it doesn't happen using the external or internal software
    			// renderer, even though two different threads are also using the
    			// Object.wait() method at the same time.
    			// Maybe this is related to the Lwjgl library implementing the OpenGL interface.
    			//
    			// As a work-around, we are forcing the "Sync Daemon" thread to
    			// not call Object.wait() when using the OpenGL renderer.
    			// The "Sync Daemon" thread is then using a Thread.sleep() call.
    			//
    			sleep(Math.min((int) delay, idleSleepMicros));
    		}
    	} else if (wantSync) {
			sleep(idleSleepMicros);
    	} else {
    		wantSync = true;
    	}
    }

    public static boolean syncDaemonStep() {
    	checkSyncWithSleep();

    	return enableDaemonThreadSync;
    }

    public static void exitSyncDaemon() {
    	runtimeSyncThread = null;
    }

    public static void setIsHomebrew(boolean isHomebrew) {
    	RuntimeContext.isHomebrew = isHomebrew;
    }

    public static boolean isHomebrew() {
    	return isHomebrew;
    }

    public static void onCodeModification(int pc, int opcode) {
    	cpu.pc = pc;
    	log.error(String.format("Code instruction at 0x%08X has been modified, expected 0x%08X, current 0x%08X", pc, opcode, memory.read32(pc)));
    	Emulator.PauseEmuWithStatus(Emulator.EMU_STATUS_MEM_WRITE);
    }

    public static void debugMemory(int address, int length) {
    	if (memory instanceof DebuggerMemory) {
    		DebuggerMemory debuggerMemory = (DebuggerMemory) memory;
    		debuggerMemory.addRangeReadWriteBreakpoint(address, address + length - 1);
    	}
    }

    public static void debugCodeBlock(int address, int numberOfArguments) {
    	if (debugCodeBlockCalls) {
    		debugCodeBlocks.put(address, numberOfArguments);
    	}
    }

    public static void setFirmwareVersion(int firmwareVersion) {
    	RuntimeContext.firmwareVersion = firmwareVersion;
    }

    public static boolean hasMemoryInt() {
    	return memoryInt != null;
    }

    public static boolean hasMemoryInt(int address) {
    	return hasMemoryInt() && Memory.isAddressGood(address);
    }

    public static boolean hasMemoryInt(TPointer address) {
    	return hasMemoryInt() && address.getMemory() == memory;
    }

    public static int[] getMemoryInt() {
    	return memoryInt;
    }

    public static int getPc() {
    	return RuntimeContextLLE.getProcessor().cpu.pc;
    }

    public static int executeEret() throws Exception {
    	int epc = processor.cpu.doERET(processor);

    	reboot.setLog4jMDC(processor);
		reboot.dumpAllThreads();

    	return epc;
    }

    private static int haltCount = 0;
    public static void executeHalt(Processor processor) throws StopThreadException {
    	if (RuntimeContextLLE.hasMMIO()) {
    		if (processor.cp0.isMediaEngineCpu()) {
				((MEProcessor) processor).halt();
    		} else {
    			if (log.isDebugEnabled()) {
    				log.debug(String.format("Allegrex halt pendingInterruptIPbitsMain=0x%X", RuntimeContextLLE.pendingInterruptIPbitsMain));
    			}
	    		reboot.dumpAllThreads();
	    		if (false) {
	    			reboot.dumpAllModulesAndLibraries();
	    		}

	    		// Simulate an interrupt exception
	    		switch (haltCount) {
	    			case 0:
	    				// The module_start of display_01g.prx is requiring at least one VBLANK interrupt
	    				// as it is executing sceDisplayWaitVblankStart().
	    				MMIOHandlerDisplayController.getInstance().triggerVblankInterrupt();
	    				break;
	    			case 1:
	    				// The init callback (sub_00000B08 registered by sceKernelSetInitCallback())
	    				// of display_01g.prx is requiring at least one VBLANK interrupt
	    				// as it is executing sceDisplayWaitVblankStart().
	    				MMIOHandlerDisplayController.getInstance().triggerVblankInterrupt();
	    				break;
	    			case 2:
	    				// The thread SCE_VSH_GRAPHICS is calling sceDisplayWaitVblankStart().
	    				MMIOHandlerDisplayController.getInstance().triggerVblankInterrupt();
	    				break;
	    			case 3:
	    				// The thread SCE_VSH_GRAPHICS is calling a function from paf.prx waiting for a vblank.
	    				MMIOHandlerDisplayController.setMaxVblankInterrupts(-1);
	    				MMIOHandlerDisplayController.getInstance().triggerVblankInterrupt();
	    				break;
	    			default:
	    				break;
	    		}
	    		haltCount++;

	    		idle();
    		}
    	} else {
        	log.error("Allegrex halt");
    		Emulator.PauseEmuWithStatus(Emulator.EMU_STATUS_HALT);
    	}
    }

    public static void idle() throws StopThreadException {
    	if (log.isDebugEnabled()) {
    		log.debug(String.format("idle wantSync=%b", wantSync));
    	}

		if (!javaThreadScheduling) {
			if (!toBeStoppedThreads.isEmpty()) {
				wantSync = true;
			}
			Emulator.getScheduler().step();
		}

		if (wantSync) {
    		sync();
    	} else {
    		idleSleepInterruptable();
    	}
    }

    public static void onLLEInterrupt() {
    	interruptIdleSleep();
    }

    public static void setLog4jMDC() {
    	setLog4jMDC(Thread.currentThread().getName());
    }

    public static void setLog4jMDC(String threadName) {
    	setLog4jMDC(threadName, 0);
    }

    public static void setLog4jMDC(String threadName, int threadUid) {

        // @FIXME: bro..
        /*
		MDC.put("LLE-thread-name", threadName);

		if (threadUid != 0) {
			MDC.put("LLE-thread-uid", String.format("0x%X", threadUid));
			MDC.put("LLE-thread", String.format("%s_0x%X", threadName, threadUid));
		} else {
			MDC.put("LLE-thread-uid", "");
			MDC.put("LLE-thread", threadName);
		}
         */
    }
}
