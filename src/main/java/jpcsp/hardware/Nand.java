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
package jpcsp.hardware;

public class Nand {
    public static final int pageSize = 0x200; // 512B per page
    public static final int pagesPerBlock = 0x20; // 16KB per block
	private int totalSizeMb;


    public final static Nand Instance = new Nand();

    public void Nand()
    {
        this.init();
    }

	public void init() {
		totalSizeMb = 32;
		if (Model.getModel() != Model.MODEL_PSP_FAT) {
			totalSizeMb = 64;
		}
	}

	public int getTotalSizeMb() {
		return totalSizeMb;
	}

	public int getTotalSize() {
		return totalSizeMb * 0x100000;
	}

	public int getTotalPages() {
		return getTotalSize() / pageSize;
	}

	public int getTotalBlocks() {
		return getTotalSize() / (pagesPerBlock * pageSize);
	}
}
