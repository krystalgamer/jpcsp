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
package jpcsp.HLE.modules;

import static jpcsp.HLE.Modules.ThreadManForUserModule;
import static jpcsp.hardware.Model.MODEL_PSP_STREET;
import static jpcsp.hardware.Model.getModel;
import static jpcsp.util.Utilities.setBit;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jpcsp.Emulator;
import jpcsp.HLE.BufferInfo;
import jpcsp.HLE.HLEFunction;
import jpcsp.HLE.HLEModule;
import jpcsp.HLE.HLEUnimplemented;
import jpcsp.HLE.Modules;
import jpcsp.HLE.TPointer;
import jpcsp.HLE.TPointer32;
import jpcsp.HLE.TPointerFunction;
import jpcsp.HLE.kernel.types.SceKernelErrors;
import jpcsp.HLE.BufferInfo.LengthInfo;
import jpcsp.HLE.BufferInfo.Usage;
import jpcsp.hardware.Battery;
import jpcsp.hardware.LED;
import jpcsp.hardware.MemoryStick;
import jpcsp.hardware.Model;
import jpcsp.hardware.UMDDrive;
import jpcsp.hardware.Wlan;
import jpcsp.util.Utilities;

public class sceSyscon extends HLEModule {
    public static Logger log = Modules.getLogger("sceSyscon");
    public static final int PSP_SYSCON_CMD_NOP                           = 0x00;
    public static final int PSP_SYSCON_CMD_GET_BARYON                    = 0x01;
    public static final int PSP_SYSCON_CMD_GET_DIGITAL_KEY               = 0x02;
    public static final int PSP_SYSCON_CMD_GET_ANALOG                    = 0x03;
    public static final int PSP_SYSCON_CMD_GET_TACHYON_TEMP              = 0x05;
    public static final int PSP_SYSCON_CMD_GET_DIGITAL_KEY_ANALOG        = 0x06;
    public static final int PSP_SYSCON_CMD_GET_KERNEL_DIGITAL_KEY        = 0x07;
    public static final int PSP_SYSCON_CMD_GET_KERNEL_DIGITAL_KEY_ANALOG = 0x08;
    public static final int PSP_SYSCON_CMD_READ_CLOCK                    = 0x09;
    public static final int PSP_SYSCON_CMD_READ_ALARM                    = 0x0A;
    public static final int PSP_SYSCON_CMD_GET_POWER_SUPPLY_STATUS       = 0x0B;
    public static final int PSP_SYSCON_CMD_GET_TACHYON_WDT_STATUS        = 0x0C;
    public static final int PSP_SYSCON_CMD_GET_BATT_VOLT                 = 0x0D;
    public static final int PSP_SYSCON_CMD_GET_WAKE_UP_FACTOR            = 0x0E;
    public static final int PSP_SYSCON_CMD_GET_WAKE_UP_REQ               = 0x0F;
    public static final int PSP_SYSCON_CMD_GET_STATUS2                   = 0x10;
    public static final int PSP_SYSCON_CMD_GET_TIMESTAMP                 = 0x11;
    public static final int PSP_SYSCON_CMD_GET_VIDEO_CABLE               = 0x12;
    public static final int PSP_SYSCON_CMD_WRITE_CLOCK                   = 0x20;
    public static final int PSP_SYSCON_CMD_SET_USB_STATUS                = 0x21;
    public static final int PSP_SYSCON_CMD_WRITE_ALARM                   = 0x22;
    public static final int PSP_SYSCON_CMD_WRITE_SCRATCHPAD              = 0x23;
    public static final int PSP_SYSCON_CMD_READ_SCRATCHPAD               = 0x24;
    public static final int PSP_SYSCON_CMD_SEND_SETPARAM                 = 0x25;
    public static final int PSP_SYSCON_CMD_RECEIVE_SETPARAM              = 0x26;
    public static final int PSP_SYSCON_CMD_CTRL_BT_POWER_UNK1            = 0x29;
    public static final int PSP_SYSCON_CMD_CTRL_BT_POWER_UNK2            = 0x2A;
    public static final int PSP_SYSCON_CMD_UNKNOWN_30                    = 0x30;
    public static final int PSP_SYSCON_CMD_CTRL_TACHYON_WDT              = 0x31;
    public static final int PSP_SYSCON_CMD_RESET_DEVICE                  = 0x32;
    public static final int PSP_SYSCON_CMD_CTRL_ANALOG_XY_POLLING        = 0x33;
    public static final int PSP_SYSCON_CMD_CTRL_HR_POWER                 = 0x34;
    public static final int PSP_SYSCON_CMD_SHUTDOWN_PSP                  = 0x35;
    public static final int PSP_SYSCON_CMD_SUSPEND_PSP                   = 0x36;
    public static final int PSP_SYSCON_CMD_GET_BATT_VOLT_AD              = 0x37;
    public static final int PSP_SYSCON_CMD_GET_POMMEL_VERSION            = 0x40;
    public static final int PSP_SYSCON_CMD_GET_POLESTAR_VERSION          = 0x41;
    public static final int PSP_SYSCON_CMD_CTRL_VOLTAGE                  = 0x42;
    public static final int PSP_SYSCON_CMD_CTRL_POWER                    = 0x45;
    public static final int PSP_SYSCON_CMD_GET_POWER_STATUS              = 0x46;
    public static final int PSP_SYSCON_CMD_CTRL_LED                      = 0x47;
    public static final int PSP_SYSCON_CMD_WRITE_POMMEL_REG              = 0x48;
    public static final int PSP_SYSCON_CMD_READ_POMMEL_REG               = 0x49;
    public static final int PSP_SYSCON_CMD_CTRL_HDD_POWER                = 0x4A;
    public static final int PSP_SYSCON_CMD_CTRL_LEPTON_POWER             = 0x4B;
    public static final int PSP_SYSCON_CMD_CTRL_MS_POWER                 = 0x4C;
    public static final int PSP_SYSCON_CMD_CTRL_WLAN_POWER               = 0x4D;
    public static final int PSP_SYSCON_CMD_WRITE_POLESTAR_REG            = 0x4E;
    public static final int PSP_SYSCON_CMD_READ_POLESTAR_REG             = 0x4F;
    public static final int PSP_SYSCON_CMD_CTRL_DVE_POWER                = 0x52;
    public static final int PSP_SYSCON_CMD_CTRL_BT_POWER                 = 0x53;
    public static final int PSP_SYSCON_CMD_CTRL_USB_POWER                = 0x55;
    public static final int PSP_SYSCON_CMD_CTRL_CHARGE                   = 0x56;
    public static final int PSP_SYSCON_CMD_BATTERY_BASE                  = 0x60;
    public static final int PSP_SYSCON_CMD_BATTERY_NOP                   = 0x60;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_STATUS_CAP        = 0x61;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_TEMP              = 0x62;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_VOLT              = 0x63;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_ELEC              = 0x64;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_RCAP              = 0x65;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_CAP               = 0x66;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_FULL_CAP          = 0x67;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_IFC               = 0x68;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_LIMIT_TIME        = 0x69;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_STATUS            = 0x6A;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_CYCLE             = 0x6B;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_SERIAL            = 0x6C;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_INFO              = 0x6D;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_TEMP_AD           = 0x6E;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_VOLT_AD           = 0x6F;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_ELEC_AD           = 0x70;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_TOTAL_ELEC        = 0x71;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_CHARGE_TIME       = 0x72;
    public static final int PSP_SYSCON_CMD_BATTERY_WRITE_EEPROM          = 0x73;
    public static final int PSP_SYSCON_CMD_BATTERY_READ_EEPROM           = 0x74;
    public static final int PSP_SYSCON_CMD_BATTERY_GET_MANUFACTURER      = 0x76;
    public static final int PSP_SYSCON_CMD_BATTERY_CHALLENGE1            = 0xE0;
    public static final int PSP_SYSCON_CMD_BATTERY_CHALLENGE2            = 0xE1;
    private static String cmdNames[];
    public static final int PSP_SYSCON_LED_MS    = 0; // Memory-Stick LED
    public static final int PSP_SYSCON_LED_WLAN  = 1; // W-LAN LED
    public static final int PSP_SYSCON_LED_POWER = 2; // Power LED
    public static final int PSP_SYSCON_LED_BT    = 3; // Bluetooth LED (only PSP GO)
    public static final int PSP_SYSCON_DEVICE_PSP = 0x01;
    public static final int PSP_SYSCON_DEVICE_UMD = 0x02;
    public static final int PSP_SYSCON_DEVICE_WLAN = 0x04;
    public static final int PSP_SYSCON_DEVICE_BT = 0x20;
    public static final int PSP_SYSCON_DEVICE_RESET_MODE_2 = 0x40;
    public static final int PSP_SYSCON_DEVICE_RESET_MODE_1 = 0x80;
    private final int scratchPad[] = new int[32];
    private int alarm;
    private int tachyonTemp = 13094; // Unsigned value, expected to be larger or equal to 13094
    private int polestarStatusCount;
    private final Callback[] callbacks = new Callback[SceSysconCallbacks.SYSCON_CB_COUNT.ordinal()];

