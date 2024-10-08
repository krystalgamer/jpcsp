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

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jpcsp.HLE.Modules;
import jpcsp.HLE.TPointer;
import jpcsp.HLE.kernel.types.SceKernelErrors;
import jpcsp.HLE.modules.IoFileMgrForUser;
import jpcsp.HLE.modules.IoFileMgrForUser.IoOperation;
import jpcsp.HLE.modules.IoFileMgrForUser.IoOperationTiming;
import jpcsp.filesystems.SeekableDataInput;
import jpcsp.util.Utilities;

public abstract class AbstractVirtualFile implements IVirtualFile {
	protected static Logger log = AbstractVirtualFileSystem.log;
	protected final SeekableDataInput file;
	protected static final int IO_ERROR = AbstractVirtualFileSystem.IO_ERROR;
	private final IVirtualFile ioctlFile;

	public AbstractVirtualFile(SeekableDataInput file) {
		this.file = file;
		ioctlFile = null;
	}

	public AbstractVirtualFile(SeekableDataInput file, IVirtualFile ioctlFile) {
		this.file = file;
		this.ioctlFile = ioctlFile;
	}

	@Override
	public long getPosition() {
		try {
			return file.getFilePointer();
		} catch (IOException e) {
			log.error("getPosition", e);
		}
		return Modules.IoFileMgrForUserModule.getPosition(this);
	}

	protected void setPosition(long position) {
		Modules.IoFileMgrForUserModule.setPosition(this, position);
		ioLseek(position);
	}

	@Override
	public int ioClose() {
		try {
			file.close();
		} catch (IOException e) {
			log.error("ioClose", e);
			return IO_ERROR;
		}

		return 0;
	}

	private int getReadLength(int outputLength) {
		int readLength = outputLength;
		long restLength = length() - getPosition();
		if (restLength < readLength) {
			readLength = (int) restLength;
		}

		return readLength;
	}

	@Override
	public int ioRead(TPointer outputPointer, int outputLength) {
		int readLength = getReadLength(outputLength);
		try {
			Utilities.readFully(file, outputPointer, readLength);
		} catch (IOException e) {
			log.error("ioRead", e);
			return SceKernelErrors.ERROR_KERNEL_FILE_READ_ERROR;
		}

		return readLength;
	}

	@Override
	public int ioRead(byte[] outputBuffer, int outputOffset, int outputLength) {
		int readLength = getReadLength(outputLength);
		if (readLength > 0) {
			try {
				file.readFully(outputBuffer, outputOffset, readLength);
			} catch (IOException e) {
				log.error(String.format("ioRead readLength=0x%X, filePosition=0x%X, fileLength=0x%X, %s", readLength, getPosition(), length(), this), e);
				return SceKernelErrors.ERROR_KERNEL_FILE_READ_ERROR;
			}
		} else if (outputLength > 0) {
			// End of file
			return SceKernelErrors.ERROR_KERNEL_FILE_READ_ERROR;
		}

		return readLength;
	}

	@Override
	public int ioWrite(TPointer inputPointer, int inputLength) {
		return IO_ERROR;
	}

	@Override
	public int ioWrite(byte[] inputBuffer, int inputOffset, int inputLength) {
		return IO_ERROR;
	}

	@Override
	public long ioLseek(long offset) {
		try {
			file.seek(offset);
		} catch (IOException e) {
			log.error("ioLseek", e);
			return IO_ERROR;
		}
		return offset;
	}

	@Override
	public int ioIoctl(int command, TPointer inputPointer, int inputLength, TPointer outputPointer, int outputLength) {
		if (ioctlFile != null) {
			return ioctlFile.ioIoctl(command, inputPointer, inputLength, outputPointer, outputLength);
		}

		if (log.isWarnEnabled()) {
	        log.warn(String.format("ioIoctl 0x%08X unsupported command, inlen=%d, outlen=%d", command, inputLength, outputLength));
	        if (inputPointer.isAddressGood()) {
	        	log.warn(String.format("ioIoctl indata: %s", Utilities.getMemoryDump(inputPointer.getAddress(), inputLength)));
	        }
	        if (outputPointer.isAddressGood()) {
	        	log.warn(String.format("ioIoctl outdata: %s", Utilities.getMemoryDump(outputPointer.getAddress(), outputLength)));
	        }
		}

		return IO_ERROR;
	}

	@Override
	public long length() {
		try {
			return file.length();
		} catch (IOException e) {
			if (log.isDebugEnabled()) {
				log.debug("length", e);
			}
		}

		return 0;
	}

	@Override
	public boolean isSectorBlockMode() {
		return false;
	}

	@Override
	public IVirtualFile duplicate() {
		return null;
	}

	@Override
	public Map<IoOperation, IoOperationTiming> getTimings() {
		return IoFileMgrForUser.defaultTimings;
	}

    @Override
	public String toString() {
    	return file == null ? null : file.toString();
	}
}
