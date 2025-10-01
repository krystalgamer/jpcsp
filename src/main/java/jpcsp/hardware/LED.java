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

public class LED {
	private boolean ledMemoryStickOn;
	private boolean ledWlanOn;
	private boolean ledPowerOn;
	private boolean ledBluetoothOn;

    public static LED Instance = new LED();

    public LED()
    {
    }

	public boolean isLedMemoryStickOn() {
		return ledMemoryStickOn;
	}

	public void setLedMemoryStickOn(boolean ledMemoryStickOn) {
		this.ledMemoryStickOn = ledMemoryStickOn;
	}

	public boolean isLedWlanOn() {
		return ledWlanOn;
	}

	public void setLedWlanOn(boolean ledWlanOn) {
        this.ledWlanOn = ledWlanOn;
	}

	public boolean isLedPowerOn() {
		return ledPowerOn;
	}

	public void setLedPowerOn(boolean ledPowerOn) {
        this.ledPowerOn = ledPowerOn;
	}

	public boolean isLedBluetoothOn() {
		return ledBluetoothOn;
	}

	public void setLedBluetoothOn(boolean ledBluetoothOn) {
        this.ledBluetoothOn = ledBluetoothOn;
	}
}
