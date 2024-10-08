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
package jpcsp.HLE.VFS;

import static jpcsp.HLE.VFS.xmb.XmbVirtualFileSystem.PSP_GAME;

import java.util.HashMap;

import jpcsp.HLE.VFS.local.LocalVirtualFileSystem;
import jpcsp.HLE.VFS.xmb.XmbVirtualFileSystem;
import jpcsp.settings.Settings;

public class VirtualFileSystemManager {
	protected HashMap<String, IVirtualFileSystem> virtualFileSystems = new HashMap<String, IVirtualFileSystem>();
	private IVirtualFileSystem xmbVfs;

	public VirtualFileSystemManager() {
		FakeVirtualFileSystem.getInstance().registerAllFakeVirtualFiles(this);
	}

	public void register(String name, IVirtualFileSystem vfs) {
		virtualFileSystems.put(name, vfs);
	}

	public void unregister(String name) {
		virtualFileSystems.remove(name);
	}

	public IVirtualFileSystem getVirtualFileSystem(String absoluteFileName, StringBuilder localFileName) {
		IVirtualFileSystem vfs = virtualFileSystems.get(absoluteFileName);
		if (vfs != null) {
			if (localFileName != null) {
				localFileName.setLength(0);
				localFileName.append(absoluteFileName);
			}
			return vfs;
		}

		int colon = absoluteFileName.indexOf(':');
		if (colon < 0) {
			return null;
		}

		String name = absoluteFileName.substring(0, colon);
		name = name.toLowerCase();

		if (localFileName != null) {
			localFileName.setLength(0);
			localFileName.append(absoluteFileName.substring(colon + 1));

			normalizeLocalFileName(localFileName);

			if ("ms0".equals(name) && localFileName.toString().startsWith(PSP_GAME)) {
				if (xmbVfs == null) {
					xmbVfs = new XmbVirtualFileSystem(new LocalVirtualFileSystem(Settings.getInstance().getDirectoryMapping("ms0"), true));
				}
				return xmbVfs;
			}
		}

		return virtualFileSystems.get(name);
	}

	/**
	 * Normalize the given local file name:
	 * - resolve ".." and "." special notation
	 * - remove leading and trailing "/"
	 * 
	 * @param localFileName   the local file name to be normalized
	 */
	private void normalizeLocalFileName(StringBuilder localFileName) {
		// Remove "/../" in the local file name
		// E.g.:
		//      /PSP_GAME/USRDIR/A/../B
		// is transformed into
		//      /PSP_GAME/USRDIR/B
		while (true) {
			int dotDotIndex = localFileName.indexOf("/../");
			if (dotDotIndex < 0) {
				break;
			}
    		int parentIndex = localFileName.lastIndexOf("/", dotDotIndex - 1);
    		if (parentIndex < 0) {
    			break;
    		}
    		localFileName.delete(parentIndex, dotDotIndex + 3);
		}

		// Remove "/.." at the end of the local file name
		// E.g.:
		//      PSP_GAME/USRDIR/A/..
		// is transformed into
		//      PSP_GAME/USRDIR
		if (localFileName.length() >= 3 && localFileName.lastIndexOf("/..") == localFileName.length() - 3) {
			if (localFileName.length() <= 3) {
				localFileName.setLength(0);
			} else {
				int parentIndex = localFileName.lastIndexOf("/", localFileName.length() - 4);
				if (parentIndex < 0) {
					localFileName.setLength(0);
				} else {
					localFileName.setLength(parentIndex);
				}
			}
		}

		// Remove "/./" in the local file name
		// E.g.:
		//     PSP_GAME/USRDIR/A/./B
		// is transformed into
		//     PSP_GAME/USRDIR/A/B
		while (true) {
			int dotIndex = localFileName.indexOf("/./");
			if (dotIndex < 0) {
				break;
			}
			localFileName.delete(dotIndex, dotIndex + 2);
		}

		// Remove "/." at the end of the local file name
		// E.g.:
		//     PSP_GAME/USRDIR/A/.
		// is transformed into
		//     PSP_GAME/USRDIR/A
		if (localFileName.length() >= 2 && localFileName.lastIndexOf("/.") == localFileName.length() - 2) {
			localFileName.setLength(localFileName.length() - 2);
		}

		// Delete any leading "/"
		if (localFileName.length() > 0 && localFileName.charAt(0) == '/') {
			localFileName.deleteCharAt(0);
		}

		// Delete any trailing "/"
		if (localFileName.length() > 0 && localFileName.charAt(localFileName.length() - 1) == '/') {
			localFileName.setLength(localFileName.length() - 1);
		}
	}

	public static String getFileNameLastPart(String fileName) {
		if (fileName != null) {
			int lastSepIndex = fileName.lastIndexOf('/');
			if (lastSepIndex >= 0) {
				fileName = fileName.substring(lastSepIndex + 1);
			}
		}

		return fileName;
	}
}
