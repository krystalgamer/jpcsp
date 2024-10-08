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
package jpcsp.network.adhoc;

import static jpcsp.HLE.modules.sceNet.convertMacAddressToString;
import static jpcsp.HLE.modules.sceNetAdhoc.ANY_MAC_ADDRESS;
import static jpcsp.HLE.modules.sceNetAdhoc.isAnyMacAddress;
import static jpcsp.HLE.modules.sceNetAdhoc.isMyMacAddress;
import static jpcsp.util.Utilities.writeBytes;

import java.net.InetAddress;

import jpcsp.HLE.Modules;
import jpcsp.hardware.Wlan;
import jpcsp.memory.IMemoryReader;
import jpcsp.memory.MemoryReader;

/**
 * @author gid15
 *
 * An AdhocMessage is consisting of:
 * - 6 bytes for the MAC address of the message sender
 * - 6 bytes for the MAC address of the message recipient
 * - n bytes for the message data
 */
public abstract class AdhocMessage {
	// Unique message id which is used to avoid processing one message multiple
	// times even if it is received over multiple broadcast interfaces.
	private int id;
	protected byte[] fromMacAddress = new byte[Wlan.MAC_ADDRESS_LENGTH];
	protected byte[] toMacAddress = new byte[Wlan.MAC_ADDRESS_LENGTH];
	protected byte[] data = new byte[0];
	protected int offset;
	public static final int MAX_HEADER_SIZE = 17;

	public AdhocMessage() {
		id = getNewId();
		init(0, 0, ANY_MAC_ADDRESS);
	}

	public AdhocMessage(byte[] fromMacAddress, byte[] toMacAddress) {
		id = getNewId();
		init(0, 0, toMacAddress);
	}

	public AdhocMessage(byte[] message, int length) {
		id = getNewId();
		setMessage(message, length);
	}

	public AdhocMessage(int address, int length) {
		id = getNewId();
		init(address, length, ANY_MAC_ADDRESS);
	}

	public AdhocMessage(int address, int length, byte[] toMacAddress) {
		id = getNewId();
		init(address, length, toMacAddress);
	}

	private int getNewId() {
		return Modules.sceNetAdhocModule.getNewAdhocMessageId();
	}

	public int getId() {
		return id;
	}

	public boolean hasId() {
		return id != 0;
	}

	public void setId(int id) {
		this.id = id;
	}

	public void clearId() {
		id = 0;
	}

	protected void addToBytes(byte[] bytes, byte value) {
		bytes[offset++] = value;
	}

	protected void addToBytes(byte[] bytes, byte[] src) {
		System.arraycopy(src, 0, bytes, offset, src.length);
		offset += src.length;
	}

	protected void addInt32ToBytes(byte[] bytes, int value) {
		addToBytes(bytes, (byte) value);
		addToBytes(bytes, (byte) (value >> 8));
		addToBytes(bytes, (byte) (value >> 16));
		addToBytes(bytes, (byte) (value >> 24));
	}

	protected byte copyByteFromBytes(byte[] bytes) {
		return bytes[offset++];
	}

	protected void copyFromBytes(byte[] bytes, byte[] dst) {
		System.arraycopy(bytes, offset, dst, 0, dst.length);
		offset += dst.length;
	}

	protected int copyInt32FromBytes(byte[] bytes) {
		return (copyByteFromBytes(bytes) & 0xFF) |
		       ((copyByteFromBytes(bytes) & 0xFF) << 8) |
		       ((copyByteFromBytes(bytes) & 0xFF) << 16) |
		       ((copyByteFromBytes(bytes) & 0xFF) << 24);
	}

	private void init(int address, int length, byte[] toMacAddress) {
		init(address, length, Wlan.getMacAddress(), toMacAddress);
	}

	private void init(int address, int length, byte[] fromMacAddress, byte[] toMacAddress) {
		setFromMacAddress(fromMacAddress);
		setToMacAddress(toMacAddress);
		data = new byte[length];
		if (length > 0 && address != 0) {
    		IMemoryReader memoryReader = MemoryReader.getMemoryReader(address, length, 1);
    		for (int i = 0; i < length; i++) {
    			data[i] = (byte) memoryReader.readNext();
    		}
		}
	}

	public void setData(byte[] data) {
		this.data = new byte[data.length];
		System.arraycopy(data, 0, this.data, 0, data.length);
	}

	public void setDataInt32(int value) {
		data = new byte[4];
		for (int i = 0; i < 4; i++) {
			data[i] = (byte) (value >> (i * 8));
		}
	}

	public int getDataInt32() {
		int value = 0;

		for (int i = 0; i < 4 && i < data.length; i++) {
			value |= (data[i] & 0xFF) << (i * 8);
		}

		return value;
	}

	public abstract byte[] getMessage();

	public int getMessageLength() {
		return getDataLength();
	}

	public abstract void setMessage(byte[] message, int length);

	public void writeDataToMemory(int address) {
		writeBytes(address, getDataLength(), data, 0);
	}

	public void writeDataToMemory(int address, int maxLength) {
		writeBytes(address, Math.min(getDataLength(), maxLength), data, 0);
	}

	public void writeDataToMemory(int address, int offset, int maxLength) {
		writeBytes(address, Math.min(getDataLength() - offset, maxLength), data, offset);
	}

	public byte[] getData() {
		return data;
	}

	public int getDataLength() {
		return data.length;
	}

	public byte[] getFromMacAddress() {
		return fromMacAddress;
	}

	public byte[] getToMacAddress() {
		return toMacAddress;
	}

	public void setFromMacAddress(byte[] fromMacAddress) {
		System.arraycopy(fromMacAddress, 0, this.fromMacAddress, 0, this.fromMacAddress.length);
	}

	public void setToMacAddress(byte[] toMacAddress) {
		System.arraycopy(toMacAddress, 0, this.toMacAddress, 0, this.toMacAddress.length);
	}

	public void setAlreadyReceived() {
		if (hasId()) {
			Modules.sceNetAdhocModule.setAlreadyReceived(this);
		}
	}

	public boolean isForMe(int port, InetAddress address) {
		if (hasId()) {
			// The same message can be received over multiple broadcasting interfaces.
			// Make sure we are processing such a message only once.
			if (Modules.sceNetAdhocModule.isAlreadyReceived(this)) {
				return false;
			}
		}

		if (isMyMacAddress(fromMacAddress)) {
    		// This packet is coming from myself, ignore it
			return false;
		}

		return isAnyMacAddress(toMacAddress) || isMyMacAddress(toMacAddress);
	}

	@Override
	public String toString() {
		return String.format("%s[id=0x%X, fromMacAddress=%s, toMacAddress=%s, dataLength=%d]", getClass().getSimpleName(), getId(), convertMacAddressToString(fromMacAddress), convertMacAddressToString(toMacAddress), getDataLength());
	}
}
