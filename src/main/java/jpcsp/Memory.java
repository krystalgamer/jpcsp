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
package jpcsp;

import static jpcsp.Allegrex.compiler.RuntimeContext.getPc;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import jpcsp.HLE.Modules;
import jpcsp.graphics.VideoEngine;
import jpcsp.hardware.Screen;
import jpcsp.memory.DebuggerMemory;
import jpcsp.memory.DirectBufferMemory;
import jpcsp.memory.FastMemory;
import jpcsp.memory.IMemoryReader;
import jpcsp.memory.IMemoryWriter;
import jpcsp.memory.MemoryReader;
import jpcsp.memory.MemoryWriter;
import jpcsp.memory.NativeMemory;
import jpcsp.memory.SafeDirectBufferMemory;
import jpcsp.memory.SafeFastMemory;
import jpcsp.memory.SafeNativeMemory;
import jpcsp.memory.SafeSparseNativeMemory;
import jpcsp.memory.SparseNativeMemory;
import jpcsp.memory.StandardMemory;
import jpcsp.settings.AbstractBoolSettingsListener;
import jpcsp.settings.Settings;
import jpcsp.state.IState;
import jpcsp.state.StateInputStream;
import jpcsp.state.StateOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Memory implements IState {
    public static Logger log = LoggerFactory.getLogger("memory");
    private static Memory instance = null;
    public static boolean useNativeMemory = false;
    public static boolean useDirectBufferMemory = false;
    public static boolean useSafeMemory = true;
    public static final int addressMask = 0x1FFFFFFF;
    private boolean ignoreInvalidMemoryAccess = false;
    protected static final int MEMORY_PAGE_SHIFT = 12;
    protected static final boolean[] validMemoryPage = new boolean[1 << (Integer.SIZE - MEMORY_PAGE_SHIFT)];
    // Assume that a video check during a memcpy is only necessary
    // when copying at least one screen row (at 2 bytes per pixel).
    private static final int MINIMUM_LENGTH_FOR_VIDEO_CHECK = Screen.width * 2;
	private static final int STATE_VERSION = 0;

	public static Memory getInstance() {
        if (instance == null) {
            //
            // The following memory implementations are available:
            // - StandardMemory        :  low memory requirements, performs address checking, slow
            // - SafeFastMemory        : high memory requirements, performs address checking, fast
            // - FastMemory            : high memory requirements, no address checking, very fast
            // - SafeDirectBufferMemory: high memory requirements, performs address checking, moderate
            // - DirectBufferMemory    : high memory requirements, no address checking, fast
            //
            // Best choices are currently
            // 1) SafeFastMemory (address check is useful when debugging programs)
            // 2) StandardMemory when available memory is not sufficient for 1st choice
            //

        	boolean useDebuggerMemory = false;
            if (Settings.getInstance().readBool("emu.useDebuggerMemory") || new File(DebuggerMemory.mBrkFilePath).exists()) {
            	useDebuggerMemory = true;
            	// Always use the safe memory when using the debugger memory
            	useSafeMemory = true;
            }

            // Disable address checking when the option
            // "ignoring invalid memory access" is selected.
            if (Settings.getInstance().readBool("emu.ignoreInvalidMemoryAccess") && !useDebuggerMemory) {
                useSafeMemory = false;
            }

        	if (useNativeMemory) {
        		try {
        			System.loadLibrary("memory");
        		} catch (UnsatisfiedLinkError e) {
        			log.error("Cannot load memory library", e);
        			useNativeMemory = false;
        		}
        	}

        	if (useNativeMemory) {
        		if (useSafeMemory) {
        			instance = new SafeNativeMemory();
        		} else {
        			instance = new NativeMemory();
        		}
        	} else if (useDirectBufferMemory) {
        		if (useSafeMemory) {
        			instance = new SafeDirectBufferMemory();
        		} else {
        			instance = new DirectBufferMemory();
        		}
        	} else {
        		if (useSafeMemory) {
	        		instance = new SafeFastMemory();
	        	} else {
	        		instance = new FastMemory();
	        	}
        	}

        	if (instance != null) {
        		if (!instance.allocate()) {
        			instance = null;

        			// Second chance for a native memory...
        			if (useNativeMemory) {
        				if (useSafeMemory) {
            				instance = new SafeSparseNativeMemory();
        				} else {
            				instance = new SparseNativeMemory();
        				}

        				if (!instance.allocate()) {
        					log.warn(String.format("Cannot allocate native memory"));
        					instance = null;
        				}
        			}
        		}
        	}

            if (instance == null) {
                instance = new StandardMemory();
                if (!instance.allocate()) {
                    instance = null;
                }
            }

            if (instance == null) {
                throw new OutOfMemoryError("Cannot allocate memory");
            }

            if (useDebuggerMemory) {
                DebuggerMemory.install();
            }

            if (log.isDebugEnabled()) {
            	log.debug(String.format("Using %s", instance.getClass().getName()));
            }
        }

        return instance;
    }

    private class IgnoreInvalidMemoryAccessSettingsListerner extends AbstractBoolSettingsListener {

        @Override
        protected void settingsValueChanged(boolean value) {
            setIgnoreInvalidMemoryAccess(value);
        }
    }

    protected Memory() {
        Settings.getInstance().registerSettingsListener("Memory", "emu.ignoreInvalidMemoryAccess", new IgnoreInvalidMemoryAccessSettingsListerner());
    }

    public static void setInstance(Memory mem) {
        instance = mem;
    }

    public void invalidMemoryAddress(int address, String prefix, int status) {
        String message = String.format("%s - Invalid memory address: 0x%08X PC=0x%08X", prefix, address, getPc());

        if (ignoreInvalidMemoryAccess) {
            log.warn("IGNORED: " + message);
        } else {
            if (Thread.currentThread().getName().startsWith("Dmac Thread for 0x")) {
            	// Include a stack dump for Dmac threads
            	log.error(message, new Throwable());
            } else {
                log.error(message);
            }
            Emulator.PauseEmuWithStatus(status);
        }
    }

    public void invalidMemoryAddress(int address, int length, String prefix, int status) {
        String message = String.format("%s - Invalid memory address: 0x%08X-0x%08X(length=0x%X) PC=0x%08X", prefix, address, address + length, length, getPc());

        if (ignoreInvalidMemoryAccess) {
            log.warn("IGNORED: " + message);
        } else {
            log.error(message);
            Emulator.PauseEmuWithStatus(status);
        }
    }

    public boolean read32AllowedInvalidAddress(int address) {
        //
        // Ugly hack for programs using pspsdk :-(
        //
        // The function pspSdkInstallNoPlainModuleCheckPatch()
        // is trying to patch 2 psp modules and is expecting to have
        // the module stub implemented as a Jump instruction,
        // something like:
        //          [08XXXXXX]: j YYYYYYYY        // YYYYYYYY = XXXXXX << 2
        //          [00000000]: nop
        //
        // Jpcsp is however based on the following code sequence, e.g.:
        //          [03E00008]: jr $ra
        //          [00081B4C]: syscall 0x0206D
        //
        // The function pspSdkInstallNoPlainModuleCheckPatch()
        // is retrieving the address of the Jump instruction and reading
        // from it in kernel mode.
        // On jpcsp, it is thus trying to read at the following address
        //          0x8f800020 = (0x03E00008 << 2) || 0x80000000
        // up to    0x8f8001ac
        //
        // The hack here is to allow these memory reads and returns 0.
        //
        // Here is the C code from pspsdk:
        //
        //          int pspSdkInstallNoPlainModuleCheckPatch(void)
        //          {
        //              u32 *addr;
        //              int i;
        //
        //              addr = (u32*) (0x80000000 | ((sceKernelProbeExecutableObject & 0x03FFFFFF) << 2));
        //              //printf("sceKernelProbeExecutableObject %p\n", addr);
        //              for(i = 0; i < 100; i++)
        //              {
        //                  if((addr[i] & 0xFFE0FFFF) == LOAD_EXEC_PLAIN_CHECK)
        //                  {
        //                      //printf("Found instruction %p\n", &addr[i]);
        //                      addr[i] = (LOAD_EXEC_PLAIN_PATCH | (addr[i] & ~0xFFE0FFFF));
        //                  }
        //              }
        //
        //              addr = (u32*) (0x80000000 | ((sceKernelCheckPspConfig & 0x03FFFFFF) << 2));
        //              //printf("sceCheckPspConfig %p\n", addr);
        //              for(i = 0; i < 100; i++)
        //              {
        //                  if((addr[i] & 0xFFE0FFFF) == LOAD_EXEC_PLAIN_CHECK)
        //                  {
        //                      //printf("Found instruction %p\n", &addr[i]);
        //                      addr[i] = (LOAD_EXEC_PLAIN_PATCH | (addr[i] & ~0xFFE0FFFF));
        //                  }
        //              }
        //
        //              sceKernelDcacheWritebackAll();
        //
        //              return 0;
        //          }
        //
        if ((address >= 0x8f800020 && address <= 0x8f8001ac)
                || (address >= 0x0f800020 && address <= 0x0f8001ac)) { // Accept also masked address
            log.debug("read32 - ignoring pspSdkInstallNoPlainModuleCheckPatch");
            return true;
        }

        return false;
    }

    public abstract void Initialise();

    public abstract int read8(int address);

    public abstract int read16(int address);

    public abstract int read32(int address);

    public abstract void write8(int address, byte data);

    public abstract void write16(int address, short data);

    public abstract void write32(int address, int data);

    public abstract void memset(int address, byte data, int length);

    public abstract Buffer getMainMemoryByteBuffer();

    public abstract Buffer getBuffer(int address, int length);

    public abstract void copyToMemory(int address, ByteBuffer source, int length);

    protected abstract void memcpy(int destination, int source, int length, boolean checkOverlap);

    public boolean hasMemoryInt(int address) {
    	return false;
    }

    public int[] getMemoryInt(int address) {
    	return null;
    }

    public int getMemoryIntOffset(int address) {
    	return (address & addressMask) >> 2;
    }

    public static boolean isAddressGood(int address) {
        return validMemoryPage[address >>> MEMORY_PAGE_SHIFT];
    }

    public static boolean isAddressAlignedTo(int address, int alignment) {
        return (address % alignment) == 0;
    }

    public static boolean isRawAddressGood(int rawAddress) {
        return validMemoryPage[rawAddress >> MEMORY_PAGE_SHIFT];
    }

    public boolean allocate() {
        for (int i = 0; i < validMemoryPage.length; i++) {
            int address = normalizeAddress(i << MEMORY_PAGE_SHIFT);

            boolean isValid = false;
            if (address >= MemoryMap.START_RAM && address <= MemoryMap.END_RAM) {
                isValid = true;
            } else if (address >= MemoryMap.START_VRAM && address <= MemoryMap.END_VRAM) {
                isValid = true;
            } else if (address >= MemoryMap.START_SCRATCHPAD && address <= MemoryMap.END_SCRATCHPAD) {
                isValid = true;
            }

            validMemoryPage[i] = isValid;
        }

        return true;
    }

    public int internalRead8(int address) {
    	return read8(address);
    }

    public int internalRead16(int address) {
    	return read16(address);
    }

    public int internalRead32(int address) {
    	return read32(address);
    }

    public long read64(int address) {
        long low = read32(address);
        long high = read32(address + 4);
        return (low & 0xFFFFFFFFL) | (high << 32);
    }

    public void write64(int address, long data) {
        write32(address, (int) data);
        write32(address + 4, (int) (data >> 32));
    }

    public void writeUnsigned16(int address, int data) {
    	write16(address, (short) data);
    }

    // memcpy does not check overlapping source and destination areas
    public void memcpy(int destination, int source, int length) {
        memcpy(destination, source, length, false);
    }

    /**
     * Same as memcpy but checking if the source/destination are not used as video textures.
     * 
     * @param destination   destination address
     * @param source        source address
     * @param length        length in bytes to be copied
     */
    public void memcpyWithVideoCheck(int destination, int source, int length) {
    	// As an optimization, do not perform the video check if we are copying only a small memory area.
    	if (length >= MINIMUM_LENGTH_FOR_VIDEO_CHECK) {
	        // If copying to the VRAM or the frame buffer, do not cache the texture
	        if (isVRAM(destination) || Modules.sceDisplayModule.isFbAddress(destination)) {
	        	// If the display is rendering to the destination address, wait for its completion
	        	// before performing the memcpy.
	        	Modules.sceDisplayModule.waitForRenderingCompletion(destination);
	
	        	VideoEngine.getInstance().addVideoTexture(destination, source, length);
	        }
	        // If copying from the VRAM, force the saving of the GE to memory
	        if (isVRAM(source) && Modules.sceDisplayModule.getSaveGEToTexture()) {
	        	VideoEngine.getInstance().addVideoTexture(source, source + length);
	        }
	        if (isVRAM(source)) {
	        	Modules.sceDisplayModule.waitForRenderingCompletion(source);
	        }
    	} else if (isVRAM(destination)) {
    		Modules.sceDisplayModule.waitForRenderingCompletion(destination);
    	}

    	memcpy(destination, source, length);
    }

    /**
     * Same as memset but checking if the destination is not used as video texture.
     * 
     * @param address   destination address
     * @param data      byte to be set in memory
     * @param length    length in bytes to be set
     */
    public void memsetWithVideoCheck(int address, byte data, int length) {
    	// As an optimization, do not perform the video check if we are setting only a small memory area.
    	if (length >= MINIMUM_LENGTH_FOR_VIDEO_CHECK) {
	        // If changing the VRAM or the frame buffer, do not cache the texture
	        if (isVRAM(address) || Modules.sceDisplayModule.isFbAddress(address)) {
	        	// If the display is rendering to the destination address, wait for its completion
	        	// before performing the memcpy.
	        	Modules.sceDisplayModule.waitForRenderingCompletion(address);
	
	        	VideoEngine.getInstance().addVideoTexture(address, address + length);
	        }
    	} else if (isVRAM(address)) {
    		Modules.sceDisplayModule.waitForRenderingCompletion(address);
    	}

    	memset(address, data, length);
    }

    // memmove reproduces the bytes correctly at destination even if the two areas overlap
    public void memmove(int destination, int source, int length) {
        memcpy(destination, source, length, true);
    }

    public int normalize(int address) {
    	return address & addressMask;
    }

    public static int normalizeAddress(int address) {
        address &= addressMask;

        // Test on a PSP: 0x4200000 is equivalent to 0x4000000
        if ((address & 0xFF000000) == MemoryMap.START_VRAM) {
            address &= 0xFF1FFFFF;
        }

        return address;
    }

    protected boolean areOverlapping(int destination, int source, int length) {
        if (source + length <= destination || destination + length <= source) {
            return false;
        }

        return true;
    }

    public boolean isIgnoreInvalidMemoryAccess() {
        return ignoreInvalidMemoryAccess;
    }

    public void setIgnoreInvalidMemoryAccess(boolean ignoreInvalidMemoryAccess) {
        this.ignoreInvalidMemoryAccess = ignoreInvalidMemoryAccess;
    }

    public static boolean isRAM(int address) {
        address &= addressMask;
        return address >= MemoryMap.START_RAM && address <= MemoryMap.END_RAM;
    }

    public static boolean isVRAM(int address) {
        address &= addressMask;
        // Test first against END_VRAM as it is most likely to fail first (because RAM is above VRAM)
        return address <= MemoryMap.END_VRAM && address >= MemoryMap.START_VRAM;
    }

    public void remapMemoryAtProcessorReset() {
    }

    public void reset() {
    }

    protected void read(StateInputStream stream, int address, int length) throws IOException {
    	IMemoryWriter memoryWriter = MemoryWriter.getMemoryWriter(this, address, length, 4);
    	for (int i = 0; i < length; i += 4) {
    		memoryWriter.writeNext(stream.readInt());
    	}
    	memoryWriter.flush();
    }

    protected void write(StateOutputStream stream, int address, int length) throws IOException {
    	IMemoryReader memoryReader = MemoryReader.getMemoryReader(this, address, length, 4);
    	for (int i = 0; i < length; i += 4) {
    		stream.writeInt(memoryReader.readNext());
    	}
    }

    @Override
	public void read(StateInputStream stream) throws IOException {
    	stream.readVersion(STATE_VERSION);
    	read(stream, MemoryMap.START_SCRATCHPAD, MemoryMap.SIZE_SCRATCHPAD);
    	read(stream, MemoryMap.START_VRAM, MemoryMap.SIZE_VRAM);
    	read(stream, MemoryMap.START_RAM, MemoryMap.SIZE_RAM);
	}

	@Override
	public void write(StateOutputStream stream) throws IOException {
		stream.writeVersion(STATE_VERSION);
    	write(stream, MemoryMap.START_SCRATCHPAD, MemoryMap.SIZE_SCRATCHPAD);
    	write(stream, MemoryMap.START_VRAM, MemoryMap.SIZE_VRAM);
    	write(stream, MemoryMap.START_RAM, MemoryMap.SIZE_RAM);
	}
}
