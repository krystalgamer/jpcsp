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
package jpcsp.HLE.kernel.types;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import jpcsp.HLE.modules.sceNetInet;

public class pspNetSockAddrInternet extends pspAbstractMemoryMappedStructure {
	public int sin_len;
	public int sin_family;
	public int sin_port;
	public int sin_addr;
	public int sin_zero1;
	public int sin_zero2;

	@Override
	protected void read() {
		// start address is not 32-bit aligned
		sin_len = read8();
		sin_family = read8();
		sin_port = endianSwap16((short) readUnaligned16());
		sin_addr = readUnaligned32();
		sin_zero1 = readUnaligned32();
		sin_zero2 = readUnaligned32();
	}

	@Override
	protected void write() {
		// start address is not 32-bit aligned
		write8((byte) sin_len);
		write8((byte) sin_family);
		writeUnaligned16((short) endianSwap16((short) sin_port));
		writeUnaligned32(sin_addr);
		writeUnaligned32(sin_zero1);
		writeUnaligned32(sin_zero2);
	}

	public void readFromInetAddress(InetAddress inetAddress) {
		sin_len = sizeof();
		sin_family = sceNetInet.AF_INET;
		sin_port = 0;
		sin_addr = sceNetInet.bytesToInternetAddress(inetAddress.getAddress());
	}

	public void readFromInetAddress(InetAddress inetAddress, pspNetSockAddrInternet netSockAddrInternet) {
		sin_len = sizeof();
		sin_family = netSockAddrInternet != null ? netSockAddrInternet.sin_family : sceNetInet.AF_INET;
		sin_port = netSockAddrInternet != null ? netSockAddrInternet.sin_port : 0;
		sin_addr = sceNetInet.bytesToInternetAddress(inetAddress.getAddress());
	}

	public void readFromInetSocketAddress(InetSocketAddress inetSocketAddress) {
		sin_len = sizeof();
		sin_family = sceNetInet.AF_INET;
		sin_port = inetSocketAddress.getPort();
		sin_addr = sceNetInet.bytesToInternetAddress(inetSocketAddress.getAddress().getAddress());
	}

	@Override
	public int sizeof() {
		return 16;
	}

	public boolean equals(InetAddress inetAddress) {
		int addr = sceNetInet.bytesToInternetAddress(inetAddress.getAddress());
		return addr == sin_addr;
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();

		if (getBaseAddress() != 0) {
			s.append(String.format("0x%08X(", getBaseAddress()));
		}
		s.append(String.format("pspNetSockAddrInternet[family=%d, port=%d, addr=0x%08X(%s)]", sin_family, sin_port, sin_addr, sceNetInet.internetAddressToString(sin_addr)));
		if (getBaseAddress() != 0) {
			s.append(")");
		}

		return s.toString();
	}
}
