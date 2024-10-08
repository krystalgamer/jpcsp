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
package jpcsp.filesystems.umdiso;

import java.io.IOException;

public interface IBrowser {
	public byte[] readParamSFO() throws IOException;
	public byte[] readIcon0()    throws IOException;
	public byte[] readIcon1()    throws IOException;
	public byte[] readPic0()     throws IOException;
	public byte[] readPic1()     throws IOException;
	public byte[] readSnd0()     throws IOException;
	public byte[] readPspData()  throws IOException;
	public byte[] readPsarData() throws IOException;
}
