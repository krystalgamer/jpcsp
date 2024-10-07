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

import org.apache.log4j.Logger;

/**
 * @author gid15
 *
 */
public abstract class InvalidatedExecutable implements IExecutable {
	protected static Logger log = Compiler.log;
	private IExecutable executable;

	protected InvalidatedExecutable(CodeBlock codeBlock) {
		executable = codeBlock.getExecutable().getExecutable();
		while (executable != null && executable instanceof InvalidatedExecutable) {
			executable = executable.getExecutable();
		}
	}

	@Override
	public void setExecutable(IExecutable e) {
		// Nothing to do
	}

	@Override
	public IExecutable getExecutable() {
		return executable;
	}
}
