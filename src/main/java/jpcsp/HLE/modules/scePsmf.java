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

import jpcsp.Memory;
import jpcsp.HLE.BufferInfo;
import jpcsp.HLE.CheckArgument;
import jpcsp.HLE.HLEFunction;
import jpcsp.HLE.HLELogging;
import jpcsp.HLE.HLEModule;
import jpcsp.HLE.HLEUnimplemented;
import jpcsp.HLE.SceKernelErrorException;
import jpcsp.HLE.TPointer;
import jpcsp.HLE.TPointer32;
import jpcsp.HLE.BufferInfo.LengthInfo;
import jpcsp.HLE.BufferInfo.Usage;

import static jpcsp.HLE.modules.sceMpeg.getMpegVersion;
import static jpcsp.HLE.modules.sceMpeg.mpegTimestampPerSecond;
import static jpcsp.util.Utilities.endianSwap16;
import static jpcsp.util.Utilities.endianSwap32;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import jpcsp.HLE.Modules;
import jpcsp.HLE.kernel.types.SceKernelErrors;
import jpcsp.HLE.kernel.types.SceMpegRingbuffer;
import jpcsp.util.Utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class scePsmf extends HLEModule {
    public static Logger log = Modules.getLogger("scePsmf");
    public static final int PSMF_AVC_STREAM = 0;
    public static final int PSMF_ATRAC_STREAM = 1;
    public static final int PSMF_PCM_STREAM = 2;
    public static final int PSMF_DATA_STREAM = 3;
    public static final int PSMF_AUDIO_STREAM = 15;
    public static final int PSMF_VIDEO_STREAM_ID = 0xE0;
    public static final int PSMF_AUDIO_STREAM_ID = 0xBD;
    // At least 2048 bytes of MPEG data is provided when analysing the MPEG header
    public static final int MPEG_HEADER_BUFFER_MINIMUM_SIZE = 2048;
    public static final int PSMF_MAGIC_OFFSET = 0x0;
    public static final int PSMF_STREAM_VERSION_OFFSET = 0x4;
    public static final int PSMF_STREAM_OFFSET_OFFSET = 0x8;
    public static final int PSMF_STREAM_SIZE_OFFSET = 0xC;
    public static final int PSMF_FIRST_TIMESTAMP_OFFSET = 0x54;
    public static final int PSMF_LAST_TIMESTAMP_OFFSET = 0x5A;
    public static final int PSMF_NUMBER_STREAMS_OFFSET = 0x80;
    public static final int PSMF_FRAME_WIDTH_OFFSET = 0x8E;
    public static final int PSMF_FRAME_HEIGHT_OFFSET = 0x8F;

    @Override
    public void start() {
        psmfHeaderMap = new HashMap<Integer, PSMFHeader>();

        super.start();
    }

    private HashMap<Integer, PSMFHeader> psmfHeaderMap;

    // Entry class for the PSMF streams.
    public static class PSMFStream {
        private int streamType = -1;
        private int streamChannel = -1;
        private int streamNumber;
    	private int EPMapNumEntries;
    	private int EPMapOffset;
    	private List<PSMFEntry> EPMap;
    	private int frameWidth;
    	private int frameHeight;

        public PSMFStream(int streamNumber) {
        	this.streamNumber = streamNumber;
        }

        public int getStreamType() {
            return streamType;
        }

        public int getStreamChannel() {
            return streamChannel;
        }

		public int getStreamNumber() {
			return streamNumber;
		}

		public boolean isStreamOfType(int type) {
			if (streamType == type) {
				return true;
			}
			if (type == PSMF_AUDIO_STREAM) {
				// Atrac or PCM
				return streamType == PSMF_ATRAC_STREAM || streamType == PSMF_PCM_STREAM;
			}

			return false;
		}

		public void readMPEGVideoStreamParams(Memory mem, int addr, byte[] mpegHeader, int offset, PSMFHeader psmfHeader) {
            int streamID = read8(mem, addr, mpegHeader, offset);                // 0xE0
            int privateStreamID = read8(mem, addr, mpegHeader, offset + 1);     // 0x00
            int unk1 = read8(mem, addr, mpegHeader, offset + 2);                // Found values: 0x20/0x21 
            int unk2 = read8(mem, addr, mpegHeader, offset + 3);                // Found values: 0x44/0xFB/0x75
            EPMapOffset = endianSwap32(readUnaligned32(mem, addr, mpegHeader, offset + 4));
            EPMapNumEntries = endianSwap32(readUnaligned32(mem, addr, mpegHeader, offset + 8));
            frameWidth = read8(mem, addr, mpegHeader, offset + 12) * 0x10;  // PSMF video width (bytes per line).
            frameHeight = read8(mem, addr, mpegHeader, offset + 13) * 0x10; // PSMF video heigth (bytes per line).

            if (log.isInfoEnabled()) {
	            log.info(String.format("Found PSMF MPEG video stream data: streamID=0x%X, privateStreamID=0x%X, unk1=0x%X, unk2=0x%X, EPMapOffset=0x%x, EPMapNumEntries=%d, frameWidth=%d, frameHeight=%d", streamID, privateStreamID, unk1, unk2, EPMapOffset, EPMapNumEntries, frameWidth, frameHeight));
            }

            streamType = PSMF_AVC_STREAM;
            streamChannel = streamID & 0x0F;
        }

        public void readPrivateAudioStreamParams(Memory mem, int addr, byte[] mpegHeader, int offset, PSMFHeader psmfHeader) {
            int streamID = read8(mem, addr, mpegHeader, offset);                  // 0xBD
            int privateStreamID = read8(mem, addr, mpegHeader, offset + 1);       // 0x00
            int unk1 = read8(mem, addr, mpegHeader, offset + 2);                  // Always 0x20
            int unk2 = read8(mem, addr, mpegHeader, offset + 3);                  // Always 0x04
            int audioChannelConfig = read8(mem, addr, mpegHeader, offset + 14);   // 1 - mono, 2 - stereo
            int audioSampleFrequency = read8(mem, addr, mpegHeader, offset + 15); // 2 - 44khz

            if (log.isInfoEnabled()) {
	            log.info(String.format("Found PSMF MPEG audio stream data: streamID=0x%X, privateStreamID=0x%X, unk1=0x%X, unk2=0x%X, audioChannelConfig=%d, audioSampleFrequency=%d", streamID, privateStreamID, unk1, unk2, audioChannelConfig, audioSampleFrequency));
            }

            if (psmfHeader != null) {
            	psmfHeader.audioChannelConfig = audioChannelConfig;
            	psmfHeader.audioSampleFrequency = audioSampleFrequency;
            }

            streamType = ((privateStreamID & 0xF0) == 0 ? PSMF_ATRAC_STREAM : PSMF_PCM_STREAM);
            streamChannel = privateStreamID & 0x0F;
        }

        public void readUserDataStreamParams(Memory mem, int addr, byte[] mpegHeader, int offset, PSMFHeader psmfHeader) {
        	log.warn(String.format("Unknown User Data stream format"));
        	streamType = PSMF_DATA_STREAM;
        }
    }

    // Entry class for the EPMap.
    protected static class PSMFEntry {
        private int EPIndex;
        private int EPPicOffset;
        private int EPPts;
        private int EPOffset;
        private int id;

        public PSMFEntry(int id, int index, int picOffset, int pts, int offset) {
        	this.id = id;
            EPIndex = index;
            EPPicOffset = picOffset;
            EPPts = pts;
            EPOffset = offset;
        }

        public int getEntryIndex() {
            return EPIndex;
        }

        public int getEntryPicOffset() {
            return EPPicOffset;
        }

        public int getEntryPTS() {
            return EPPts;
        }

        public int getEntryOffset() {
            return EPOffset * SceMpegRingbuffer.ringbufferPacketSize;
        }

        public int getId() {
        	return id;
        }

		@Override
		public String toString() {
			return String.format("id=%d, index=0x%X, picOffset=0x%X, PTS=0x%X, offset=0x%X", getId(), getEntryIndex(), getEntryPicOffset(), getEntryPTS(), getEntryOffset());
		}
    }

    public static class PSMFHeader {
        private static final int size = 2048;

        // Header vars.
        public int mpegMagic;
        public int mpegRawVersion;
        public int mpegVersion;
        public int mpegOffset;
        public int mpegStreamSize;
        public long mpegFirstTimestamp;
        public long mpegLastTimestamp;
        public Date mpegFirstDate;
        public Date mpegLastDate;
        private int streamNum;
        private int audioSampleFrequency;
        private int audioChannelConfig;
        private int avcDetailFrameWidth;
        private int avcDetailFrameHeight;

        // Stream map.
        public List<PSMFStream> psmfStreams;
        private PSMFStream currentStream = null;
        private PSMFStream currentVideoStream = null;

        public PSMFHeader() {
        }

        public PSMFHeader(int bufferAddr, byte[] mpegHeader) {
            Memory mem = Memory.getInstance();

            int streamDataTotalSize = endianSwap32(readUnaligned32(mem, bufferAddr, mpegHeader, 0x50));
            int unk = endianSwap32(readUnaligned32(mem, bufferAddr, mpegHeader, 0x60));
            int streamDataNextBlockSize = endianSwap32(readUnaligned32(mem, bufferAddr, mpegHeader, 0x6A));      // General stream information block size.
            int streamDataNextInnerBlockSize = endianSwap32(readUnaligned32(mem, bufferAddr, mpegHeader, 0x7C)); // Inner stream information block size.
            streamNum = endianSwap16(read16(mem, bufferAddr, mpegHeader, PSMF_NUMBER_STREAMS_OFFSET));                  // Number of total registered streams.

            mpegMagic = read32(mem, bufferAddr, mpegHeader, PSMF_MAGIC_OFFSET);
            mpegRawVersion = read32(mem, bufferAddr, mpegHeader, PSMF_STREAM_VERSION_OFFSET);
            mpegVersion = getMpegVersion(mpegRawVersion);
            mpegOffset = endianSwap32(read32(mem, bufferAddr, mpegHeader, PSMF_STREAM_OFFSET_OFFSET));
            mpegStreamSize = endianSwap32(read32(mem, bufferAddr, mpegHeader, PSMF_STREAM_SIZE_OFFSET));
            mpegFirstTimestamp = readTimestamp(mem, bufferAddr, mpegHeader, PSMF_FIRST_TIMESTAMP_OFFSET);
            mpegLastTimestamp = readTimestamp(mem, bufferAddr, mpegHeader, PSMF_LAST_TIMESTAMP_OFFSET);
            avcDetailFrameWidth = read8(mem, bufferAddr, mpegHeader, PSMF_FRAME_WIDTH_OFFSET) << 4;
            avcDetailFrameHeight = read8(mem, bufferAddr, mpegHeader, PSMF_FRAME_HEIGHT_OFFSET) << 4;

            mpegFirstDate = convertTimestampToDate(mpegFirstTimestamp);
            mpegLastDate = convertTimestampToDate(mpegLastTimestamp);

            if (log.isDebugEnabled()) {
            	log.debug(String.format("PSMFHeader: version=0x%04X, firstTimestamp=%d, lastTimestamp=%d, streamDataTotalSize=%d, unk=0x%08X, streamDataNextBlockSize=%d, streamDataNextInnerBlockSize=%d, streamNum=%d", getVersion(), mpegFirstTimestamp, mpegLastTimestamp, streamDataTotalSize, unk, streamDataNextBlockSize, streamDataNextInnerBlockSize, streamNum));
            }

            if (isValid()) {
            	psmfStreams = readPsmfStreams(mem, bufferAddr, mpegHeader, this);

	            // PSP seems to default to stream 0.
	            if (psmfStreams.size() > 0) {
	            	setStreamNum(0);
	            }

	            // EPMap info:
	            // - Located at EPMapOffset (set by the AVC stream);
	            // - Each entry is composed by a total of 10 bytes:
	            //      - 1 byte: Reference picture index (RAPI);
	            //      - 1 byte: Reference picture offset from the current index;
	            //      - 4 bytes: PTS of the entry point;
	            //      - 4 bytes: Relative offset of the entry point in the MPEG data.
	            for (PSMFStream stream : psmfStreams) {
	            	stream.EPMap = new LinkedList<PSMFEntry>();
	            	int EPMapOffset = stream.EPMapOffset;
		            for (int i = 0; i < stream.EPMapNumEntries; i++) {
		                int index = read8(mem, bufferAddr, mpegHeader, EPMapOffset + i * 10);
		                int picOffset = read8(mem, bufferAddr, mpegHeader, EPMapOffset + 1 + i * 10);
		                int pts = endianSwap32(readUnaligned32(mem, bufferAddr, mpegHeader, EPMapOffset + 2 + i * 10));
		                int offset = endianSwap32(readUnaligned32(mem, bufferAddr, mpegHeader, EPMapOffset + 6 + i * 10));
		                PSMFEntry psmfEntry = new PSMFEntry(i, index, picOffset, pts, offset);
		                stream.EPMap.add(psmfEntry);
		                if (log.isDebugEnabled()) {
		                	log.debug(String.format("EPMap stream %d, entry#%d: %s", stream.getStreamChannel(), i, psmfEntry));
		                }
		            }
	            }
            }
        }

        private long readTimestamp(Memory mem, int bufferAddr, byte[] mpegHeader, int offset) {
            long timestamp = endianSwap32(readUnaligned32(mem, bufferAddr, mpegHeader, offset + 2)) & 0xFFFFFFFFL;
            timestamp |= ((long) read8(mem, bufferAddr, mpegHeader, offset + 1)) << 32;
            timestamp |= ((long) read8(mem, bufferAddr, mpegHeader, offset + 0)) << 40;

            return timestamp;
        }

        public boolean isValid() {
            return mpegFirstTimestamp == 90000 && mpegFirstTimestamp < mpegLastTimestamp && mpegLastTimestamp > 0;
        }

        public boolean isInvalid() {
        	return !isValid();
        }

        public int getVersion() {
            return mpegRawVersion;
        }

        public int getHeaderSize() {
            return size;
        }

        public int getStreamOffset() {
            return mpegOffset;
        }

        public int getStreamSize() {
            return mpegStreamSize;
        }

        public int getPresentationStartTime() {
            return (int) mpegFirstTimestamp;
        }

        public int getPresentationEndTime() {
            return (int) mpegLastTimestamp;
        }

        public int getVideoWidth() {
            return avcDetailFrameWidth;
        }

        public int getVideoHeight() {
            return avcDetailFrameHeight;
        }

        public int getAudioSampleFrequency() {
            return audioSampleFrequency;
        }

        public int getAudioChannelConfig() {
            return audioChannelConfig;
        }

        public int getEPMapEntriesNum() {
        	if (currentVideoStream == null) {
        		return 0;
        	}
            return currentVideoStream.EPMapNumEntries;
        }

        public boolean hasEPMap() {
        	return getEPMapEntriesNum() > 0;
        }

        public PSMFEntry getEPMapEntry(int id) {
        	if (!hasEPMap()) {
        		return null;
        	}
        	if (id < 0 || id >= currentVideoStream.EPMap.size()) {
        		return null;
        	}
            return currentVideoStream.EPMap.get(id);
        }

        public PSMFEntry getEPMapEntryWithTimestamp(int ts) {
        	if (!hasEPMap()) {
        		return null;
        	}

        	PSMFEntry foundEntry = null;
            for (PSMFEntry entry : currentVideoStream.EPMap) {
            	if (foundEntry == null || entry.getEntryPTS() <= ts) {
            		foundEntry = entry;
            	} else if (entry.getEntryPTS() > ts) {
                    break;
                }
            }

            return foundEntry;
        }

        public PSMFEntry getEPMapEntryWithOffset(int offset) {
        	if (!hasEPMap()) {
        		return null;
        	}

        	PSMFEntry foundEntry = null;
            for (PSMFEntry entry : currentVideoStream.EPMap) {
            	if (foundEntry == null || entry.getEntryOffset() <= offset) {
            		foundEntry = entry;
            	} else if (entry.getEntryOffset() > offset) {
                    break;
                }
            }

            return foundEntry;
        }

        public int getNumberOfStreams() {
            return streamNum;
        }

        public int getCurrentStreamNumber() {
        	if (!isValidCurrentStreamNumber()) {
        		return -1;
        	}
            return currentStream.getStreamNumber();
        }

        public boolean isValidCurrentStreamNumber() {
        	return currentStream != null;
        }

        public int getCurrentStreamType() {
        	if (!isValidCurrentStreamNumber()) {
        		return -1;
        	}
        	return currentStream.getStreamType();
        }

        public int getCurrentStreamChannel() {
        	if (!isValidCurrentStreamNumber()) {
        		return -1;
        	}
        	return currentStream.getStreamChannel();
        }

        public int getSpecificStreamNum(int type) {
        	int num = 0;
        	if (psmfStreams != null) {
	        	for (PSMFStream stream : psmfStreams) {
	        		if (stream.isStreamOfType(type)) {
	        			num++;
	        		}
	        	}
        	}

        	return num;
        }

        public void setStreamNum(int id) {
        	if (id < 0 || id >= psmfStreams.size()) {
        		currentStream = null;
        	} else {
        		currentStream = psmfStreams.get(id);

	            int type = getCurrentStreamType();
	            switch (type) {
	            	case PSMF_AVC_STREAM:
	            		currentVideoStream = currentStream;
	            		break;
	            	case PSMF_PCM_STREAM:
	            	case PSMF_ATRAC_STREAM:
	            		break;
	            }
            }
        }

        private int getStreamNumber(int type, int typeNum, int channel) {
        	if (psmfStreams != null) {
	        	for (PSMFStream stream : psmfStreams) {
	        		if (stream.isStreamOfType(type)) {
	        			if (typeNum <= 0) {
	        				if (channel < 0 || stream.getStreamChannel() == channel) {
	        					return stream.getStreamNumber();
	        				}
	        			}
	    				typeNum--;
	        		}
	        	}
        	}

        	return -1;
        }

        public boolean setStreamWithType(int type, int channel) {
        	int streamNumber = getStreamNumber(type, 0, channel);
        	if (streamNumber < 0) {
        		return false;
        	}
        	setStreamNum(streamNumber);

    		return true;
        }

        public boolean setStreamWithTypeNum(int type, int typeNum) {
        	int streamNumber = getStreamNumber(type, typeNum, -1);
        	if (streamNumber < 0) {
        		return false;
        	}
        	setStreamNum(streamNumber);

    		return true;
        }
    }

    public static int read8(Memory mem, int bufferAddr, byte[] buffer, int offset) {
    	if (buffer != null) {
    		return Utilities.read8(buffer, offset);
    	}
    	return mem.read8(bufferAddr + offset);
    }

    public static int readUnaligned32(Memory mem, int bufferAddr, byte[] buffer, int offset) {
    	if (buffer != null) {
    		return Utilities.readUnaligned32(buffer, offset);
    	}
    	return Utilities.readUnaligned32(mem, bufferAddr + offset);
    }

    public static int read32(Memory mem, int bufferAddr, byte[] buffer, int offset) {
    	if (buffer != null) {
    		return Utilities.readUnaligned32(buffer, offset);
    	}
    	return mem.read32(bufferAddr + offset);
    }

    public static int read16(Memory mem, int bufferAddr, byte[] buffer, int offset) {
    	if (buffer != null) {
    		return Utilities.readUnaligned16(buffer, offset);
    	}
    	return mem.read16(bufferAddr + offset);
    }

    public static Date convertTimestampToDate(long timestamp) {
        long millis = timestamp / (mpegTimestampPerSecond / 1000);
        return new Date(millis);
    }

    public static int getPsmfNumStreams(Memory mem, int addr, byte[] mpegHeader) {
    	return endianSwap16(read16(mem, addr, mpegHeader, sceMpeg.PSMF_NUMBER_STREAMS_OFFSET));    	
    }

    public static LinkedList<PSMFStream> readPsmfStreams(Memory mem, int addr, byte[] mpegHeader, PSMFHeader psmfHeader) {
    	int numStreams = getPsmfNumStreams(mem, addr, mpegHeader);

    	// Stream area:
        // At offset 0x82, each 16 bytes represent one stream.
        LinkedList<PSMFStream> streams = new LinkedList<PSMFStream>();

        // Parse the stream field and assign each one to it's type.
        int numberOfStreams = 0;
        for (int i = 0; i < numStreams; i++) {
            PSMFStream stream = null;
            int currentStreamOffset = 0x82 + i * 16;
            int streamID = read8(mem, addr, mpegHeader, currentStreamOffset);
            int subStreamID = read8(mem, addr, mpegHeader, currentStreamOffset + 1);
            if ((streamID & 0xF0) == PSMF_VIDEO_STREAM_ID) {
                stream = new PSMFStream(numberOfStreams);
                stream.readMPEGVideoStreamParams(mem, addr, mpegHeader, currentStreamOffset, psmfHeader);
            } else if (streamID == PSMF_AUDIO_STREAM_ID && subStreamID < 0x20) {
                stream = new PSMFStream(numberOfStreams);
                stream.readPrivateAudioStreamParams(mem, addr, mpegHeader, currentStreamOffset, psmfHeader);
            } else {
            	stream = new PSMFStream(numberOfStreams);
            	stream.readUserDataStreamParams(mem, addr, mpegHeader, currentStreamOffset, psmfHeader);
            }

            if (stream != null) {
                streams.add(stream);
                numberOfStreams++;
            }
        }

        return streams;
    }

    public TPointer32 checkPsmf(TPointer32 psmf) {
		int headerAddress = psmf.getValue(24);
		if (!psmfHeaderMap.containsKey(headerAddress)) {
    		throw new SceKernelErrorException(SceKernelErrors.ERROR_PSMF_NOT_FOUND);
		}

		return psmf;
    }

    public TPointer32 checkPsmfWithEPMap(TPointer32 psmf) {
    	psmf = checkPsmf(psmf);
    	PSMFHeader header = getPsmfHeader(psmf);
    	if (!header.hasEPMap()) {
        	if (log.isDebugEnabled()) {
        		log.debug(String.format("checkPsmfWithEPMap returning 0x%08X(ERROR_PSMF_NOT_FOUND)", SceKernelErrors.ERROR_PSMF_NOT_FOUND));
        	}
    		throw new SceKernelErrorException(SceKernelErrors.ERROR_PSMF_NOT_FOUND);
    	}

    	return psmf;
    }

    private PSMFHeader getPsmfHeader(TPointer32 psmf) {
		int headerAddress = psmf.getValue(24);
		return psmfHeaderMap.get(headerAddress);
    }

    @HLELogging(level="info")
    @HLEFunction(nid = 0xC22C8327, version = 150, checkInsideInterrupt = true, stackUsage = 0x50)
    public int scePsmfSetPsmf(TPointer32 psmf, TPointer bufferAddr) {
        PSMFHeader header = new PSMFHeader(bufferAddr.getAddress(), null);
        psmfHeaderMap.put(bufferAddr.getAddress(), header);

        // PSMF struct:
        // This is an internal system data area which is used to store
        // several parameters of the file being handled.
        // It's size ranges from 28 bytes to 52 bytes, since when a pointer to
        // a certain PSMF area does not exist (NULL), it's omitted from the struct
        // (e.g.: no mark data or non existant EPMap).
        psmf.setValue(0, header.getVersion());              // PSMF version.
        psmf.setValue(4, header.getHeaderSize());           // The PSMF header size (0x800).
        psmf.setValue(8, header.getStreamSize());           // The PSMF stream size.
        psmf.setValue(12, 0);                               // Grouping Period ID.
        psmf.setValue(16, 0);                               // Group ID.
        psmf.setValue(20, header.getCurrentStreamNumber()); // Current stream's number.
        psmf.setValue(24, bufferAddr.getAddress());         // Pointer to PSMF header.
        // psmf + 28 - Pointer to current PSMF stream info (video/audio).
        // psmf + 32 - Pointer to mark data (used for chapters in UMD_VIDEO).
        // psmf + 36 - Pointer to current PSMF stream grouping period.
        // psmf + 40 - Pointer to current PSMF stream group.
        // psmf + 44 - Pointer to current PSMF stream.
        // psmf + 48 - Pointer to PSMF EPMap.

        return 0;
    }

    @HLEFunction(nid = 0xC7DB3A5B, version = 150, checkInsideInterrupt = true, stackUsage = 0x50)
    public int scePsmfGetCurrentStreamType(@CheckArgument("checkPsmf") TPointer32 psmf, TPointer32 typeAddr, TPointer32 channelAddr) {
        PSMFHeader header = getPsmfHeader(psmf);
        typeAddr.setValue(header.getCurrentStreamType());
        channelAddr.setValue(header.getCurrentStreamChannel());

        if (log.isDebugEnabled()) {
            log.debug(String.format("scePsmfGetCurrentStreamType returning type=%d, channel=%d", typeAddr.getValue(), channelAddr.getValue()));
        }

        return 0;
    }

    @HLEFunction(nid = 0x28240568, version = 150, checkInsideInterrupt = true, stackUsage = 0x0)
    public int scePsmfGetCurrentStreamNumber(@CheckArgument("checkPsmf") TPointer32 psmf) {
        PSMFHeader header = getPsmfHeader(psmf);

        return header.getCurrentStreamNumber();
    }

    @HLEFunction(nid = 0x1E6D9013, version = 150, checkInsideInterrupt = true, stackUsage = 0x20)
    public int scePsmfSpecifyStreamWithStreamType(@CheckArgument("checkPsmf") TPointer32 psmf, int type, int ch) {
        PSMFHeader header = getPsmfHeader(psmf);
        if (!header.setStreamWithType(type, ch)) {
        	// Do not return SceKernelErrors.ERROR_PSMF_INVALID_ID, but set an invalid stream number.
        	header.setStreamNum(-1);
        }

        return 0;
    }

    @HLEFunction(nid = 0x4BC9BDE0, version = 150, checkInsideInterrupt = true, stackUsage = 0x40)
    public int scePsmfSpecifyStream(@CheckArgument("checkPsmf") TPointer32 psmf, int streamNum) {
        PSMFHeader header = getPsmfHeader(psmf);
        header.setStreamNum(streamNum);

        return 0;
    }

    @HLEFunction(nid = 0x76D3AEBA, version = 150, checkInsideInterrupt = true, stackUsage = 0x10)
    public int scePsmfGetPresentationStartTime(@CheckArgument("checkPsmf") TPointer32 psmf, TPointer32 startTimeAddr) {
        PSMFHeader header = getPsmfHeader(psmf);
        int startTime = header.getPresentationStartTime();
        startTimeAddr.setValue(startTime);
        if (log.isDebugEnabled()) {
            log.debug(String.format("scePsmfGetPresentationStartTime startTime=%d", startTime));
        }

        return 0;
    }

    @HLEFunction(nid = 0xBD8AE0D8, version = 150, checkInsideInterrupt = true, stackUsage = 0x10)
    public int scePsmfGetPresentationEndTime(@CheckArgument("checkPsmf") TPointer32 psmf, TPointer32 endTimeAddr) {
        PSMFHeader header = getPsmfHeader(psmf);
        int endTime = header.getPresentationEndTime();
        endTimeAddr.setValue(endTime);
        if (log.isDebugEnabled()) {
            log.debug(String.format("scePsmfGetPresentationEndTime endTime=%d", endTime));
        }

        return 0;
    }

    @HLEFunction(nid = 0xEAED89CD, version = 150, checkInsideInterrupt = true, stackUsage = 0x10)
    public int scePsmfGetNumberOfStreams(@CheckArgument("checkPsmf") TPointer32 psmf) {
        PSMFHeader header = getPsmfHeader(psmf);

        return header.getNumberOfStreams();
    }

    @HLEFunction(nid = 0x7491C438, version = 150, checkInsideInterrupt = true, stackUsage = 0x10)
    public int scePsmfGetNumberOfEPentries(@CheckArgument("checkPsmf") TPointer32 psmf) {
        PSMFHeader header = getPsmfHeader(psmf);

        return header.getEPMapEntriesNum();
    }

    @HLEFunction(nid = 0x0BA514E5, version = 150, checkInsideInterrupt = true, stackUsage = 0x20)
    public int scePsmfGetVideoInfo(@CheckArgument("checkPsmf") TPointer32 psmf, TPointer32 videoInfoAddr) {
        PSMFHeader header = getPsmfHeader(psmf);
        if (!header.isValidCurrentStreamNumber()) {
        	if (log.isDebugEnabled()) {
        		log.debug(String.format("scePsmfGetVideoInfo returning 0x%08X(ERROR_PSMF_INVALID_ID)", SceKernelErrors.ERROR_PSMF_INVALID_ID));
        	}
        	return SceKernelErrors.ERROR_PSMF_INVALID_ID;
        }
        videoInfoAddr.setValue(0, header.getVideoWidth());
        videoInfoAddr.setValue(4, header.getVideoHeight());

        return 0;
    }

    @HLEFunction(nid = 0xA83F7113, version = 150, checkInsideInterrupt = true, stackUsage = 0x20)
    public int scePsmfGetAudioInfo(@CheckArgument("checkPsmf") TPointer32 psmf, TPointer32 audioInfoAddr) {
        PSMFHeader header = getPsmfHeader(psmf);
        if (!header.isValidCurrentStreamNumber()) {
        	if (log.isDebugEnabled()) {
        		log.debug(String.format("scePsmfGetAudioInfo returning 0x%08X(ERROR_PSMF_INVALID_ID)", SceKernelErrors.ERROR_PSMF_INVALID_ID));
        	}
        	return SceKernelErrors.ERROR_PSMF_INVALID_ID;
        }
        audioInfoAddr.setValue(0, header.getAudioChannelConfig());
        audioInfoAddr.setValue(4, header.getAudioSampleFrequency());

        return 0;
    }

    @HLEFunction(nid = 0x971A3A90, version = 150, checkInsideInterrupt = true, stackUsage = 0x10)
    public int scePsmfCheckEPmap(@CheckArgument("checkPsmfWithEPMap") TPointer32 psmf) {
    	PSMFHeader header = getPsmfHeader(psmf);
    	if (!header.hasEPMap()) {
    		return SceKernelErrors.ERROR_PSMF_NOT_FOUND;
    	}
        return 0;
    }

    @HLEFunction(nid = 0x4E624A34, version = 150, checkInsideInterrupt = true, stackUsage = 0x10)
    public int scePsmfGetEPWithId(@CheckArgument("checkPsmfWithEPMap") TPointer32 psmf, int id, @BufferInfo(lengthInfo=LengthInfo.fixedLength, length=16, usage=Usage.out) TPointer32 outAddr) {
        PSMFHeader header = getPsmfHeader(psmf);
    	if (!header.hasEPMap()) {
    		return SceKernelErrors.ERROR_PSMF_NOT_FOUND;
    	}
        PSMFEntry entry = header.getEPMapEntry(id);
    	if (entry == null) {
    		if (log.isDebugEnabled()) {
    			log.debug(String.format("scePsmfGetEPWithId returning 0x%08X(ERROR_PSMF_INVALID_ID)", SceKernelErrors.ERROR_PSMF_INVALID_ID));
    		}
    		return SceKernelErrors.ERROR_PSMF_INVALID_ID;
    	}

        if (log.isDebugEnabled()) {
        	log.debug(String.format("scePsmfGetEPWithId returning %s", entry));
        }
        outAddr.setValue(0, entry.getEntryPTS());
        outAddr.setValue(4, entry.getEntryOffset());
        outAddr.setValue(8, entry.getEntryIndex());
        outAddr.setValue(12, entry.getEntryPicOffset());

        return 0;
    }

    @HLEFunction(nid = 0x7C0E7AC3, version = 150, checkInsideInterrupt = true, stackUsage = 0x10)
    public int scePsmfGetEPWithTimestamp(@CheckArgument("checkPsmfWithEPMap") TPointer32 psmf, int ts, TPointer32 entryAddr) {
        PSMFHeader header = getPsmfHeader(psmf);
    	if (ts < header.getPresentationStartTime()) {
    		if (log.isDebugEnabled()) {
    			log.debug(String.format("scePsmfGetEPWithTimestamp returning 0x%08X(ERROR_PSMF_INVALID_TIMESTAMP)", SceKernelErrors.ERROR_PSMF_INVALID_TIMESTAMP));
    		}
            return SceKernelErrors.ERROR_PSMF_INVALID_TIMESTAMP;
    	}

    	PSMFEntry entry = header.getEPMapEntryWithTimestamp(ts);
        if (entry == null) {
        	// Unknown error code
    		if (log.isDebugEnabled()) {
    			log.debug(String.format("scePsmfGetEPWithTimestamp returning -1"));
    		}
        	return -1;
        }

        if (log.isDebugEnabled()) {
        	log.debug(String.format("scePsmfGetEPWithTimestamp returning %s", entry));
        }
        entryAddr.setValue(0, entry.getEntryPTS());
        entryAddr.setValue(4, entry.getEntryOffset());
        entryAddr.setValue(8, entry.getEntryIndex());
        entryAddr.setValue(12, entry.getEntryPicOffset());

        return 0;
    }

    @HLEFunction(nid = 0x5F457515, version = 150, checkInsideInterrupt = true, stackUsage = 0x20)
    public int scePsmfGetEPidWithTimestamp(@CheckArgument("checkPsmfWithEPMap") TPointer32 psmf, int ts) {
        PSMFHeader header = getPsmfHeader(psmf);
    	if (!header.hasEPMap()) {
    		return SceKernelErrors.ERROR_PSMF_NOT_FOUND;
    	}

    	if (ts < header.getPresentationStartTime()) {
    		if (log.isDebugEnabled()) {
    			log.debug(String.format("scePsmfGetEPidWithTimestamp returning 0x%08X(ERROR_PSMF_INVALID_TIMESTAMP)", SceKernelErrors.ERROR_PSMF_INVALID_TIMESTAMP));
    		}
            return SceKernelErrors.ERROR_PSMF_INVALID_TIMESTAMP;
    	}

        PSMFEntry entry = header.getEPMapEntryWithTimestamp(ts);
        if (entry == null) {
        	// Unknown error code
    		if (log.isDebugEnabled()) {
    			log.debug(String.format("scePsmfGetEPidWithTimestamp returning -1"));
    		}
            return -1;
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("scePsmfGetEPidWithTimestamp returning id 0x%X", entry.getId()));
        }

        return entry.getId();
	}

    @HLEFunction(nid = 0x5B70FCC1, version = 150, checkInsideInterrupt = true, stackUsage = 0x20)
    public int scePsmfQueryStreamOffset(TPointer bufferAddr, TPointer32 offsetAddr) {
        PSMFHeader header = new PSMFHeader(bufferAddr.getAddress(), null);
        offsetAddr.setValue(header.mpegOffset);

        return 0;
    }

    @HLEFunction(nid = 0x9553CC91, version = 150, checkInsideInterrupt = true, stackUsage = 0x0)
    public int scePsmfQueryStreamSize(TPointer bufferAddr, TPointer32 sizeAddr) {
        PSMFHeader header = new PSMFHeader(bufferAddr.getAddress(), null);
        sizeAddr.setValue(header.mpegStreamSize);

        return 0;
    }

    @HLEFunction(nid = 0x68D42328, version = 150, checkInsideInterrupt = true, stackUsage = 0xA0)
    public int scePsmfGetNumberOfSpecificStreams(@CheckArgument("checkPsmf") TPointer32 psmf, int streamType) {
        PSMFHeader header = getPsmfHeader(psmf);
        int streamNum = header.getSpecificStreamNum(streamType);

        if (log.isDebugEnabled()) {
            log.debug(String.format("scePsmfGetNumberOfSpecificStreams returning %d", streamNum));
        }

        return streamNum;
    }

    @HLEFunction(nid = 0x0C120E1D, version = 150, checkInsideInterrupt = true)
    public int scePsmfSpecifyStreamWithStreamTypeNumber(@CheckArgument("checkPsmf") TPointer32 psmf, int type, int typeNum) {
        PSMFHeader header = getPsmfHeader(psmf);
        if (!header.setStreamWithTypeNum(type, typeNum)) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("scePsmfSpecifyStreamWithStreamTypeNumber returning 0x%08X(ERROR_PSMF_INVALID_ID)", SceKernelErrors.ERROR_PSMF_INVALID_ID));
            }
        	return SceKernelErrors.ERROR_PSMF_INVALID_ID;
        }

        return 0;
    }

    @HLEFunction(nid = 0x2673646B, version = 150, checkInsideInterrupt = true, stackUsage = 0x100)
    public int scePsmfVerifyPsmf(TPointer bufferAddr) {
        if (log.isTraceEnabled()) {
            log.trace(String.format("scePsmfVerifyPsmf %s", Utilities.getMemoryDump(bufferAddr.getAddress(), MPEG_HEADER_BUFFER_MINIMUM_SIZE)));
        }

        int magic = bufferAddr.getValue32(sceMpeg.PSMF_MAGIC_OFFSET);
        if (magic != sceMpeg.PSMF_MAGIC) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("scePsmfVerifyPsmf returning 0x%08X(ERROR_PSMF_INVALID_PSMF)", SceKernelErrors.ERROR_PSMF_INVALID_PSMF));
            }
        	return SceKernelErrors.ERROR_PSMF_INVALID_PSMF;
        }

        int rawVersion = bufferAddr.getValue32(sceMpeg.PSMF_STREAM_VERSION_OFFSET);
        int version = sceMpeg.getMpegVersion(rawVersion);
        if (version < 0) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("scePsmfVerifyPsmf returning 0x%08X(ERROR_PSMF_INVALID_PSMF)", SceKernelErrors.ERROR_PSMF_INVALID_PSMF));
            }
        	return SceKernelErrors.ERROR_PSMF_INVALID_PSMF;
        }

        return 0;
    }

    @HLEFunction(nid = 0xB78EB9E9, version = 150, checkInsideInterrupt = true, stackUsage = 0x0)
    public int scePsmfGetHeaderSize(@CheckArgument("checkPsmf") TPointer32 psmf, TPointer32 sizeAddr) {
        PSMFHeader header = getPsmfHeader(psmf);
        sizeAddr.setValue(header.getHeaderSize());

        return 0;
    }

    @HLEFunction(nid = 0xA5EBFE81, version = 150, checkInsideInterrupt = true, stackUsage = 0x0)
    public int scePsmfGetStreamSize(@CheckArgument("checkPsmf") TPointer32 psmf, TPointer32 sizeAddr) {
        PSMFHeader header = getPsmfHeader(psmf);
        sizeAddr.setValue(header.getStreamSize());

        return 0;
    }

    @HLEFunction(nid = 0xE1283895, version = 150, checkInsideInterrupt = true, stackUsage = 0x0)
    public int scePsmfGetPsmfVersion(@CheckArgument("checkPsmf") TPointer32 psmf) {
        PSMFHeader header = getPsmfHeader(psmf);

        // Convert the header version into a decimal number, e.g. 0x0015 -> 15
        int headerVersion = header.getVersion();
        int version = 0;
        for (int i = 0; i < 4; i++, headerVersion >>= 8) {
        	int digit = headerVersion & 0x0F;
        	version = (version * 10) + digit;
        }

        if (log.isDebugEnabled()) {
        	log.debug(String.format("scePsmfGetPsmfVersion returning version=%d (headerVersion=0x%04X)", version, headerVersion));
        }

        return version;
    }

    @HLEUnimplemented
    @HLEFunction(nid = 0xDE78E9FC, version = 150)
    public int scePsmfGetNumberOfPsmfMarks(@CheckArgument("checkPsmf") TPointer32 psmf, int unknown) {
    	return 0;
    }

    @HLEUnimplemented
    @HLEFunction(nid = 0x43AC7DBB, version = 150)
    public int scePsmfGetPsmfMark(@CheckArgument("checkPsmf") TPointer32 psmf, int unknown, int markNumber, TPointer markInfoAddr) {
    	int markType = 0;
    	int markTimestamp = 0;
    	int markEntryEsStream = 0;
    	int markData = 0;
    	String markName = "Test";
    	markInfoAddr.setValue32(0, markType);
    	markInfoAddr.setValue32(4, markTimestamp);
    	markInfoAddr.setValue32(8, markEntryEsStream);
    	markInfoAddr.setValue32(12, markData);
    	markInfoAddr.setValue32(16, markName.length());
    	markInfoAddr.setStringNZ(20, markName.length(), markName);

    	return 0;
    }
}
