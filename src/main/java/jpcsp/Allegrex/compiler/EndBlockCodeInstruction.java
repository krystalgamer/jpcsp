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

import org.objectweb.asm.MethodVisitor;

/**
 * @author gid15
 *
 */
public class EndBlockCodeInstruction extends CodeInstruction {
	public EndBlockCodeInstruction(int address) {
		super(address, 0, null, false, false, 0);
	}

	@Override
	public void compile(CompilerContext context, MethodVisitor mv) {
		context.loadImm(getAddress());
		context.visitJump();
	}

    @Override
    public String toString() {
        return String.format("0x%08X - EndBlock", getAddress());
    }
}
