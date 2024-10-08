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

import org.objectweb.asm.MethodVisitor;

import jpcsp.Allegrex.compiler.CodeInstruction;
import jpcsp.Allegrex.compiler.CompilerContext;

/**
 * @author gid15
 *
 */
public class HookCodeInstruction extends CodeInstruction {
	private NativeCodeSequence nativeCodeSequence;

	public HookCodeInstruction(NativeCodeSequence nativeCodeSequence, CodeInstruction codeInstruction) {
		super(codeInstruction);
		this.nativeCodeSequence = nativeCodeSequence;
	}

	@Override
	public void compile(CompilerContext context, MethodVisitor mv) {
		// Generate the instruction label before the hook call so that
		// the hook is being executed when branching to the instruction.
		if (hasLabel()) {
			mv.visitLabel(getLabel());
			setLabel(null);
		}

		context.visitHook(nativeCodeSequence);
		super.compile(context, mv);
	}
}
