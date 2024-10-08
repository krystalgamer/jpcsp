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
package jpcsp.HLE.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jpcsp.HLE.HLEFunction;
import jpcsp.HLE.HLEModule;
import jpcsp.HLE.HLEUnimplemented;
import jpcsp.HLE.Modules;
import jpcsp.HLE.TPointer;

public class sceUsbHost extends HLEModule {
    public static Logger log = Modules.getLogger("sceUsbHost");

    @HLEUnimplemented
    @HLEFunction(nid = 0x433B2626, version = 661)
    public int sceUsbHostInit() {
    	return 0;
    }

    @HLEUnimplemented
    @HLEFunction(nid = 0xF29704D3, version = 661)
    public int sceUsbHostEnd() {
    	return 0;
    }

    @HLEUnimplemented
    @HLEFunction(nid = 0xF8D5D089, version = 661)
    public int sceUsbHostRegisterClass(TPointer usbHostClass) {
    	return 0;
    }

    @HLEUnimplemented
    @HLEFunction(nid = 0xAA260E35, version = 661)
    public int sceUsbHostUnregisterClass(TPointer usbHostClass) {
    	return 0;
    }
}
