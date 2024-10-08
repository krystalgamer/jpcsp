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
package jpcsp.HLE.VFS;

import static jpcsp.HLE.kernel.types.SceKernelErrors.ERROR_KERNEL_UNSUPPORTED_OPERATION;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jpcsp.HLE.TPointer;
import jpcsp.HLE.kernel.types.SceIoDirent;
import jpcsp.HLE.kernel.types.SceIoStat;
import jpcsp.HLE.modules.IoFileMgrForUser;
import jpcsp.util.Utilities;

public abstract class AbstractVirtualFileSystem implements IVirtualFileSystem {
	protected static Logger log = LoggerFactory.getLogger("vfs");
	public static final int IO_ERROR = -1;

	protected static boolean hasFlag(int mode, int flag) {
		return (mode & flag) == flag;
	}

	@Override
	public void ioInit() {
	}

	@Override
	public void ioExit() {
	}

	@Override
	public IVirtualFile ioOpen(String fileName, int flags, int mode) {
		return null;
	}

	@Override
	public int ioRemove(String name) {
		return IO_ERROR;
	}

	@Override
	public int ioMkdir(String name, int mode) {
		return IO_ERROR;
	}

	@Override
	public int ioRmdir(String name) {
		return IO_ERROR;
	}

	@Override
	public String[] ioDopen(String dirName) {
		return null;
	}

	@Override
	public int ioDread(String dirName, SceIoDirent dir) {
		// Return the Getstat on the given directory file
		String fileName;
		if (dirName == null || dirName.length() == 0) {
			fileName = dir.filename;
		} else {
			fileName = dirName + "/" + dir.filename;
		}

		int result = ioGetstat(fileName, dir.stat);
		if (result == 0) {
			// Success is 1 for sceIoDread
			return 1;
		}
		return result;
	}

	@Override
	public int ioDclose(String dirName) {
		return 0;
	}

	@Override
	public int ioGetstat(String fileName, SceIoStat stat) {
		return IO_ERROR;
	}

	@Override
	public int ioChstat(String fileName, SceIoStat stat, int bits) {
		return IO_ERROR;
	}

	@Override
	public int ioRename(String oldFileName, String newFileName) {
		return IO_ERROR;
	}

	@Override
	public int ioChdir(String directoryName) {
		return IO_ERROR;
	}

	@Override
	public int ioMount() {
		return IO_ERROR;
	}

	@Override
	public int ioUmount() {
		return IO_ERROR;
	}

	@Override
	public int ioDevctl(String deviceName, int command, TPointer inputPointer, int inputLength, TPointer outputPointer, int outputLength) {
        if (log.isWarnEnabled()) {
	        log.warn(String.format("ioDevctl on '%s', 0x%08X unsupported command, inlen=%d, outlen=%d", deviceName, command, inputLength, outputLength));
	        if (inputPointer.isAddressGood()) {
	        	log.warn(String.format("ioDevctl indata: %s", Utilities.getMemoryDump(inputPointer.getAddress(), inputLength)));
	        }
	        if (outputPointer.isAddressGood()) {
	        	log.warn(String.format("ioDevctl outdata: %s", Utilities.getMemoryDump(outputPointer.getAddress(), outputLength)));
	        }
		}

		return ERROR_KERNEL_UNSUPPORTED_OPERATION;
	}

	@Override
	public Map<IoFileMgrForUser.IoOperation, IoFileMgrForUser.IoOperationTiming> getTimings() {
		return IoFileMgrForUser.defaultTimings;
	}
}
