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
package jpcsp.memory.mmio;

import static jpcsp.memory.FastMemory.memory16Mask;
import static jpcsp.memory.FastMemory.memory16Shift;
import static jpcsp.memory.FastMemory.memory8Mask;
import static jpcsp.memory.FastMemory.memory8Shift;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jpcsp.state.StateInputStream;
import jpcsp.state.StateOutputStream;
import org.slf4j.event.Level;

public class MMIOHandlerReadWrite extends MMIOHandlerBase {
	private static final int STATE_VERSION = 0;
	private final int[] memory;
	private Level logLevel = Level.TRACE;

	public MMIOHandlerReadWrite(int baseAddress, int length) {
		super(baseAddress);

		memory = new int[length >> 2];
	}

	public MMIOHandlerReadWrite(int baseAddress, int length, int[] memory) {
		super(baseAddress);

		this.memory = memory;
	}

	@Override
	public void read(StateInputStream stream) throws IOException {
		stream.readVersion(STATE_VERSION);
		stream.readInts(memory);
		super.read(stream);
	}

	@Override
	public void write(StateOutputStream stream) throws IOException {
		stream.writeVersion(STATE_VERSION);
		stream.writeInts(memory);
		super.write(stream);
	}

	@Override
	public void reset() {
		super.reset();

		Arrays.fill(memory, 0);
	}

	public void setLogLevel(Level logLevel) {
		this.logLevel = logLevel;
	}

	public int[] getInternalMemory() {
		return memory;
	}

	protected String getTraceFormatRead32() {
		return "0x%08X - read32(0x%08X)=0x%08X";
	}

	protected String getTraceFormatRead16() {
		return "0x%08X - read16(0x%08X)=0x%04X";
	}

	protected String getTraceFormatRead8() {
		return "0x%08X - read8(0x%08X)=0x%02X";
	}

	@Override
	public int read32(int address) {
		int data = internalRead32(address);
		if (log.isEnabledForLevel(logLevel)) {
			log.trace(String.format(getTraceFormatRead32(), getPc(), address, data));
		}

		return data;
	}

	@Override
	public int read16(int address) {
		int data = internalRead16(address);
		if (log.isEnabledForLevel(logLevel)) {
			log.trace(String.format(getTraceFormatRead16(), getPc(), address, data));
		}

		return data;
	}

	@Override
	public int read8(int address) {
		int data = internalRead8(address);
		if (log.isEnabledForLevel(logLevel)) {
			log.trace(String.format(getTraceFormatRead8(), getPc(), address, data));
		}
	
		return data;
	}

	@Override
	public int internalRead8(int address) {
		return (memory[(address - baseAddress) >> 2] >> memory8Shift[address & 0x03]) & 0xFF;
	}

	@Override
	public int internalRead16(int address) {
		return (memory[(address - baseAddress) >> 2] >> memory16Shift[address & 0x02]) & 0xFFFF;
	}

	@Override
	public int internalRead32(int address) {
		return memory[(address - baseAddress) >> 2];
	}

	protected String getTraceFormatWrite32() {
		return "0x%08X - write32(0x%08X, 0x%08X)";
	}

	protected String getTraceFormatWrite16() {
		return "0x%08X - write16(0x%08X, 0x%04X)";
	}

	protected String getTraceFormatWrite8() {
		return "0x%08X - write8(0x%08X, 0x%02X)";
	}

	@Override
	public void write32(int address, int value) {
		if (log.isEnabledForLevel(logLevel)) {
			log.trace(String.format(getTraceFormatWrite32(), getPc(), address, value));
		}

		memory[(address - baseAddress) >> 2] = value;
	}

	@Override
	public void write16(int address, short value) {
		if (log.isEnabledForLevel(logLevel)) {
			log.trace(String.format(getTraceFormatWrite16(), getPc(), address, value & 0xFFFF));
		}

		int index = address & 0x02;
		int memData = (memory[(address - baseAddress) >> 2] & memory16Mask[index]) | ((value & 0xFFFF) << memory16Shift[index]);

		memory[(address - baseAddress) >> 2] = memData;
	}

	@Override
	public void write8(int address, byte value) {
		if (log.isEnabledForLevel(logLevel)) {
			log.trace(String.format(getTraceFormatWrite8(), getPc(), address, value & 0xFF));
		}

		int index = address & 0x03;
		int memData = (memory[(address - baseAddress) >> 2] & memory8Mask[index]) | ((value & 0xFF) << memory8Shift[index]);

		memory[(address - baseAddress) >> 2] = memData;
	}
}
