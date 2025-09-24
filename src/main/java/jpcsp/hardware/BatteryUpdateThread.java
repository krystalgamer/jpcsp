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

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import jpcsp.util.OS;

import java.util.Arrays;
import java.util.List;

/**
 * @author gid15
 */
public class BatteryUpdateThread extends Thread {

    private long sleepMillis;
    private Battery battery;

    public BatteryUpdateThread(long sleepMillis, Battery battery)
    {
        this.battery = battery;
        this.sleepMillis = sleepMillis;

        this.setDaemon(true);
        this.setName("Battery Drain");
    }

    @Override
    public void run() {
        if (OS.isWindows) updateWindows();
        else if (OS.isLinux) updateLinux();
        else if (OS.isMac) updateMac();
        else updateGeneric();
    }

    private void updateWindows() {
        while (true) {
            this.battery.setPluggedIn(true);
            this.battery.setPresent(true);

            sleepMillis(5 * 1000); // Wait five second between updates
        }
    }

    static private void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void updateLinux() {
        updateGeneric();
    }

    private void updateMac() {
        updateGeneric();
    }

    private void updateGeneric() {
        while (true) {
            this.battery.setCurrentPowerPercent(100);
            sleepMillis(sleepMillis);
        }
    }
}
