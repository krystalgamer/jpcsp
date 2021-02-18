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

import java.io.IOException;

import jpcsp.state.StateInputStream;
import jpcsp.state.StateOutputStream;
import jpcsp.util.Utilities;

public class MMIOHandlerProfiler extends MMIOHandlerBase {
	private static final int STATE_VERSION = 0;
	private static final int PROFILER_REGISTER_COUNT = 21;
	private final int[] registers = new int[PROFILER_REGISTER_COUNT];

	public MMIOHandlerProfiler(int baseAddress) {
		super(baseAddress);
	}

	@Override
	public void read(StateInputStream stream) throws IOException {
		stream.readVersion(STATE_VERSION);
		stream.readInts(registers);
		super.read(stream);
	}

	@Override
	public void write(StateOutputStream stream) throws IOException {
		stream.writeVersion(STATE_VERSION);
		stream.writeInts(registers);
		super.write(stream);
	}

	@Override
	public int read32(int address) {
		int value;
		switch (address - baseAddress) {
			case 0x00:
			case 0x04:
			case 0x08:
			case 0x0C:
			case 0x10:
			case 0x14:
			case 0x18:
			case 0x1C:
			case 0x20:
			case 0x24:
			case 0x28:
			case 0x2C:
			case 0x30:
			case 0x34:
			case 0x38:
			case 0x3C:
			case 0x40:
			case 0x44:
			case 0x48:
			case 0x4C:
			case 0x50: value = registers[(address - baseAddress) >> 2]; break;
			default: value = super.read32(address); break;
		}

		if (log.isTraceEnabled()) {
			log.trace(String.format("0x%08X - read32(0x%08X) returning 0x%08X", getPc(), address, value));
		}

		return value;
	}

	@Override
	public void write32(int address, int value) {
		switch (address - baseAddress) {
			case 0x00:
			case 0x04:
			case 0x08:
			case 0x0C:
			case 0x10:
			case 0x14:
			case 0x18:
			case 0x1C:
			case 0x20:
			case 0x24:
			case 0x28:
			case 0x2C:
			case 0x30:
			case 0x34:
			case 0x38:
			case 0x3C:
			case 0x40:
			case 0x44:
			case 0x48:
			case 0x4C:
			case 0x50: registers[(address - baseAddress) >> 2] = value; break;
			default: super.write32(address, value); break;
		}

		if (log.isTraceEnabled()) {
			log.trace(String.format("0x%08X - write32(0x%08X, 0x%08X) on %s", getPc(), address, value, this));
		}
	}
}
