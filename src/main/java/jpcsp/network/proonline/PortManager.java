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
package jpcsp.network.proonline;

import java.util.LinkedList;
import java.util.List;

import jpcsp.HLE.modules.sceNetApctl;
import jpcsp.network.upnp.UPnP;

/**
 * @author gid15
 *
 */
public class PortManager {
	private List<String> hosts = new LinkedList<String>();
	private List<PortInfo> portInfos = new LinkedList<PortInfo>();
	private UPnP upnp;
	private String localIPAddress;
	private final static int portLeaseDuration = 0;
	private final static String portDescription = "Jpcsp ProOnline Network";
	private final static boolean SUPPORTS_MAPPING_FOR_MULTIPLE_REMOTE_HOSTS = false;
	private final static String ALL_REMOTE_HOSTS = "";

	private static class PortInfo {
		int port;
		String protocol;

		public PortInfo(int port, String protocol) {
			this.port = port;
			this.protocol = protocol;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof PortInfo) {
				PortInfo portInfo = (PortInfo) obj;
				return port == portInfo.port && protocol.equals(portInfo.protocol);
			}
			return super.equals(obj);
		}
	}

	public PortManager(UPnP upnp) {
		this.upnp = upnp;
		localIPAddress = sceNetApctl.getLocalHostIP();
	}

	protected String getLocalIPAddress() {
		return localIPAddress;
	}

	public synchronized void addHost(String host) {
		if (hosts.contains(host)) {
			return;
		}

		if (SUPPORTS_MAPPING_FOR_MULTIPLE_REMOTE_HOSTS) {
			// Open all the ports to this new host
			for (PortInfo portInfo : portInfos) {
				upnp.getIGD().addPortMapping(upnp, host, portInfo.port, portInfo.protocol, portInfo.port, getLocalIPAddress(), portDescription, portLeaseDuration);
			}
		} else {
			if (hosts.isEmpty()) {
				for (PortInfo portInfo : portInfos) {
					upnp.getIGD().addPortMapping(upnp, ALL_REMOTE_HOSTS, portInfo.port, portInfo.protocol, portInfo.port, getLocalIPAddress(), portDescription, portLeaseDuration);
				}
			}
		}

		hosts.add(host);
	}

	public synchronized void removeHost(String host) {
		if (!hosts.contains(host)) {
			return;
		}

		hosts.remove(host);

		if (SUPPORTS_MAPPING_FOR_MULTIPLE_REMOTE_HOSTS) {
			// Remove all the port mappings from this host
			for (PortInfo portInfo : portInfos) {
				upnp.getIGD().deletePortMapping(upnp, host, portInfo.port, portInfo.protocol);
			}
		} else {
			if (hosts.isEmpty()) {
				for (PortInfo portInfo : portInfos) {
					upnp.getIGD().deletePortMapping(upnp, ALL_REMOTE_HOSTS, portInfo.port, portInfo.protocol);
				}
			}
		}
	}

	public synchronized void addPort(int port, String protocol) {
		PortInfo portInfo = new PortInfo(port, protocol);
		if (portInfos.contains(portInfo)) {
			return;
		}

		if (SUPPORTS_MAPPING_FOR_MULTIPLE_REMOTE_HOSTS) {
			// All the new port mapping for all the hosts
			for (String host : hosts) {
				upnp.getIGD().addPortMapping(upnp, host, port, protocol, port, getLocalIPAddress(), portDescription, portLeaseDuration);
			}
		} else {
			upnp.getIGD().addPortMapping(upnp, ALL_REMOTE_HOSTS, port, protocol, port, getLocalIPAddress(), portDescription, portLeaseDuration);
		}

		portInfos.add(portInfo);
	}

	public synchronized void removePort(int port, String protocol) {
		PortInfo portInfo = new PortInfo(port, protocol);
		if (!portInfos.contains(portInfo)) {
			return;
		}

		if (SUPPORTS_MAPPING_FOR_MULTIPLE_REMOTE_HOSTS) {
			// Remove the port mapping for all the hosts
			for (String host : hosts) {
				upnp.getIGD().deletePortMapping(upnp, host, port, protocol);
			}
		} else {
			upnp.getIGD().deletePortMapping(upnp, ALL_REMOTE_HOSTS, port, protocol);
		}

		portInfos.remove(portInfo);
	}

	public synchronized void clear() {
		// Remove all the hosts
		while (!hosts.isEmpty()) {
			String host = hosts.get(0);
			removeHost(host);
		}

		// ...and remove all the ports
		while (!portInfos.isEmpty()) {
			PortInfo portInfo = portInfos.get(0);
			removePort(portInfo.port, portInfo.protocol);
		}
	}
}
