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

import static jpcsp.HLE.kernel.types.SceKernelErrors.ERROR_MP3_DECODING_ERROR;
import static jpcsp.HLE.kernel.types.SceKernelErrors.ERROR_MP3_ID_NOT_RESERVED;
import static jpcsp.HLE.kernel.types.SceKernelErrors.ERROR_MP3_INVALID_ID;
import static jpcsp.HLE.modules.sceAudiocodec.PSP_CODEC_MP3;
import static jpcsp.util.Utilities.endianSwap32;
import static jpcsp.util.Utilities.readUnaligned16;
import static jpcsp.util.Utilities.readUnaligned32;
import jpcsp.HLE.CanBeNull;
import jpcsp.HLE.CheckArgument;
import jpcsp.HLE.HLEFunction;
import jpcsp.HLE.HLELogging;
import jpcsp.HLE.HLEModule;
import jpcsp.HLE.SceKernelErrorException;
import jpcsp.HLE.TPointer;
import jpcsp.HLE.TPointer32;
import jpcsp.Memory;
import jpcsp.HLE.Modules;
import jpcsp.HLE.kernel.types.SceKernelErrors;
import jpcsp.HLE.kernel.types.pspFileBuffer;
import jpcsp.HLE.modules.sceAudiocodec.AudiocodecInfo;
import jpcsp.media.codec.mp3.Mp3Decoder;
import jpcsp.media.codec.mp3.Mp3Header;
import jpcsp.util.Utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class sceMp3 extends HLEModule {
    public static Logger log = Modules.getLogger("sceMp3");
    private Mp3Info[] ids;
    public static final int ID3 = 0x00334449; // "ID3"
    private static final int TAG_Xing = 0x676E6958; // "Xing"
    private static final int TAG_Info = 0x6F666E49; // "Info"
    private static final int TAG_VBRI = 0x49524256; // "VBRI"
    private static final int infoTagOffsets[][] = {{17, 32}, {9, 17}};
    private static final int mp3DecodeDelay = 4000; // Microseconds
	private static final int maxSamplesBytesStereo = 0x1200;

    @Override
    public void start() {
    	ids = new Mp3Info[2];
    	for (int i = 0; i < ids.length; i++) {
    		ids[i] = new Mp3Info(i);
    	}

        super.start();
    }

    public int checkId(int id) {
        if (ids == null || ids.length == 0) {
            throw new SceKernelErrorException(ERROR_MP3_INVALID_ID);
        }
        if (id < 0 || id >= ids.length) {
            throw new SceKernelErrorException(ERROR_MP3_INVALID_ID);
        }

        return id;
    }

    public int checkInitId(int id) {
        id = checkId(id);
        if (!ids[id].isReserved()) {
            throw new SceKernelErrorException(ERROR_MP3_ID_NOT_RESERVED);
        }

        return id;
    }

    public Mp3Info getMp3Info(int id) {
        return ids[id];
    }

    public static boolean isMp3Magic(int magic) {
    	return (magic & 0xE0FF) == 0xE0FF;
    }

    public static class Mp3Info extends AudiocodecInfo {
        //
        // The Buffer layout is the following:
        // - mp3BufSize: maximum buffer size, cannot be changed
        // - mp3InputBufSize: the number of bytes available for reading
        // - mp3InputFileReadPos: the index of the first byte available for reading
        // - mp3InputBufWritePos: the index of the first byte available for writing
        //                          (i.e. the index of the first byte after the last byte
        //                           available for reading)
        // The buffer is cyclic, i.e. the byte following the last byte is the first byte.
        // The following conditions are always true:
        // - 0 <= mp3InputFileReadPos < mp3BufSize
        // - 0 <= mp3InputBufWritePos < mp3BufSize
        // - mp3InputFileReadPos + mp3InputBufSize == mp3InputBufWritePos
        //   or (for cyclic buffer)
        //   mp3InputFileReadPos + mp3InputBufSize == mp3InputBufWritePos + mp3BufSize
        //
        // For example:
        //   [................R..........W.......]
        //                    |          +-> mp3InputBufWritePos
        //                    +-> mp3InputFileReadPos
        //                    <----------> mp3InputBufSize
        //   <-----------------------------------> mp3BufSize
        //
        //   mp3BufSize = 8192
        //   mp3InputFileReadPos = 4096
        //   mp3InputBufWritePos = 6144
        //   mp3InputBufSize = 2048
        //
        // MP3 Frame Header (4 bytes):
        // - Bits 31 to 21: Frame sync (all 1);
        // - Bits 20 to 19: MPEG Audio version;
        // - Bits 18 and 17: Layer;
        // - Bit 16: Protection bit;
        // - Bits 15 to 12: Bitrate;
        // - Bits 11 and 10: Sample rate;
        // - Bit 9: Padding;
        // - Bit 8: Reserved;
        // - Bits 7 and 6: Channels;
        // - Bits 5 and 4: Channel extension;
        // - Bit 3: Copyrigth;
        // - Bit 2: Original;
        // - Bits 1 and 0: Emphasis.
        //
        // NOTE: sceMp3 is only capable of handling MPEG Version 1 Layer III data.
        //

    	// The PSP is always reserving this size at the beginning of the input buffer
    	private static final int reservedBufferSize = 0x5C0;
    	private static final int minimumInputBufferSize = reservedBufferSize;
        private boolean reserved;
        private pspFileBuffer inputBuffer;
        private int bufferAddr;
        private int outputAddr;
        private int outputSize;
        private int sumDecodedSamples;
        private int halfBufferSize;
        private int outputIndex;
        private int loopNum;
        private int startPos;
        private long endPos;
        private int sampleRate;
        private int bitRate;
        private int maxSamples;
        private int channels;
        private int version;
        private int numberOfFrames;

        public Mp3Info(int id) {
        	super(id);
        }

        public boolean isReserved() {
            return reserved;
        }

        public void reserve(int bufferAddr, int bufferSize, int outputAddr, int outputSize, long startPos, long endPos) {
            reserved = true;
            this.bufferAddr = bufferAddr;
            this.outputAddr = outputAddr;
            this.outputSize = outputSize;
            this.startPos = (int) startPos;
            this.endPos = endPos;
            inputBuffer = new pspFileBuffer(bufferAddr + reservedBufferSize, bufferSize - reservedBufferSize, 0, this.startPos);
            inputBuffer.setFileMaxSize((int) endPos);
            loopNum = -1; // Looping indefinitely by default
            initCodec();

            halfBufferSize = (bufferSize - reservedBufferSize) >> 1;
        }

        @Override
		public void release() {
        	super.release();
            reserved = false;
        }

		public void initCodec() {
        	initCodec(PSP_CODEC_MP3);
        }

        public int notifyAddStream(int bytesToAdd) {
        	bytesToAdd = Math.min(bytesToAdd, getInternalWritableBytes());

            if (log.isTraceEnabled()) {
                log.trace(String.format("notifyAddStream inputBuffer %s: %s", inputBuffer, Utilities.getMemoryDump(inputBuffer.getWriteAddr(), bytesToAdd)));
            }

            inputBuffer.notifyWrite(bytesToAdd);

            return 0;
        }

        public pspFileBuffer getInputBuffer() {
            return inputBuffer;
        }

        public boolean isStreamDataNeeded() {
        	boolean isDataNeeded;
        	if (inputBuffer.isFileEnd()) {
        		isDataNeeded = false;
        	} else {
        		isDataNeeded = getWritableBytes() > 0;
        	}

        	return isDataNeeded;
        }

        public int getSumDecodedSamples() {
            return sumDecodedSamples;
        }

        public int decode(TPointer32 outputBufferAddress) {
        	int result;
        	int decodeOutputAddr = outputAddr + outputIndex;
        	if (inputBuffer.getCurrentSize() <= 0) {
        		int outputBytes = codec.getNumberOfSamples() * 4;
        		Memory mem = Memory.getInstance();
        		mem.memset(decodeOutputAddr, (byte) 0, outputBytes);
        		result = outputBytes;
        	} else {
	        	int decodeInputAddr = inputBuffer.getReadAddr();
	        	int decodeInputLength = inputBuffer.getReadSize();

	        	// Reaching the end of the input buffer (wrapping to its beginning)?
	        	if (decodeInputLength < minimumInputBufferSize && decodeInputLength < inputBuffer.getCurrentSize()) {
	        		// Concatenate the input into a temporary buffer
	        		Memory mem = Memory.getInstance();
	        		mem.memcpy(bufferAddr, decodeInputAddr, decodeInputLength);
	        		int wrapLength = Math.min(inputBuffer.getCurrentSize(), minimumInputBufferSize) - decodeInputLength;
	        		mem.memcpy(bufferAddr + decodeInputLength, inputBuffer.getAddr(), wrapLength);

	        		decodeInputAddr = bufferAddr;
	        		decodeInputLength += wrapLength;
	        	}

	        	if (log.isDebugEnabled()) {
	            	log.debug(String.format("Decoding from 0x%08X, length=0x%X to 0x%08X, inputBuffer %s", decodeInputAddr, decodeInputLength, decodeOutputAddr, inputBuffer));
	            }

	        	result = codec.decode(outputBufferAddress.getMemory(), decodeInputAddr, decodeInputLength, outputBufferAddress.getMemory(), decodeOutputAddr);

	            if (result < 0) {
	            	result = ERROR_MP3_DECODING_ERROR;
	            } else {
		            int readSize = result;
		            int samples = codec.getNumberOfSamples();
		            int outputBytes = samples * outputChannels * 2;

		            inputBuffer.notifyRead(readSize);

		            sumDecodedSamples += samples;

		            // Update index in output buffer for next decode()
		            outputIndex += outputBytes;
		            if (outputIndex + outputBytes > outputSize) {
		            	// No space enough to store the same amount of output bytes,
		            	// reset to beginning of output buffer
		            	outputIndex = 0;
		            }

		            result = outputBytes;
	            }

	            if (inputBuffer.isFileEnd() && loopNum != 0) {
	            	if (inputBuffer.getCurrentSize() < minimumInputBufferSize || (inputBuffer.getFilePosition() - inputBuffer.getCurrentSize()) > endPos) {
		            	if (log.isDebugEnabled()) {
		            		log.debug(String.format("Looping loopNum=%d", loopNum));
		            	}

		            	if (loopNum > 0) {
		            		loopNum--;
		            	}

		            	resetPlayPosition(0);
	            	}
	            }
        	}

            outputBufferAddress.setValue(decodeOutputAddr);

            return result;
        }

        private int getInternalWritableBytes() {
        	return inputBuffer.getNoFileWriteSize();
        }

        public int getWritableBytes() {
        	int writeSize = getInternalWritableBytes();

        	// Never return more than halfBufferSize (tested on PSP using JpcspTrace),
        	// even when 2*halfBufferSize would be free.
        	if (writeSize >= halfBufferSize) {
        		return halfBufferSize;
        	}

        	return 0;
        }

		public int getLoopNum() {
			return loopNum;
		}

		public void setLoopNum(int loopNum) {
			this.loopNum = loopNum;
		}

		public int resetPlayPosition(int position) {
			inputBuffer.reset(0, startPos);
			sumDecodedSamples = 0;

			return 0;
		}

        private void parseMp3FrameHeader() {
            Memory mem = Memory.getInstance();
            int startAddr = inputBuffer.getAddr();
            int headerAddr = startAddr;
        	int header = readUnaligned32(mem, headerAddr);

            // Skip the ID3 tags
        	if ((header & 0x00FFFFFF) == ID3) {
        		int size = endianSwap32(readUnaligned32(mem, startAddr + 6));
        		// Highest bit of each byte has to be ignored (format: 0x7F7F7F7F)
        		size = (size & 0x7F) | ((size & 0x7F00) >> 1) | ((size & 0x7F0000) >> 2) | ((size & 0x7F000000) >> 3);
        		if (log.isDebugEnabled()) {
        			log.debug(String.format("Skipping ID3 of size 0x%X", size));
        		}
        		inputBuffer.notifyRead(10 + size);
        		headerAddr = startAddr + 10 + size;
        		header = readUnaligned32(mem, headerAddr);
        	}

        	if (!isMp3Magic(header)) {
        		log.error(String.format("Invalid MP3 header 0x%08X", header));
        		return;
        	}

        	header = Utilities.endianSwap32(header);
            if (log.isDebugEnabled()) {
            	log.debug(String.format("Mp3 header: 0x%08X", header));
            }

            Mp3Header mp3Header = new Mp3Header();
            Mp3Decoder.decodeHeader(mp3Header, header);
            version = mp3Header.version;
            channels = mp3Header.nbChannels;
            sampleRate = mp3Header.sampleRate;
            bitRate = mp3Header.bitRate;
            maxSamples = mp3Header.maxSamples;

            parseInfoTag(headerAddr + 4 + infoTagOffsets[mp3Header.lsf][mp3Header.nbChannels - 1]);
            parseVbriTag(headerAddr + 4 + 32);
        }

        private void parseInfoTag(int addr) {
        	Memory mem = Memory.getInstance();
        	int tag = readUnaligned32(mem, addr);
        	if (tag == TAG_Xing || tag == TAG_Info) {
        		int numberOfBytes = 0;
        		int flags = endianSwap32(readUnaligned32(mem, addr + 4));
        		addr += 8;
        		if ((flags & 0x1) != 0) {
        			numberOfFrames = endianSwap32(readUnaligned32(mem, addr));
        			addr += 4;
        		}
        		if ((flags & 0x2) != 0) {
        			numberOfBytes = endianSwap32(readUnaligned32(mem, addr));
        			addr += 4;
        		}

        		if (log.isDebugEnabled()) {
            		log.debug(String.format("Found TAG 0x%08X, numberOfFrames=%d, numberOfBytes=0x%X", tag, numberOfFrames, numberOfBytes));
            	}
        	}
        }

        private void parseVbriTag(int addr) {
        	Memory mem = Memory.getInstance();
        	int tag = readUnaligned32(mem, addr);
        	if (tag == TAG_VBRI) {
        		int version = readUnaligned16(mem, addr + 4);
        		if (version == 1) {
        			int numberOfBytes = endianSwap32(readUnaligned32(mem, addr + 10));
        			numberOfFrames = endianSwap32(readUnaligned32(mem, addr + 14));

        			if (log.isDebugEnabled()) {
                		log.debug(String.format("Found TAG 0x%08X, numberOfFrames=%d, numberOfBytes=0x%X", tag, numberOfFrames, numberOfBytes));
                	}
        		}
        	}
        }

        public void init() {
        	parseMp3FrameHeader();

            codec.init(0, channels, outputChannels, 0);

            sumDecodedSamples = 0;
        }

        public int getChannelNum() {
        	return channels;
        }

        public int getSampleRate() {
        	return sampleRate;
        }

        public int getBitRate() {
        	return bitRate;
        }

        public int getMaxSamples() {
        	return maxSamples;
        }

        public int getVersion() {
        	return version;
        }

        public int getNumberOfFrames() {
        	return numberOfFrames;
        }
    }

    public int getFreeMp3Id() {
        int id = -1;
        for (int i = 0; i < ids.length; i++) {
            if (!ids[i].isReserved()) {
                id = i;
                break;
            }
        }
        if (id < 0) {
            return -1;
        }

        return id;
    }

    @HLEFunction(nid = 0x07EC321A, version = 150, checkInsideInterrupt = true)
    public int sceMp3ReserveMp3Handle(@CanBeNull TPointer parameters) {
    	long startPos = 0;
    	long endPos = 0;
    	int bufferAddr = 0;
    	int bufferSize = 0;
    	int outputAddr = 0;
    	int outputSize = 0;
    	if (parameters.isNotNull()) {
	        startPos = parameters.getValue64(0);   // Audio data frame start position.
	        endPos = parameters.getValue64(8);     // Audio data frame end position.
	        bufferAddr = parameters.getValue32(16); // Input AAC data buffer.
	        bufferSize = parameters.getValue32(20); // Input AAC data buffer size.
	        outputAddr = parameters.getValue32(24); // Output PCM data buffer.
	        outputSize = parameters.getValue32(28); // Output PCM data buffer size.

	        if (bufferAddr == 0 || outputAddr == 0) {
	        	return SceKernelErrors.ERROR_MP3_INVALID_ADDRESS;
	        }
	        if (startPos < 0 || startPos > endPos) {
	        	return SceKernelErrors.ERROR_MP3_INVALID_PARAMETER;
	        }
	        if (bufferSize < 8192 || outputSize < maxSamplesBytesStereo * 2) {
	        	return SceKernelErrors.ERROR_MP3_INVALID_PARAMETER;
	        }
    	}

        if (log.isDebugEnabled()) {
            log.debug(String.format("sceMp3ReserveMp3Handle parameters: startPos=0x%X, endPos=0x%X, "
                    + "bufferAddr=0x%08X, bufferSize=0x%X, outputAddr=0x%08X, outputSize=0x%X",
                    startPos, endPos, bufferAddr, bufferSize, outputAddr, outputSize));
        }

        int id = getFreeMp3Id();
        if (id < 0) {
        	return id;
        }

        ids[id].reserve(bufferAddr, bufferSize, outputAddr, outputSize, startPos, endPos);

        return id;
    }

    @HLEFunction(nid = 0x0DB149F4, version = 150, checkInsideInterrupt = true)
    public int sceMp3NotifyAddStreamData(@CheckArgument("checkInitId") int id, int bytesToAdd) {
    	return getMp3Info(id).notifyAddStream(bytesToAdd);
    }

    @HLEFunction(nid = 0x2A368661, version = 150, checkInsideInterrupt = true)
    public int sceMp3ResetPlayPosition(@CheckArgument("checkInitId") int id) {
    	return getMp3Info(id).resetPlayPosition(0);
    }

    @HLELogging(level="info")
    @HLEFunction(nid = 0x35750070, version = 150, checkInsideInterrupt = true)
    public int sceMp3InitResource() {
        return 0;
    }

    @HLELogging(level="info")
    @HLEFunction(nid = 0x3C2FA058, version = 150, checkInsideInterrupt = true)
    public int sceMp3TermResource() {
        return 0;
    }

    @HLEFunction(nid = 0x3CEF484F, version = 150, checkInsideInterrupt = true)
    public int sceMp3SetLoopNum(@CheckArgument("checkInitId") int id, int loopNum) {
    	getMp3Info(id).setLoopNum(loopNum);

        return 0;
    }

    @HLEFunction(nid = 0x44E07129, version = 150, checkInsideInterrupt = true, stackUsage = 0x38)
    public int sceMp3Init(@CheckArgument("checkId") int id) {
    	Mp3Info mp3Info = getMp3Info(id);
    	mp3Info.init();
        if (log.isInfoEnabled()) {
            log.info(String.format("Initializing Mp3 data: channels=%d, samplerate=%dkHz, bitrate=%dkbps.", mp3Info.getChannelNum(), mp3Info.getSampleRate(), mp3Info.getBitRate()));
        }

        return 0;
    }

    @HLEFunction(nid = 0x7F696782, version = 150, checkInsideInterrupt = true)
    public int sceMp3GetMp3ChannelNum(@CheckArgument("checkInitId") int id) {
        return getMp3Info(id).getChannelNum();
    }

    @HLEFunction(nid = 0x8F450998, version = 150, checkInsideInterrupt = true)
    public int sceMp3GetSamplingRate(@CheckArgument("checkInitId") int id) {
    	Mp3Info mp3Info = getMp3Info(id);
    	if (log.isDebugEnabled()) {
    		log.debug(String.format("sceMp3GetSamplingRate returning 0x%X", mp3Info.getSampleRate()));
    	}
        return mp3Info.getSampleRate();
    }

    @HLEFunction(nid = 0xA703FE0F, version = 150, checkInsideInterrupt = true)
    public int sceMp3GetInfoToAddStreamData(@CheckArgument("checkInitId") int id, @CanBeNull TPointer32 writeAddr, @CanBeNull TPointer32 writableBytesAddr, @CanBeNull TPointer32 readOffsetAddr) {
        Mp3Info info = getMp3Info(id);
        writeAddr.setValue(info.getInputBuffer().getWriteAddr());
        writableBytesAddr.setValue(info.getWritableBytes());
        readOffsetAddr.setValue(info.getInputBuffer().getFilePosition());

        if (log.isDebugEnabled()) {
        	log.debug(String.format("sceMp3GetInfoToAddStreamData returning writeAddr=0x%08X, writableBytes=0x%X, readOffset=0x%X", writeAddr.getValue(), writableBytesAddr.getValue(), readOffsetAddr.getValue()));
        }
        return 0;
    }

    @HLEFunction(nid = 0xD021C0FB, version = 150, checkInsideInterrupt = true, stackUsage = 0x28)
    public int sceMp3Decode(@CheckArgument("checkInitId") int id, TPointer32 bufferAddress) {
        int result = getMp3Info(id).decode(bufferAddress);

        if (log.isDebugEnabled()) {
            log.debug(String.format("sceMp3Decode bufferAddress=%s(0x%08X) returning 0x%X", bufferAddress, bufferAddress.getValue(), result));
        }

        if (result >= 0) {
            Modules.ThreadManForUserModule.hleKernelDelayThread(mp3DecodeDelay, false);
        }

        return result;
    }

    @HLEFunction(nid = 0xD0A56296, version = 150, checkInsideInterrupt = true)
    public boolean sceMp3CheckStreamDataNeeded(@CheckArgument("checkInitId") int id) {
        return getMp3Info(id).isStreamDataNeeded();
    }

    @HLEFunction(nid = 0xF5478233, version = 150, checkInsideInterrupt = true, stackUsage = 0x8)
    public int sceMp3ReleaseMp3Handle(@CheckArgument("checkId") int id) {
    	getMp3Info(id).release();

        return 0;
    }

    @HLEFunction(nid = 0x354D27EA, version = 150)
    public int sceMp3GetSumDecodedSample(@CheckArgument("checkInitId") int id) {
        int sumDecodedSamples = getMp3Info(id).getSumDecodedSamples();
        if (log.isDebugEnabled()) {
            log.debug(String.format("sceMp3GetSumDecodedSample returning 0x%X", sumDecodedSamples));
        }

        return sumDecodedSamples;
    }

    @HLEFunction(nid = 0x87677E40, version = 150, checkInsideInterrupt = true)
    public int sceMp3GetBitRate(@CheckArgument("checkInitId") int id) {
        return getMp3Info(id).getBitRate();
    }
    
    @HLEFunction(nid = 0x87C263D1, version = 150, checkInsideInterrupt = true)
    public int sceMp3GetMaxOutputSample(@CheckArgument("checkInitId") int id) {
    	Mp3Info mp3Info = getMp3Info(id);
    	if (log.isDebugEnabled()) {
    		log.debug(String.format("sceMp3GetMaxOutputSample returning 0x%X", mp3Info.getMaxSamples()));
    	}
        return mp3Info.getMaxSamples();
    }

    @HLEFunction(nid = 0xD8F54A51, version = 150, checkInsideInterrupt = true)
    public int sceMp3GetLoopNum(@CheckArgument("checkInitId") int id) {
        return getMp3Info(id).getLoopNum();
    }

    @HLEFunction(nid = 0x3548AEC8, version = 150)
    public int sceMp3GetFrameNum(@CheckArgument("checkInitId") int id) {
    	return getMp3Info(id).getNumberOfFrames();
    }

    @HLEFunction(nid = 0xAE6D2027, version = 150)
    public int sceMp3GetVersion(@CheckArgument("checkInitId") int id) {
    	return getMp3Info(id).getVersion();
    }

    @HLEFunction(nid = 0x0840E808, version = 150, checkInsideInterrupt = true)
    public int sceMp3ResetPlayPosition2(@CheckArgument("checkInitId") int id, int position) {
    	return getMp3Info(id).resetPlayPosition(position);
    }

    @HLEFunction(nid = 0x1B839B83 , version = 620)
    public int sceMp3LowLevelInit(@CheckArgument("checkInitId") int id, int unknown) {
    	Mp3Info mp3Info = getMp3Info(id);
    	// Always output in stereo, even if the input is mono
    	mp3Info.getCodec().init(0, 2, 2, 0);

		return 0;
	}

	@HLEFunction(nid = 0xE3EE2C81, version = 620)
    public int sceMp3LowLevelDecode(@CheckArgument("checkInitId") int id, TPointer sourceAddr, TPointer32 sourceBytesConsumedAddr, TPointer samplesAddr, TPointer32 sampleBytesAddr) {
    	Mp3Info mp3Info = getMp3Info(id);
		int result = mp3Info.getCodec().decode(sourceAddr.getMemory(), sourceAddr.getAddress(), 10000, samplesAddr.getMemory(), samplesAddr.getAddress());
		if (log.isDebugEnabled()) {
			log.debug(String.format("sceMp3LowLevelDecode result=0x%08X, samples=0x%X", result, mp3Info.getCodec().getNumberOfSamples()));
		}
		if (result < 0) {
			return SceKernelErrors.ERROR_MP3_LOW_LEVEL_DECODING_ERROR;
		}

		sourceBytesConsumedAddr.setValue(result);
		sampleBytesAddr.setValue(mp3Info.getCodec().getNumberOfSamples() * 4);

		return 0;
	}
}
