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
package jpcsp.media.codec;

public interface IVideoCodec {
	public int init(int extraData[]);
	public int decode(int input[], int inputOffset, int inputLength);
	public boolean hasImage();
	public int getImageWidth();
	public int getImageHeight();
	public int getImage(int luma[], int cb[], int cr[]);
	public boolean isKeyFrame();
	public void getAspectRatio(int numDen[]);
}