    private static enum SceSysconCallbacks {
        SYSCON_CB_LOW_BATTERY,
        SYSCON_CB_POWER_SWITCH,
        SYSCON_CB_ALARM,
        SYSCON_CB_AC_SUPPLY,
        SYSCON_CB_HP_CONNECT,
        SYSCON_CB_WLAN_SWITCH,
        SYSCON_CB_HOLD_SWITCH,
        SYSCON_CB_UMD_SWITCH,
        SYSCON_CB_HR_POWER,
        SYSCON_CB_WLAN_POWER,
        SYSCON_CB_GSENSOR,
        UNUSED,
        SYSCON_CB_BT_POWER,
        SYSCON_CB_BT_SWITCH,
        SYSCON_CB_HR_WAKEUP,
        SYSCON_CB_AC_SUPPLY2,
        SYSCON_CB_HR_UNK16,
        SYSCON_CB_HR_UNK17,
        SYSCON_CB_UNK18,
        SYSCON_CB_USB_UNK19,
        SYSCON_CB_COUNT
    }

    protected static class Callback {
    	private TPointerFunction callback;
    	private int param;
    	private int gp;

    	public Callback(TPointerFunction callback, int param) {
			this.callback = callback;
			this.param = param;
			gp = Emulator.getProcessor().cpu._gp;
		}

    	public void call(boolean enabled) {
    		if (callback == null || callback.isNull()) {
    			return;
    		}

    		ThreadManForUserModule.executeCallback(callback.getAddress(), gp, null, enabled ? 1 : 0, param);
    	}
    }

    @Override
	public void start() {
		Arrays.fill(scratchPad, 0);

		// Unknown 4-bytes value at offset 8
		int scratchPad8 = 0;
		for (int i = 0; i < 4; i++, scratchPad8 >>= 8) {
			scratchPad[i + 8] = scratchPad8 & 0xFF;
		}

		// Unknown 4-bytes value at offset 8
		int scratchPad12 = 0;
		for (int i = 0; i < 4; i++, scratchPad12 >>= 8) {
			scratchPad[i + 12] = scratchPad12 & 0xFF;
		}

		// 5-bytes value at offset 16, used to initialize the clock.
		// Set this value to 0 to force the clock initialization at boot time.
		long scratchPad16 = sceRtc.hleGetCurrentTick() >> 19;
		if (log.isDebugEnabled()) {
			log.debug(String.format("Initializing scratchPad16=0x%X", scratchPad16));
		}
		for (int i = 0; i < 5; i++, scratchPad16 >>= 8) {
			scratchPad[i + 16] = (int) scratchPad16 & 0xFF;
		}

		// Unknown 5-bytes value at offset 24
		long scratchPad24 = 0L;
		for (int i = 0; i < 5; i++, scratchPad24 >>= 8) {
			scratchPad[i + 24] = (int) scratchPad24 & 0xFF;
		}

		alarm = 0;

		polestarStatusCount = 0;

		super.start();
	}

