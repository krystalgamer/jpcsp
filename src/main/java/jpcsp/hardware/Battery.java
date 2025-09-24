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

import java.io.IOException;
import java.util.Random;

import jpcsp.settings.Settings;
import jpcsp.state.StateInputStream;
import jpcsp.state.StateOutputStream;

public class Battery {
	private final int STATE_VERSION = 0;
    private final String settingsBatterySerialNumber = "batterySerialNumber";
    public static final int BATTERY_SERIAL_NUMBER_SERVICE = 0xFFFFFFFF;
    public final int BATTERY_SERIAL_NUMBER_AUTOBOOT = 0x00000000;

    //battery life time in minutes
    private int lifeTime = (5 * 60);        // 5 hours
    //some standard battery temperature 28 deg C
    private int temperature = 28;
    //battery voltage 4,135 in slim
    private int voltage = 4135;

    private boolean pluggedIn = true;
    private boolean present = true;
    private int currentPowerPercent = 100;
    // led starts flashing at 12%
    private final int lowPercent = 12;
    // PSP auto suspends at 4%
    private final int forceSuspendPercent = 4;
    // battery capacity in mAh when it is full
    private final int fullCapacity = 1800;
    private boolean charging = false;
    public final int EEPROM_SIZE = 256;
    private final byte[] eeprom = new byte[EEPROM_SIZE];

    public static final Battery instance = new Battery();
    private boolean initDone = false;

    private Battery() {}

    public synchronized void initialize() {

        if (initDone)
        {
            return;
        }

    	// Generate a random but valid battery serial number
    	int randomBatterySerialNumber;
    	Random random = new Random();
    	do {
    		randomBatterySerialNumber = random.nextInt(); 
    	} while (randomBatterySerialNumber == BATTERY_SERIAL_NUMBER_SERVICE || randomBatterySerialNumber == BATTERY_SERIAL_NUMBER_AUTOBOOT);

    	// Take the battery serial number from the settings if one is specified,
    	// otherwise, use the random one.
    	int batterySerialNumber = Settings.getInstance().readInt(settingsBatterySerialNumber, randomBatterySerialNumber);

    	// Store the serial number into the EEPROM
    	writeEeprom(15, batterySerialNumber >> 24);
    	writeEeprom(14, batterySerialNumber >> 16);
    	writeEeprom(19, batterySerialNumber >> 8);
    	writeEeprom(18, batterySerialNumber);

        long millis = this.getLifeTime() * 60L / 100;
    	new BatteryUpdateThread(millis * 1000, this).start();

        initDone = true;
    }

    private void batterySerialNumberUpdated() {
    	int batterySerialNumber = readEepromBatterySerialNumber();
    	Settings.getInstance().writeIntHex(settingsBatterySerialNumber, batterySerialNumber);
    }

    public int getLifeTime() {
        return lifeTime;
    }

    public void setLifeTime(int lifeTime) {
        this.lifeTime = lifeTime;
    }

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    public int getVoltage() {
        return voltage;
    }

    public void setVoltage(int voltage) {
        this.voltage = voltage;
    }

    public boolean isPluggedIn() {
        return pluggedIn;
    }

    public void setPluggedIn(boolean pluggedIn) {
        this.pluggedIn = pluggedIn;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public int getCurrentPowerPercent() {
        return currentPowerPercent;
    }

    public void setCurrentPowerPercent(int currentPowerPercent) {
        this.currentPowerPercent = currentPowerPercent;
    }

    public boolean isCharging() {
        return charging;
    }

    public void setCharging(boolean charging) {
        this.charging = charging;
    }

    public int getLowPercent() {
        return lowPercent;
    }

    public int getForceSuspendPercent() {
        return forceSuspendPercent;
    }

    public int getFullCapacity() {
        return fullCapacity;
    }

    public int readEeprom(int address) {
    	return eeprom[address] & 0xFF;
    }

    public void writeEeprom(int address, int value) {
    	eeprom[address] = (byte) (value & 0xFF);

    	if (address == 14 || address == 15 || address == 18 || address == 19) {
    		batterySerialNumberUpdated();
    	}
    }

    public int readEepromBatterySerialNumber() {
    	int batterySerialNumber = 0;
    	// Read the serial number from the EEPROM
    	batterySerialNumber |= readEeprom(15) << 24;
    	batterySerialNumber |= readEeprom(14) << 16;
    	batterySerialNumber |= readEeprom(19) << 8;
    	batterySerialNumber |= readEeprom(18);

    	return batterySerialNumber;
    }

    public void read(StateInputStream stream) throws IOException {
		stream.readVersion(STATE_VERSION);
		lifeTime = stream.readInt();
		temperature = stream.readInt();
		voltage = stream.readInt();
		pluggedIn = stream.readBoolean();
		present = stream.readBoolean();
		currentPowerPercent = stream.readInt();
		charging = stream.readBoolean();
		stream.read(eeprom);
	}

	public void write(StateOutputStream stream) throws IOException {
		stream.writeVersion(STATE_VERSION);
		stream.writeInt(lifeTime);
		stream.writeInt(temperature);
		stream.writeInt(voltage);
		stream.writeBoolean(pluggedIn);
		stream.writeBoolean(present);
		stream.writeInt(currentPowerPercent);
		stream.writeBoolean(charging);
		stream.write(eeprom);
	}
}
