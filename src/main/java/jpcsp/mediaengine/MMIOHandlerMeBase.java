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
package jpcsp.mediaengine;

import java.io.IOException;

import jpcsp.Memory;
import jpcsp.Processor;
import jpcsp.HLE.modules.sceMeCore;
import jpcsp.memory.mmio.MMIOHandlerBase;
import jpcsp.state.StateInputStream;
import jpcsp.state.StateOutputStream;

public class MMIOHandlerMeBase extends MMIOHandlerBase {
	private static final int STATE_VERSION = 0;

	public MMIOHandlerMeBase(int baseAddress) {
		super(baseAddress);

		log = sceMeCore.log;
	}

	@Override
	public void read(StateInputStream stream) throws IOException {
		stream.readVersion(STATE_VERSION);
		super.read(stream);
	}

	@Override
	public void write(StateOutputStream stream) throws IOException {
		stream.writeVersion(STATE_VERSION);
		super.write(stream);
	}

	@Override
	protected Processor getProcessor() {
		return MEProcessor.getInstance();
	}

	@Override
	protected Memory getMemory() {
		return MEProcessor.getInstance().getMEMemory();
	}
}