	public static String getSysconCmdName(int cmd) {
    	if (cmdNames == null) {
    		cmdNames = new String[256];
    		cmdNames[PSP_SYSCON_CMD_NOP] = "NOP";
    		cmdNames[PSP_SYSCON_CMD_GET_BARYON] = "GET_BARYON";
    		cmdNames[PSP_SYSCON_CMD_GET_DIGITAL_KEY] = "GET_DIGITAL_KEY";
    		cmdNames[PSP_SYSCON_CMD_GET_ANALOG] = "GET_ANALOG";
    		cmdNames[PSP_SYSCON_CMD_GET_TACHYON_TEMP] = "GET_TACHYON_TEMP";
    		cmdNames[PSP_SYSCON_CMD_GET_DIGITAL_KEY_ANALOG] = "GET_DIGITAL_KEY_ANALOG";
    		cmdNames[PSP_SYSCON_CMD_GET_KERNEL_DIGITAL_KEY] = "GET_KERNEL_DIGITAL_KEY";
    		cmdNames[PSP_SYSCON_CMD_GET_KERNEL_DIGITAL_KEY_ANALOG] = "GET_KERNEL_DIGITAL_KEY_ANALOG";
    		cmdNames[PSP_SYSCON_CMD_READ_CLOCK] = "READ_CLOCK";
    		cmdNames[PSP_SYSCON_CMD_READ_ALARM] = "READ_ALARM";
    		cmdNames[PSP_SYSCON_CMD_GET_POWER_SUPPLY_STATUS] = "GET_POWER_SUPPLY_STATUS";
    		cmdNames[PSP_SYSCON_CMD_GET_TACHYON_WDT_STATUS] = "GET_TACHYON_WDT_STATUS";
    		cmdNames[PSP_SYSCON_CMD_GET_BATT_VOLT] = "GET_BATT_VOLT";
    		cmdNames[PSP_SYSCON_CMD_GET_WAKE_UP_FACTOR] = "GET_WAKE_UP_FACTOR";
    		cmdNames[PSP_SYSCON_CMD_GET_WAKE_UP_REQ] = "GET_WAKE_UP_REQ";
    		cmdNames[PSP_SYSCON_CMD_GET_STATUS2] = "GET_STATUS2";
    		cmdNames[PSP_SYSCON_CMD_GET_TIMESTAMP] = "GET_TIMESTAMP";
    		cmdNames[PSP_SYSCON_CMD_GET_VIDEO_CABLE] = "GET_VIDEO_CABLE";
    		cmdNames[PSP_SYSCON_CMD_WRITE_CLOCK] = "WRITE_CLOCK";
    		cmdNames[PSP_SYSCON_CMD_SET_USB_STATUS] = "SET_USB_STATUS";
    		cmdNames[PSP_SYSCON_CMD_WRITE_ALARM] = "WRITE_ALARM";
    		cmdNames[PSP_SYSCON_CMD_WRITE_SCRATCHPAD] = "WRITE_SCRATCHPAD";
    		cmdNames[PSP_SYSCON_CMD_READ_SCRATCHPAD] = "READ_SCRATCHPAD";
    		cmdNames[PSP_SYSCON_CMD_SEND_SETPARAM] = "SEND_SETPARAM";
    		cmdNames[PSP_SYSCON_CMD_RECEIVE_SETPARAM] = "RECEIVE_SETPARAM";
    		cmdNames[PSP_SYSCON_CMD_CTRL_BT_POWER_UNK1] = "CTRL_BT_POWER_UNK1";
    		cmdNames[PSP_SYSCON_CMD_CTRL_BT_POWER_UNK2] = "CTRL_BT_POWER_UNK2";
    		cmdNames[PSP_SYSCON_CMD_UNKNOWN_30] = "UNKNOWN_30";
    		cmdNames[PSP_SYSCON_CMD_CTRL_TACHYON_WDT] = "CTRL_TACHYON_WDT";
    		cmdNames[PSP_SYSCON_CMD_RESET_DEVICE] = "RESET_DEVICE";
    		cmdNames[PSP_SYSCON_CMD_CTRL_ANALOG_XY_POLLING] = "CTRL_ANALOG_XY_POLLING";
    		cmdNames[PSP_SYSCON_CMD_CTRL_HR_POWER] = "CTRL_HR_POWER";
    		cmdNames[PSP_SYSCON_CMD_SHUTDOWN_PSP] = "SHUTDOWN_PSP";
    		cmdNames[PSP_SYSCON_CMD_SUSPEND_PSP] = "SUSPEND_PSP";
    		cmdNames[PSP_SYSCON_CMD_GET_BATT_VOLT_AD] = "GET_BATT_VOLT_AD";
    		cmdNames[PSP_SYSCON_CMD_GET_POMMEL_VERSION] = "GET_POMMEL_VERSION";
    		cmdNames[PSP_SYSCON_CMD_GET_POLESTAR_VERSION] = "GET_POLESTAR_VERSION";
    		cmdNames[PSP_SYSCON_CMD_CTRL_VOLTAGE] = "CTRL_VOLTAGE";
    		cmdNames[PSP_SYSCON_CMD_CTRL_POWER] = "CTRL_POWER";
    		cmdNames[PSP_SYSCON_CMD_GET_POWER_STATUS] = "GET_POWER_STATUS";
    		cmdNames[PSP_SYSCON_CMD_CTRL_LED] = "CTRL_LED";
    		cmdNames[PSP_SYSCON_CMD_WRITE_POMMEL_REG] = "WRITE_POMMEL_REG";
    		cmdNames[PSP_SYSCON_CMD_READ_POMMEL_REG] = "READ_POMMEL_REG";
    		cmdNames[PSP_SYSCON_CMD_CTRL_HDD_POWER] = "CTRL_HDD_POWER";
    		cmdNames[PSP_SYSCON_CMD_CTRL_LEPTON_POWER] = "CTRL_LEPTON_POWER";
    		cmdNames[PSP_SYSCON_CMD_CTRL_MS_POWER] = "CTRL_MS_POWER";
    		cmdNames[PSP_SYSCON_CMD_CTRL_WLAN_POWER] = "CTRL_WLAN_POWER";
    		cmdNames[PSP_SYSCON_CMD_WRITE_POLESTAR_REG] = "WRITE_POLESTAR_REG";
    		cmdNames[PSP_SYSCON_CMD_READ_POLESTAR_REG] = "READ_POLESTAR_REG";
    		cmdNames[PSP_SYSCON_CMD_CTRL_DVE_POWER] = "CTRL_DVE_POWER";
    		cmdNames[PSP_SYSCON_CMD_CTRL_BT_POWER] = "CTRL_BT_POWER";
    		cmdNames[PSP_SYSCON_CMD_CTRL_USB_POWER] = "CTRL_USB_POWER";
    		cmdNames[PSP_SYSCON_CMD_CTRL_CHARGE] = "CTRL_CHARGE";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_NOP] = "BATTERY_NOP";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_STATUS_CAP] = "BATTERY_GET_STATUS_CAP";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_TEMP] = "BATTERY_GET_TEMP";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_VOLT] = "BATTERY_GET_VOLT";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_ELEC] = "BATTERY_GET_ELEC";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_RCAP] = "BATTERY_GET_RCAP";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_CAP] = "BATTERY_GET_CAP";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_FULL_CAP] = "BATTERY_GET_FULL_CAP";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_IFC] = "BATTERY_GET_IFC";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_LIMIT_TIME] = "BATTERY_GET_LIMIT_TIME";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_STATUS] = "BATTERY_GET_STATUS";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_CYCLE] = "BATTERY_GET_CYCLE";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_SERIAL] = "BATTERY_GET_SERIAL";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_INFO] = "BATTERY_GET_INFO";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_TEMP_AD] = "BATTERY_GET_TEMP_AD";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_VOLT_AD] = "BATTERY_GET_VOLT_AD";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_ELEC_AD] = "BATTERY_GET_ELEC_AD";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_TOTAL_ELEC] = "BATTERY_GET_TOTAL_ELEC";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_CHARGE_TIME] = "BATTERY_GET_CHARGE_TIME";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_WRITE_EEPROM] = "BATTERY_WRITE_EEPROM";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_READ_EEPROM] = "BATTERY_READ_EEPROM";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_GET_MANUFACTURER] = "BATTERY_GET_MANUFACTURER";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_CHALLENGE1] = "BATTERY_CHALLENGE1";
    		cmdNames[PSP_SYSCON_CMD_BATTERY_CHALLENGE2] = "BATTERY_CHALLENGE2";

    		for (int i = 0; i < cmdNames.length; i++) {
    			if (cmdNames[i] == null) {
    				cmdNames[i] = String.format("UNKNOWN_CMD_0x%02X", i);
    			}
    		}
    	}

    	return cmdNames[cmd];
    }

    public int getPowerSupplyStatus() {
    	int powerSupplyStatus = 0xC0; // Unknown value

    	if (Battery.isPresent()) {
    		powerSupplyStatus |= 0x02; // Flag indicating that a battery is present
    	}

    	return powerSupplyStatus;
    }

    private int getBatteryStatusCap() {
    	return (Battery.getCurrentPowerPercent() + 1) * 0x10000 / 16 + 0x800;
    }

    public int getBatteryStatusCap1() {
    	return getBatteryStatusCap() & 0xFFFF;
    }

    public int getBatteryStatusCap2() {
    	return (getBatteryStatusCap() >> 16) & 0xFFFF;
    }

    public int getBatteryCycle() {
    	return 0x000F;
    }

    public int getBatteryLimitTime() {
    	return 1025;
    }

    public int getBatteryElec() {
    	return 4200;
    }

    public int getPowerStatus() {
    	return 0;
    }

    public int[] getTimeStamp() {
    	return new int[12];
    }

    public void readScratchpad(int src, int[] values, int size) {
    	System.arraycopy(scratchPad, src, values, 0, size);
    }

    public void writeScratchpad(int dst, int[] src, int size) {
    }

    public int readClock() {
    	return 0;
    }

    public void writeClock(int clock) {
    }

    public int readAlarm() {
    	return alarm;
    }

    public void writeAlarm(int alarm) {
    	this.alarm = alarm;
    }

    public int getTachyonTemp() {
    	return tachyonTemp;
    }

    public int readPommelRegister(int reg) {
    	int value = 0;
    	switch (reg) {
    		case 0x80:
    			value = Model.getPommelVersion();
    			break;
    		default:
    			log.error(String.format("readPommelRegister unimplemented reg=0x%02X", reg));
    			break;
    	}

    	return value;
    }

    public void writePommelRegister(int reg, int value) {
    	log.error(String.format("writePommelRegister unimplemented reg=0x%02X, value=0x%04X", reg, value));
    }

    public int readPolestarRegister(int reg) {
    	int value = 0;
    	switch (reg) {
    		case 0x00:
    			value = 0x800C; // Polestar version (taken from PSP fat)
    			break;
    		case 0x01: // Returning information used by PSP_SYSCON_CMD_GET_POWER_SUPPLY_STATUS
    			// Values taken from PSP fat (using PSP_SYSCON_CMD_READ_POLESTAR_REG):
    			// - Battery present and DC plugged    : 0x5100
    			// - Battery present and DC unplugged  : 0x0000
    			// - Battery not present and DC plugged: 0x0300 (strange, should have flag 0x0002 set as the battery is not present)

    			// TODO Further investigate the possible values (also test on a PSP slim)
    			value = 0x0000;
    			if (polestarStatusCount <= 1) {
    				// The flag 0x0001 needs to be returned at least during the first 2 read of this register
    				value = setBit(value, 0);
    				polestarStatusCount++;
    			}

    			// The PSP Street has an internal battery, it seems to return that no battery is present in this polestar register
    			if (!Battery.isPresent() || getModel() == MODEL_PSP_STREET) {
    				// Inverted logic, the flag 0x0002 means that the battery is NOT present
    				value = setBit(value, 1);
    			}
    			break;
    		default:
    			log.error(String.format("readPolestarRegister unimplemented reg=0x%02X", reg));
    			break;
    	}

    	return value;
    }

    public void writePolestarRegister(int reg, int value) {
    	log.error(String.format("writePolestarRegister unimplemented reg=0x%02X, value=0x%04X", reg, value));
    }

    private int hleSysconSetCallback(TPointerFunction callback, int param, SceSysconCallbacks id) {
    	callbacks[id.ordinal()] = new Callback(callback, param);

    	return 0;
    }

    /**
     * Set the wlan switch callback, that will be ran when the wlan switch changes.
     *
     * @param callback The callback function.
     * @param argp The second argument that will be passed to the callback.
     *
     * @return 0.
     */
	@HLEFunction(nid = 0x50446BE5, version = 150)
	public int sceSysconSetWlanSwitchCallback(TPointerFunction callback, int argp) {
    	return hleSysconSetCallback(callback, argp, SceSysconCallbacks.SYSCON_CB_WLAN_SWITCH);
	}

    /**
     * Check if the battery is low.
     *
     * @return 1 if it is low, 0 otherwise.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x1605847F, version = 150)
	public boolean sceSysconIsLowBattery() {
    	return Battery.getCurrentPowerPercent() <= Battery.getLowPercent();
	}

    /**
     * Get the wlan switch state.
     *
     * @return 1 if wlan is activated, 0 otherwise.
     */
	@HLEFunction(nid = 0x2D510164, version = 150)
	public int sceSysconGetWlanSwitch() {
    	return Wlan.getSwitchState();
	}

	@HLEFunction(nid = 0x0B51E34D, version = 150)
	public int sceSysconSetWlanSwitch(int switchState) {
    	int oldSwitchState = Wlan.getSwitchState();
    	Wlan.setSwitchState(switchState);

    	return oldSwitchState;
    }

    /**
     * Set the wlan power.
     *
     * @param power The new power value.
     *
     * @return 0 on success.
     */
	@HLEFunction(nid = 0x48448373, version = 150)
	public int sceSysconCtrlWlanPower(boolean power) {
		if (!power) {
			log.warn(String.format("sceSysconCtrlWlanPower power=%b", power));
		}

		return 0;
	}

    /**
     * Get the wlan power status.
     *
     * @return 1 if the power is on, 0 otherwise.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x7216917F, version = 150)
	public int sceSysconGetWlanPowerStatus() {
    	return 1;
	}

    /**
     * Get the wake up req (?).
     *
     * @param req Pointer to a buffer where the req will be stored.
     *
     * @return 0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0xA9AEF39F, version = 150)
	@HLEFunction(nid = 0x9D88A8DE, version = 660)
	public int sceSysconGetWakeUpReq(@BufferInfo(usage=Usage.out) TPointer32 req) {
    	return 0;
	}

    /**
     * Get the baryon version.
     *
     * @return The baryon version.
     */
	@HLEFunction(nid = 0xFD5C58CB, version = 150)
	public int _sceSysconGetBaryonVersion() {
    	return Model.getBaryonVersion();
	}

    /**
     * Get the baryon version from the syscon.
     *
     * @param  baryonVersionAddr Pointer to a s32 where the baryon version will be stored.
     * @return 0 on success.
     */
	@HLEFunction(nid = 0x7EC5A957, version = 150)
	public int sceSysconGetBaryonVersion(@BufferInfo(usage=Usage.out) TPointer32 baryonVersionAddr) {
    	baryonVersionAddr.setValue(Model.getBaryonVersion());

    	return 0;
	}

    /**
     * Reset the device.
     *
     * @param device The device identifier, passed to the syscon.
     * @param mode The resetting mode ([0, 1 or 2]).
     * 
     * @return 0 on success.
     */
	@HLEFunction(nid = 0x8CBC7987, version = 150)
	public int sceSysconResetDevice(int device, int mode) {
		switch (device) {
			case PSP_SYSCON_DEVICE_PSP:
				if (mode != 1 && mode != 2) {
					return SceKernelErrors.ERROR_INVALID_MODE;
				}
				log.warn(String.format("sceSysconResetDevice device=PSP, mode=%d", mode));
				break;
			case PSP_SYSCON_DEVICE_UMD:
				log.warn(String.format("sceSysconResetDevice device=UMD, mode=%d", mode));
				break;
			case PSP_SYSCON_DEVICE_WLAN:
				if (log.isDebugEnabled()) {
					log.debug(String.format("sceSysconResetDevice device=WLAN, mode=%d", mode));
				}
				break;
			case PSP_SYSCON_DEVICE_BT:
				log.warn(String.format("sceSysconResetDevice device=BlueTooth, mode=%d", mode));
				break;
			default:
				log.warn(String.format("sceSysconResetDevice unknown device=%d, mode=%d", device, mode));
				break;
		}
    	return 0;
	}

    /**
     * Get the Memory Stick power control state.
     *
     * @return 1 if powered, 0 otherwise
     */
	@HLEFunction(nid = 0x7672103B, version = 150)
	public boolean sceSysconGetMsPowerCtrl() {
		return MemoryStick.hasMsPower();
	}

    /**
     * Set the memory stick power.
     *
     * @param power The new power value.
     *
     * @return 0 on success.
     */
	@HLEUnimplemented
	@HLEFunction(nid = 0x1088ABA8, version = 150)
	public int sceSysconCtrlMsPower(boolean power) {
		MemoryStick.setMsPower(power);

		return 0;
	}

    /**
     * Get the UMD drive power control state.
     *
     * @return 1 if powered, 0 otherwise
     */
	@HLEUnimplemented
	@HLEFunction(nid = 0x577C5771, version = 660)
	public boolean sceSysconGetLeptonPowerCtrl() {
		return UMDDrive.hasUmdPower();
	}

    /**
     * Set the lepton power.
     *
     * @param power The new power value.
     *
     * @return 0 on success.
     */
	@HLEUnimplemented
	@HLEFunction(nid = 0x8A4519F5, version = 660)
	public int sceSysconCtrlLeptonPower(boolean power) {
		UMDDrive.setUmdPower(power);

		return 0;
	}

	/**
	 * Execute synchronously a syscon packet.
	 * 
	 * @param packet   The packet to execute. Its tx member needs to be initialized.
	 * @param flags    The packet flags. Check SceSysconPacketFlags.
	 * @return         0 on success.
	 */
	@HLEUnimplemented
	@HLEFunction(nid = 0x5B9ACC97, version = 150)
	public int sceSysconCmdExec(@BufferInfo(lengthInfo=LengthInfo.fixedLength, length=96, usage=Usage.inout) TPointer packet, int flags) {
		int cmd = packet.getValue8(12);
		int len = packet.getValue8(13);
		if (log.isDebugEnabled()) {
			log.debug(String.format("sceSysconCmdExec cmd=0x%02X, len=0x%02X, txData: %s", cmd, len, Utilities.getMemoryDump(packet.getAddress() + 14, len - 2)));
		}
		return 0;
	}

	/**
	 * Execute asynchronously a syscon packet.
	 * 
	 * @param packet   The packet to execute. Its tx member needs to be initialized.
	 * @param flags    The packet flags. Check SceSysconPacketFlags.
	 * @param callback The packet callback. Check the callback member of SceSysconPacket.
	 * @param argp     The second argument that will be passed to the callback when executed.
	 * @return         0 on success.
	 */
	@HLEUnimplemented
	@HLEFunction(nid = 0x3AC3D2A4, version = 150)
	public int sceSysconCmdExecAsync(@BufferInfo(lengthInfo=LengthInfo.fixedLength, length=96, usage=Usage.inout) TPointer packet, int flags, TPointer callback, int argp) {
		int cmd = packet.getValue8(12);
		int len = packet.getValue8(13);
		if (log.isDebugEnabled()) {
			log.debug(String.format("sceSysconCmdExecAsync cmd=0x%02X, len=0x%02X, txData: %s", cmd, len, Utilities.getMemoryDump(packet.getAddress() + 14, len - 2)));
		}
		return 0;
	}

    /**
     * Get the baryon timestamp string.
     *
     * @param  timeStampAddr A pointer to a string at least 12 bytes long.
     * @return 0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x7BCC5EAE, version = 150)
	public int sceSysconGetTimeStamp(@BufferInfo(lengthInfo=LengthInfo.fixedLength, length=12, usage=Usage.out) TPointer timeStampAddr) {
    	int[] timeStamp = getTimeStamp();
    	for (int i = 0; i < timeStamp.length; i++) {
    		timeStampAddr.setValue8(i, (byte) timeStamp[i]);
    	}

    	return 0;
	}

    /**
     * Get the pommel version.
     *
     * @param  pommelAddr Pointer to a s32 where the pommel version will be stored.
     * @return 0 on success.
     */
	@HLEFunction(nid = 0xE7E87741, version = 150)
	public int sceSysconGetPommelVersion(@BufferInfo(usage=Usage.out) TPointer32 pommelAddr) {
    	pommelAddr.setValue(Model.getPommelVersion());

    	return 0;
	}

    /**
     * Get the power status.
     *
     * @param  statusAddr Pointer to a s32 where the power status will be stored.
     * @return 0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x28363C97, version = 150)
	public int sceSysconGetPowerStatus(@BufferInfo(usage=Usage.out) TPointer32 statusAddr) {
    	statusAddr.setValue(getPowerStatus());

    	return 0;
	}

    /**
     * Read data from the scratchpad.
     *
     * @param src  The scratchpad address to read from. 
     * @param dst  A pointer where will be copied the read data.
     * @param size The size of the data to read from the scratchpad.
     * @return 0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0xEB277C88, version = 150)
	public int sceSysconReadScratchPad(int src, @BufferInfo(lengthInfo=LengthInfo.nextParameter, usage=Usage.out) TPointer dst, int size) {
    	int values[] = new int[size];
    	readScratchpad(src, values, size);
    	for (int i = 0; i < size; i++) {
    		dst.setValue8(i, (byte) values[i]);
    	}

    	return 0;
	}

    /**
     * Write data to the scratchpad.
     *
     * @param dst  The scratchpad address to write to.
     * @param src  A pointer to the data to copy to the scratchpad.
     * @param size The size of the data to copy.
     * @return     0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x65EB6096, version = 150)
	public int sceSysconWriteScratchPad(int dst, @BufferInfo(lengthInfo=LengthInfo.nextParameter, usage=Usage.out) TPointer src, int size) {
    	int[] values = new int[size];
    	for (int i = 0; i < size; i++) {
    		values[i] = src.getValue8(i);
    	}
    	writeScratchpad(dst, values, size);

    	return 0;
	}

    /**
     * Control an LED.
     *
     * @param led   The led to toggle (PSP_SYSCON_LED_xxx)
     * @param state Whether to turn on or off 
     * @return      < 0 on error
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x18BFBE65, version = 150)
	public int sceSysconCtrlLED(int led, boolean state) {
    	switch (led) {
    		case PSP_SYSCON_LED_MS: LED.setLedMemoryStickOn(state); break;
    		case PSP_SYSCON_LED_WLAN: LED.setLedWlanOn(state); break;
    		case PSP_SYSCON_LED_POWER: LED.setLedPowerOn(state); break;
    		case PSP_SYSCON_LED_BT: LED.setLedBluetoothOn(state); break;
    		default: return SceKernelErrors.ERROR_INVALID_INDEX;
    	}

    	return 0;
	}

    /**
     * Receive a parameter (used by power).
     *
     * @param id    The parameter ID.
     * @param param Pointer to a buffer (length 8) where will be copied the parameter.
     * @return      0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x08234E6D, version = 150)
	public int sceSysconReceiveSetParam(int id, @BufferInfo(lengthInfo=LengthInfo.fixedLength, length=8, usage=Usage.out) TPointer param) {
    	return 0;
    }

    /**
     * Set a parameter (used by power).
     *
     * @param id    The parameter ID.
     * @param param Pointer to a buffer (length 8) the parameter will be set to.
     * @return      0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x992C22C2, version = 150)
	public int sceSysconSendSetParam(int id, @BufferInfo(lengthInfo=LengthInfo.fixedLength, length=8, usage=Usage.in) TPointer param) {
    	return 0;
    }

    /**
     * Control the remote control power.
     *
     * @param power  1 is on, 0 is off
     * @return       < 0 on error 
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x44439604, version = 150)
	@HLEFunction(nid = 0xBB7260C8, version = 660)
	public int sceSysconCtrlHRPower(boolean power) {
    	return 0;
    }

    /**
     * Get the power supply status.
     *
     * @param statusAddr Pointer to a s32 where the power supply status will be stored.
     * @return           0 on success. 
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0xFC32141A, version = 150)
	@HLEFunction(nid = 0x22240B41, version = 660)
	public int sceSysconGetPowerSupplyStatus(@BufferInfo(usage=Usage.out) TPointer32 statusAddr) {
    	statusAddr.setValue(getPowerSupplyStatus());
    	return 0;
    }

    /**
     * Get the battery status cap.
     *
     * @param unknown1 Pointer to an unknown s32 where a value will be stored. 
     * @param unknown2 Pointer to an unknown s32 where a value will be stored. 
     * @return         0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x6A53F3F8, version = 150)
	@HLEFunction(nid = 0x85F5F601, version = 660)
	public int sceSysconBatteryGetStatusCap(@BufferInfo(usage=Usage.out) TPointer32 unknown1, @BufferInfo(usage=Usage.out) TPointer32 unknown2) {
    	unknown1.setValue(getBatteryStatusCap1());
    	unknown2.setValue(getBatteryStatusCap2());
    	return 0;
    }

    /**
     * Get the battery full capacity.
     *
     * @param capAddr Pointer to a s32 where the capacity will be stored.
     * @return        0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x71135D7D, version = 150)
	@HLEFunction(nid = 0x4C871BEA, version = 660)
	public int sceSysconBatteryGetFullCap(@BufferInfo(usage=Usage.out) TPointer32 capAddr) {
    	capAddr.setValue(Battery.getFullCapacity());
    	return 0;
    }

    /**
     * Get the battery cycle.
     *
     * @param cycleAddr Pointer to a s32 where the cycle will be stored.
     * @return          0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0xB5105D51, version = 150)
	@HLEFunction(nid = 0x68AF19F1, version = 660)
	public int sceSysconBatteryGetCycle(@BufferInfo(usage=Usage.out) TPointer32 cycleAddr) {
    	cycleAddr.setValue(getBatteryCycle());
    	return 0;
    }

    /**
     * Get the battery limit time.
     *
     * @param timeAddr Pointer to a s32 where the limit time will be stored.
     * @return         0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x284FE366, version = 150)
	@HLEFunction(nid = 0x4D5A19BB, version = 660)
	public int sceSysconBatteryGetLimitTime(@BufferInfo(usage=Usage.out) TPointer32 timeAddr) {
    	timeAddr.setValue(getBatteryLimitTime());
    	return 0;
    }

    /**
     * Get the battery temperature.
     *
     * @param tempAddr Pointer to a s32 where the temperature will be stored.
     * @return         0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x70C10E61, version = 150)
	@HLEFunction(nid = 0xCE8B6633, version = 660)
	public int sceSysconBatteryGetTemp(@BufferInfo(usage=Usage.out) TPointer32 tempAddr) {
    	tempAddr.setValue(Battery.getTemperature());
    	return 0;
    }

    /**
     * Get the battery electric charge.
     *
     * @param elecAddr Pointer to a s32 where the charge will be stored.
     * @return         0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x373EC933, version = 150)
	@HLEFunction(nid = 0x483088B0, version = 660)
	public int sceSysconBatteryGetElec(@BufferInfo(usage=Usage.out) TPointer32 elecAddr) {
    	elecAddr.setValue(getBatteryElec());
    	return 0;
    }

    /**
     * Get the battery voltage.
     *
     * @param voltAddr Pointer to a s32 where the voltage will be stored.
     * @return         0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x8BDEBB1E, version = 150)
	@HLEFunction(nid = 0xA7DB34BB, version = 660)
	public int sceSysconBatteryGetVolt(@BufferInfo(usage=Usage.out) TPointer32 voltAddr) {
    	voltAddr.setValue(Battery.getVoltage());
    	return 0;
    }

    /**
     * Read the PSP clock.
     *
     * @param clockAddr Pointer to a s32 where the clock will be stored.
     * @return          0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0xC4D66C1D, version = 150)
	@HLEFunction(nid = 0xF436BB12, version = 660)
	public int sceSysconReadClock(@BufferInfo(usage=Usage.out) TPointer32 clockAddr) {
    	clockAddr.setValue(readClock());
    	return 0;
    }

    /**
     * Read the PSP alarm.
     *
     * @param alarmAddr Pointer to a s32 where the alarm will be stored.
     * @return          0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x7A805EE4, version = 150)
	@HLEFunction(nid = 0xF2AE6D5E, version = 660)
	public int sceSysconReadAlarm(@BufferInfo(usage=Usage.out) TPointer32 alarmAddr) {
    	alarmAddr.setValue(readAlarm());
    	return 0;
    }

    /**
     * Set the PSP alarm.
     *
     * @param alarm The alarm value to set the PSP alarm to.
     * @return      0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x6C911742, version = 150)
	@HLEFunction(nid = 0x80711575, version = 660)
	public int sceSysconWriteAlarm(int alarm) {
    	writeAlarm(alarm);
    	return 0;
    }

    /**
     * Send a command to the syscon doing nothing.
     *
     * @return      0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0xE6B74CB9, version = 150)
	public int sceSysconNop() {
    	return 0;
    }

    /**
     * Set the low battery callback, that will be ran when the battery is low.
     *
     * @param callback         The callback function.
     * @param callbackArgument The second argument that will be passed to the callback.
     * @return                 0.
     */
	@HLEFunction(nid = 0xAD555CE5, version = 150)
	@HLEFunction(nid = 0x599EB8A0, version = 660)
	public int sceSysconSetLowBatteryCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_LOW_BATTERY);
    }

    @HLEUnimplemented
	@HLEFunction(nid = 0xA3406117, version = 150)
	public boolean sceSysconIsAcSupplied() {
    	// Has no parameters
    	return true;
    }

    /**
     * Set the Ac supply callback, that will be ran when the PSP Ac power is (dis)connected (probably).
     *
     * @param callback         The callback function.
     * @param callbackArgument The second argument that will be passed to the callback.
     * @return                 0.
     */
	@HLEFunction(nid = 0xE540E532, version = 150)
	@HLEFunction(nid = 0x657DCEF7, version = 660)
	public int sceSysconSetAcSupplyCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_AC_SUPPLY);
    }

    /**
     * Set the power control
     *
     * @param unknown1 Unknown.
     * @param unknown2 Unknown.
     * @return         0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0xBE27FE66, version = 150)
	@HLEFunction(nid = 0xEDD3AB8B, version = 660)
	public int sceSysconCtrlPower(int unknown1, int unknown2) {
    	return 0;
    }

    /**
     * Set the voltage.
     *
     * @param unknown1 Unknown.
     * @param unknown2 Unknown.
     * @return         0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x01677F91, version = 150)
	@HLEFunction(nid = 0xF7BCD2A6, version = 660)
	public int sceSysconCtrlVoltage(int unknown1, int unknown2) {
    	return 0;
    }

    /**
     * Get the headphone connection.
     * 
     * @return 1 if the headphone is connected, 0 otherwise.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0xE0DDFE18, version = 150)
	@HLEFunction(nid = 0x2D6F2728, version = 660)
	public int sceSysconGetHPConnect() {
    	// Has no parameters
    	return 0;
    }

    /**
     * Set the tachyon watchdog timer.
     *
     * @param wdt	The timer value (0 - 0x7F).
     * @return		0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x2EE82492, version = 150)
	public int sceSysconCtrlTachyonWDT(int wdt) {
    	return 0;
    }

    /**
     * Get the wake up factor (?).
     *
     * @param factor	Pointer to a buffer where the factor will be stored.
     * @return			0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0xF775BC34, version = 150)
	@HLEFunction(nid = 0xF33E1B14, version = 660)
	public int sceSysconGetWakeUpFactor(@BufferInfo(usage=Usage.out) TPointer32 factor) {
    	return 0;
    }

    /**
     * Set the bluetooth power.
     * 
     * @param power Set the bluetooth power.
     * @return      0 on success.
     */
    @HLEUnimplemented
	@HLEFunction(nid = 0x9C4266FC, version = 150)
	@HLEFunction(nid = 0x7E3A82AF, version = 660)
	public int sceSysconCtrlBtPower(boolean power) {
    	return 0;
    }

    @HLEUnimplemented
	@HLEFunction(nid = 0x765775EB, version = 661)
	public int sceSyscon_driver_765775EB(boolean power) {
    	return 0;
    }

	@HLEFunction(nid = 0x3B657A27)
	public int sceSysconGetTachyonTemp(TPointer32 tempAddr) {
    	tempAddr.setValue(getTachyonTemp());

    	return 0;
    }

	@HLEUnimplemented
	@HLEFunction(nid = 0x011AC062, version = 150)
	public int sceSysconBatteryGetElecAD() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x01792223, version = 150)
	public int sceSysconGetHoldSwitch() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x031CCDD7, version = 150)
	public int sceSysconBatteryGetSerial() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x040982CD, version = 150)
	public int sceSyscon_driver_040982CD() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x05734D21, version = 150)
	public int sceSysconIsFalling() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x081826B4, version = 150)
	public int sceSysconSuspend() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x09076E54, version = 150)
	public int sceSysconGetBattVoltAD() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x0A771482, version = 150)
	public int sceSysconInit() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x0E4FC766, version = 150)
	public int sceSysconSetPollingMode() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x0E5FA7EA, version = 150)
	public int sceSysconGetFallingDetectTime() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x1083C71D, version = 150)
	public int sceSysconGetWlanLedCtrl() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x12518439, version = 150)
	public int sceSyscon_driver_12518439() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x138747DE, version = 150)
	public int sceSysconGetUmdSwitch() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x1602ED0D, version = 150)
	public int sceSysconCmdCancel() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x17E7753D, version = 150)
	public int sceSysconPowerSuspend() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x1C5D4229, version = 150)
	public int sceSysconCtrlTachyonVmePower() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x204F23FF, version = 150)
	public int sceSysconGetBaryonStatus2() {
		return 0;
	}

	@HLEFunction(nid = 0x21AC8621, version = 150)
	public int sceSysconSetHRPowerCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_HR_POWER);
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x248335CD, version = 150)
	public int sceSyscon_driver_248335CD() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x26307D84, version = 150)
	public int sceSyscon_driver_26307D84() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x27F94EE6, version = 150)
	public int sceSysconForbidChargeBattery() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x28F7032E, version = 150)
	public int _sceSysconGetUsbPowerType() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x294A7ED9, version = 150)
	public int sceSysconGetGSensorCarib() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x2B6BA4AB, version = 150)
	public int sceSysconBatteryGetVoltAD() {
		return 0;
	}

	@HLEFunction(nid = 0x2B7A0D32, version = 150)
	public int sceSysconSetAlarmCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_ALARM);
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x2BE8EBC2, version = 150)
	public int sceSysconSetUSBStatus() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x3032943A, version = 150)
	public int sceSysconGetBtPowerCtrl() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x327A82F2, version = 150)
	public int sceSysconCtrlAnalogXYPolling() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x374373A8, version = 150)
	public int sceSyscon_driver_374373A8() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x38CBE06E, version = 150)
	public int sceSysconGetTachyonWDTStatus() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x38DA2411, version = 150)
	public int sceSysconGetBattVolt() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x3932315D, version = 150)
	public int sceSysconGetTachyonAvcPowerCtrl() {
		return 0;
	}

	@HLEFunction(nid = 0x39456DE1, version = 150)
	public int sceSysconSetHRWakeupCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_HR_WAKEUP);
	}

	@HLEFunction(nid = 0x399708EB, version = 150)
	public int sceSysconSetHoldSwitchCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_HOLD_SWITCH);
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x3DE38336, version = 150)
	public int sceSysconReadPommelReg() {
		return 0;
	}

	@HLEFunction(nid = 0x3E0C521B, version = 150)
	public int sceSysconSetGSensorCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_GSENSOR);
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x3E16A759, version = 150)
	public int sceSysconBatteryGetCap() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x3E4BD909, version = 150)
	public int sceSysconGetDigitalKey() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x3FA9F842, version = 150)
	public int sceSysconBatteryGetStatus() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x3FB3FD08, version = 150)
	public int sceSysconWriteClock() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x457D8D7C, version = 150)
	public int sceSysconCtrlLcdPower() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x47378317, version = 150)
	public int sceSysconCtrlUsbPower() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x47C04A04, version = 150)
	public int sceSysconGetPowerSwitch() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x56931095, version = 150)
	public int sceSysconResume() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x579A30EA, version = 150)
	public int sceSysconGetBtSwitch() {
		return 0;
	}

	@HLEFunction(nid = 0x5C4C1130, version = 150)
	public int sceSysconSetBtPowerCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_BT_POWER);
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x5EDEDED6, version = 150)
	public int sceSysconPermitChargeBattery() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x5EE92F3C, version = 150)
	public int sceSysconSetDebugHandlers() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x64257B5C, version = 150)
	public int sceSysconGetHddPowerCtrl() {
		return 0;
	}

	@HLEFunction(nid = 0x672B79E8, version = 150)
	public int sceSysconSetHPConnectCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_HP_CONNECT);
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x67B66898, version = 150)
	public int sceSysconGetBtPowerStatus() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x6C388E02, version = 150)
	public int sceSyscon_driver_6C388E02() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x755CF72B, version = 150)
	public int sceSyscon_driver_755CF72B() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x7686E7A7, version = 150)
	public int sceSysconBatteryGetInfo() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x806D4D6C, version = 150)
	public int sceSysconWritePolestarReg() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x82D9F1BB, version = 150)
	public int sceSysconCtrlTachyonVoltage() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x833017E5, version = 150)
	public int sceSysconGetDvePowerCtrl() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x862A93DE, version = 150)
	public int _sceSysconGetBaryonTimeStamp() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x86D4CAD8, version = 150)
	public int sceSysconGetBaryonStatus() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x88EAAB07, version = 150)
	public int sceSysconCtrlDvePower() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x89A2024D, version = 150)
	public int sceSysconCtrlCharge() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x8DD190A1, version = 150)
	public int sceSysconBatteryGetIFC() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x8E54A128, version = 150)
	public int sceSysconBatteryGetRCap() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x8F4AD2CA, version = 150)
	public int sceSysconBatteryNop() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x90EAEA2B, version = 150)
	public int sceSyscon_driver_90EAEA2B() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x92D16FC7, version = 150)
	public int sceSysconEnd() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x93A3B23E, version = 150)
	public int sceSysconSetGSensorCarib() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x9497E906, version = 150)
	public int sceSysconGetLcdPowerCtrl() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x97765E27, version = 150)
	public int sceSyscon_driver_97765E27() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x9BC5E33B, version = 150)
	public int sceSysconCtrlTachyonAvcPower(boolean power) {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x9E82A08C, version = 150)
	public int sceSysconGetTachyonVmePowerCtrl() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x9F39BDC8, version = 150)
	public int sceSysconCtrlTachyonAwPower() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0x9FB6B045, version = 150)
	public int sceSysconGetVideoCable() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xA0FA8CF7, version = 150)
	public int sceSysconCtrlHddPower() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xA2DAACB4, version = 150)
	public int sceSysconWriteGSensorReg() {
		return 0;
	}

	@HLEFunction(nid = 0xAA1B32D4, version = 150)
	public int sceSysconSetAcSupply2Callback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_AC_SUPPLY2);
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xB5B06B81, version = 150)
	public int sceSysconGetWlanPowerCtrl() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xB5FA7580, version = 150)
	public int sceSysconGetGSensorVersion() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xB72DDFD2, version = 150)
	public int sceSysconSetAffirmativeRertyMode() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xB761D385, version = 150)
	public int sceSyscon_driver_B761D385() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xBADF1260, version = 150)
	public int sceSyscon_driver_BADF1260() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xC56D0B5A, version = 150)
	public int sceSysconCtrlGSensor() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xC8DB7B74, version = 150)
	public int sceSysconIsAlarmed() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xD06FA2C6, version = 150)
	public int sceSysconBatteryGetTempAD() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xD1216838, version = 150)
	public int sceSysconReadGSensorReg() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xD1B501E8, version = 150)
	public int sceSysconWritePommelReg() {
		return 0;
	}

	@HLEFunction(nid = 0xD2C053E7, version = 150)
	public int sceSysconSetWlanPowerCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_WLAN_POWER);
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xD38A3708, version = 150)
	public int sceSysconGetHRPowerCtrl() {
		return 0;
	}

	@HLEFunction(nid = 0xD6C2FD5F, version = 150)
	public int sceSysconSetUmdSwitchCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_UMD_SWITCH);
	}

	@HLEFunction(nid = 0xD76A105E, version = 150)
	public int sceSysconSetPowerSwitchCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_POWER_SWITCH);
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xD8471760, version = 150)
	public int sceSysconReadPolestarReg() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xDEB91FF2, version = 150)
	public int sceSysconGetTachyonAwPowerCtrl() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xDF20C984, version = 150)
	public int sceSyscon_driver_DF20C984() {
		return 0;
	}

	@HLEFunction(nid = 0xE19BC2DF, version = 150)
	public int sceSysconSetBtSwitchCallback(TPointerFunction callback, int callbackArgument) {
    	return hleSysconSetCallback(callback, callbackArgument, SceSysconCallbacks.SYSCON_CB_BT_SWITCH);
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xE58B9388, version = 150)
	public int sceSysconGetHRPowerStatus() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xE8C20DB5, version = 150)
	public int sceSysconGetGValue() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xEB11E9DE, version = 150)
	public int sceSysconGetUsbPowerCtrl() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xEF31EF4E, version = 150)
	public int sceSysconGetHRWakeupStatus() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xF0D1443F, version = 150)
	public int sceSysconGetPowerError() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xF350F666, version = 150)
	public int sceSysconCmdSync() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xF87A1D11, version = 150)
	public int sceSysconBatteryGetChargeTime() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xF8F6E1F4, version = 150)
	public int sceSysconBatteryGetTotalElec() {
		return 0;
	}

	@HLEUnimplemented
	@HLEFunction(nid = 0xFB148FB6, version = 150)
	public int sceSysconGetPolestarVersion() {
		return 0;
	}
}
