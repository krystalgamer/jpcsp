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
package jpcsp.Allegrex.compiler.nativeCode;

/**
 * @author gid15
 *
 */
public class Memmove extends AbstractNativeCodeSequence {
	static public void call() {
		int dstAddr = getGprA0();
		int srcAddr = getGprA1();
		int n = getGprA2();

		getMemory(dstAddr).memmove(dstAddr, srcAddr, n);

		setGprV0(dstAddr);
	}

	static public void alignMemory(int alignment, int addrOffset) {
		int addr = getGprA0();
		int n = getGprA1();

		int dest = (addr + alignment) & ~alignment;
		getMemory(addr).memmove(dest, addr + addrOffset, n - addrOffset);
	}
}
