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

import static jpcsp.HLE.Modules.sceMeMemoryModule;
import static jpcsp.HLE.kernel.types.SceKernelErrors.ERROR_SAS_INVALID_ADDRESS;
import static jpcsp.HLE.kernel.types.SceKernelErrors.ERROR_SAS_INVALID_ADSR_CURVE_MODE;
import static jpcsp.util.Utilities.hasBit;

import jpcsp.HLE.BufferInfo;
import jpcsp.HLE.BufferInfo.LengthInfo;
import jpcsp.HLE.BufferInfo.Usage;
import jpcsp.HLE.CanBeNull;
import jpcsp.HLE.CheckArgument;
import jpcsp.HLE.HLEFunction;
import jpcsp.HLE.HLELogging;
import jpcsp.HLE.HLEModule;
import jpcsp.HLE.HLEUnimplemented;
import jpcsp.HLE.SceKernelErrorException;
import jpcsp.HLE.TPointer;
import jpcsp.HLE.TPointer32;
import jpcsp.Emulator;
import jpcsp.Memory;
import jpcsp.HLE.Modules;
import jpcsp.HLE.kernel.managers.SceUidManager;
import jpcsp.HLE.kernel.types.SceKernelErrors;
import jpcsp.HLE.modules.sceAtrac3plus.AtracID;
import jpcsp.memory.IMemoryWriter;
import jpcsp.memory.MemoryWriter;
import jpcsp.sound.SoundVoice;
import jpcsp.sound.SoundMixer;
import jpcsp.sound.SoundVoice.VoiceADSREnvelope;
import jpcsp.util.Utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class sceSasCore extends HLEModule {
    public static Logger log = Modules.getLogger("sceSasCore");

    @Override
    public void start() {
        sasCoreUid = -1;
        voices = new SoundVoice[PSP_SAS_VOICES_MAX];
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new SoundVoice(i);
        }
        mixer = new SoundMixer(voices);
        grainSamples = PSP_SAS_GRAIN_SAMPLES;
        outputMode = PSP_SAS_OUTPUTMODE_STEREO;

        super.start();
    }

    public static final int PSP_SAS_VOICES_MAX = 32;
    public static final int PSP_SAS_GRAIN_SAMPLES = 256;
    public static final int PSP_SAS_VOL_MAX = 0x1000;
    public static final int PSP_SAS_LOOP_MODE_OFF = 0;
    public static final int PSP_SAS_LOOP_MODE_ON = 1;
    public static final int PSP_SAS_PITCH_MIN = 0x1;
    public static final int PSP_SAS_PITCH_BASE = 0x1000;
    public static final int PSP_SAS_PITCH_MAX = 0x4000;
    public static final int PSP_SAS_NOISE_FREQ_MAX = 0x3F;
    public static final int PSP_SAS_ENVELOPE_HEIGHT_MAX = 0x40000000;
    public static final int PSP_SAS_ENVELOPE_FREQ_MAX = 0x7FFFFFFF;
    public static final int PSP_SAS_ADSR_CURVE_MODE_LINEAR_INCREASE = 0;
    public static final int PSP_SAS_ADSR_CURVE_MODE_LINEAR_DECREASE = 1;
    public static final int PSP_SAS_ADSR_CURVE_MODE_LINEAR_BENT = 2;
    public static final int PSP_SAS_ADSR_CURVE_MODE_EXPONENT_DECREASE = 3;
    public static final int PSP_SAS_ADSR_CURVE_MODE_EXPONENT_INCREASE = 4;
    public static final int PSP_SAS_ADSR_CURVE_MODE_DIRECT = 5;
    public static final int PSP_SAS_ADSR_ATTACK = 1;
    public static final int PSP_SAS_ADSR_DECAY = 2;
    public static final int PSP_SAS_ADSR_SUSTAIN = 4;
    public static final int PSP_SAS_ADSR_RELEASE = 8;
    public static final int PSP_SAS_OUTPUTMODE_STEREO = 0;
    public static final int PSP_SAS_OUTPUTMODE_MONO = 1;
    public static final int PSP_SAS_EFFECT_TYPE_OFF = -1;
    public static final int PSP_SAS_EFFECT_TYPE_ROOM = 0;
    public static final int PSP_SAS_EFFECT_TYPE_UNK1 = 1;
    public static final int PSP_SAS_EFFECT_TYPE_UNK2 = 2;
    public static final int PSP_SAS_EFFECT_TYPE_UNK3 = 3;
    public static final int PSP_SAS_EFFECT_TYPE_HALL = 4;
    public static final int PSP_SAS_EFFECT_TYPE_SPACE = 5;
    public static final int PSP_SAS_EFFECT_TYPE_ECHO = 6;
    public static final int PSP_SAS_EFFECT_TYPE_DELAY = 7;
    public static final int PSP_SAS_EFFECT_TYPE_PIPE = 8;
    private static final String[] sasADSRCurveTypeNames = new String[] {
    	"LINEAR_INCREASE",
    	"LINEAR_DECREASE",
    	"LINEAR_BENT",
    	"EXPONENT_REV",
    	"EXPONENT",
    	"DIRECT"
    };
    public static final int CURVE_STATE_OFF = -1;
    public static final int CURVE_STATE_START = -2;
	public static final int CURVE_STATE_ATTACK  = 0;
	public static final int CURVE_STATE_DECAY   = 1;
	public static final int CURVE_STATE_SUSTAIN = 2;
	public static final int CURVE_STATE_RELEASE = 3;
	public static final int CURVE_STATE_ENDED = 4;
    private static final int SASCORE_ATRAC3_CONTEXT_OFFSET = 20;
	private static final int SASCORE_VOICE_SIZE = 56;
	private static final int SASCORE_ME_OFFSET = 1812;
	private static final int SASCORE_ME_PARAMS_OFFSET = 0x700;
	private static final int SASCORE_ME_VOICE_TYPE_NONE = 0;
	private static final int SASCORE_ME_VOICE_TYPE_VAG = 1;
	private static final int SASCORE_ME_VOICE_TYPE_NOISE = 2;
	private static final int SASCORE_ME_VOICE_TYPE_PCM = 5;
	private static final int SASCORE_ME_VOICE_TYPE_ATRAC3 = 6;

    protected int sasCoreUid;
    protected SoundVoice[] voices;
    protected SoundMixer mixer;
    protected int grainSamples;
    protected int outputMode;
    protected static final int waveformBufMaxSize = 1024;  // 256 sound samples.
    protected int waveformEffectType;
    protected int waveformEffectLeftVol;
    protected int waveformEffectRightVol;
    protected int waveformEffectDelay;
    protected int waveformEffectFeedback;
    protected boolean waveformEffectIsDryOn;
    protected boolean waveformEffectIsWetOn;
    protected static final int sasCoreDelay = 5000; // Average microseconds, based on PSP tests.
    protected static final String sasCodeUidPurpose = "sceSasCore-SasCore";

    public static String getSasADSRCurveTypeName(int curveType) {
    	if (curveType < 0 || curveType >= sasADSRCurveTypeNames.length) {
    		return String.format("UNKNOWN_%d", curveType);
    	}

    	return sasADSRCurveTypeNames[curveType];
    }

    protected void checkSasAddressGood(int sasCore) {
    	if (!Memory.isAddressGood(sasCore)) {
            log.warn(String.format("%s bad sasCore Address 0x%08X", getCallingFunctionName(3), sasCore));
    		throw(new SceKernelErrorException(ERROR_SAS_INVALID_ADDRESS));
    	}

    	if (!Memory.isAddressAlignedTo(sasCore, 64)) {
            log.warn(String.format("%s bad sasCore Address 0x%08X (not aligned to 64)", getCallingFunctionName(3), sasCore));
    		throw(new SceKernelErrorException(ERROR_SAS_INVALID_ADDRESS));
    	}
    }

    protected void checkSasHandleGood(int sasCore) {
    	checkSasAddressGood(sasCore);

    	// Accept a header value set by this HLE module or set by the flash0:/kd/sc_sascore.prx module
    	int checkValue = getMemory().read32(sasCore);
        if (checkValue != sasCoreUid && !sceMeMemoryModule.isAllocated(checkValue - PSP_SAS_VOICES_MAX * 76)) {
            log.error(String.format("%s bad sasCoreUid 0x%08X (should be 0x%08X)", getCallingFunctionName(3), checkValue, sasCoreUid));
            throw(new SceKernelErrorException(SceKernelErrors.ERROR_SAS_NOT_INIT));
        }
    }

    protected void checkVoiceNumberGood(int voice) {
        if (voice < 0 || voice >= voices.length) {
            log.warn(String.format("%s bad voice number %d", getCallingFunctionName(3), voice));
    		throw(new SceKernelErrorException(SceKernelErrors.ERROR_SAS_INVALID_VOICE_INDEX));
        }
    }

    protected void checkSasAndVoiceHandlesGood(int sasCore, int voice) {
    	checkSasHandleGood(sasCore);
    	checkVoiceNumberGood(voice);
    }

    protected void checkADSRmode(int curveIndex, int flag, int curveType) {
        int[] validCurveTypes = new int[] {
    		(1 << PSP_SAS_ADSR_CURVE_MODE_LINEAR_INCREASE) | (1 << PSP_SAS_ADSR_CURVE_MODE_LINEAR_BENT) | (1 << PSP_SAS_ADSR_CURVE_MODE_EXPONENT_INCREASE),
        	(1 << PSP_SAS_ADSR_CURVE_MODE_LINEAR_DECREASE) | (1 << PSP_SAS_ADSR_CURVE_MODE_EXPONENT_DECREASE) | (1 << PSP_SAS_ADSR_CURVE_MODE_DIRECT),
        	(1 << PSP_SAS_ADSR_CURVE_MODE_LINEAR_INCREASE) | (1 << PSP_SAS_ADSR_CURVE_MODE_LINEAR_DECREASE) | (1 << PSP_SAS_ADSR_CURVE_MODE_LINEAR_BENT) | (1 << PSP_SAS_ADSR_CURVE_MODE_EXPONENT_DECREASE) | (1 << PSP_SAS_ADSR_CURVE_MODE_EXPONENT_INCREASE) | (1 << PSP_SAS_ADSR_CURVE_MODE_DIRECT),
        	(1 << PSP_SAS_ADSR_CURVE_MODE_LINEAR_DECREASE) | (1 << PSP_SAS_ADSR_CURVE_MODE_EXPONENT_DECREASE) | (1 << PSP_SAS_ADSR_CURVE_MODE_DIRECT)
        };

        if ((flag & (1 << curveIndex)) != 0) {
        	if ((validCurveTypes[curveIndex] & (1 << curveType)) == 0) {
        		throw new SceKernelErrorException(SceKernelErrors.ERROR_SAS_INVALID_ADSR_CURVE_MODE);
        	}
        }
    }

    protected void checkVoiceNotPaused(int voice, boolean requiredOnState) {
        if (voices[voice].isPaused() || voices[voice].isOn() != requiredOnState) {
        	if (log.isDebugEnabled()) {
        		log.debug(String.format("checkVoiceNotPaused returning 0x%08X(ERROR_SAS_VOICE_PAUSED)", SceKernelErrors.ERROR_SAS_VOICE_PAUSED));
        	}
        	throw new SceKernelErrorException(SceKernelErrors.ERROR_SAS_VOICE_PAUSED);
        }
    }

    public int checkVolume(int volume) {
    	if (volume < -PSP_SAS_VOL_MAX || volume > PSP_SAS_VOL_MAX) {
    		throw new SceKernelErrorException(SceKernelErrors.ERROR_SAS_INVALID_VOLUME_VAL);
    	}

    	return volume;
    }

    private void delayThread(long startMicros, int delayMicros, int minimumDelayMicros) {
    	long now = Emulator.getClock().microTime();
    	int threadDelayMicros = delayMicros - (int) (now - startMicros);
    	threadDelayMicros = Math.max(threadDelayMicros, minimumDelayMicros);
    	if (threadDelayMicros > 0) {
    		Modules.ThreadManForUserModule.hleKernelDelayThread(threadDelayMicros, false);
    	} else {
    		Modules.ThreadManForUserModule.hleRescheduleCurrentThread();
    	}
    }

    private void delayThreadSasCore(long startMicros) {
    	// Based on PSP timings: the delay for __sceSasCore is always about
    	// 600 microseconds, independently of the number of samples generated
    	// and of the number of voices currently playing.
    	int delayMicros = 600;
    	delayThread(startMicros, delayMicros, delayMicros);
    }

    public int getOutputMode() {
    	return outputMode;
    }

	protected void setSasCoreAtrac3Context(int sasCore, int voice, int atrac3Context) {
		Memory mem = Memory.getInstance();
        mem.write32(sasCore + SASCORE_VOICE_SIZE * voice + SASCORE_ATRAC3_CONTEXT_OFFSET, atrac3Context);
	}

	protected int getSasCoreAtrac3Context(int sasCore, int voice) {
		Memory mem = Memory.getInstance();
		return mem.read32(sasCore + SASCORE_VOICE_SIZE * voice + SASCORE_ATRAC3_CONTEXT_OFFSET);
	}

	/**
	 * On the PSP, the sascore voice processing is performed by the Media Engine (ME) processor.
	 * The ME is accepting commands sent using sceMeCore_driver_FA398D71().
	 * There are 2 commands related to sascore:
	 * - ME_CMD_SASCORE: called when executing __sceSasCore()
	 * - ME_CMD_SASCORE_WITH_MIX: called when executing __sceSasCoreWithMix()
	 * Both commands are receiving a copy of the sascore voice settings in memory and are
	 * copying back any response from the ME to the internal sascore structures.
	 * 
	 * This function is copying the voice values sent to ME to our HLE sascore implementation.
	 * 
	 * @param sasCore
	 */
	public void copySasCoreToME(int sasCore) {
        grainSamples = getMemory().read8(sasCore + 8) << 5;

		TPointer base = new TPointer(getMemory(), sasCore + SASCORE_ME_OFFSET);
		int revVON = base.getUnsignedValue8(SASCORE_ME_PARAMS_OFFSET + 4);
        waveformEffectIsDryOn = hasBit(revVON, 0);
        waveformEffectIsWetOn = hasBit(revVON, 1);
        waveformEffectLeftVol = base.getUnsignedValue16(SASCORE_ME_PARAMS_OFFSET + 6);
        waveformEffectRightVol = base.getUnsignedValue16(SASCORE_ME_PARAMS_OFFSET + 8);

		for (int i = 0; i < voices.length; i++) {
			int voiceType = base.getUnsignedValue8(8) & 0x0F;
			if (voiceType != SASCORE_ME_VOICE_TYPE_NONE) {
				SoundVoice voice = voices[i];
				int voicePaused = (base.getUnsignedValue8(8) >> 4) & 0x3;
		        voice.setPaused(voicePaused != 0);
		        int curveState = base.getValue8(51);
		        voice.getEnvelope().curveState = curveState;
		        if (curveState != CURVE_STATE_OFF) {
		        	if (curveState == CURVE_STATE_START) {
						switch (voiceType) {
							case SASCORE_ME_VOICE_TYPE_VAG:
								voice.setVAG(base.getValue32(0), base.getValue32(4) - base.getValue32(0));
								voice.setLoopMode(base.getUnsignedValue8(9));
								break;
							case SASCORE_ME_VOICE_TYPE_NOISE:
								voice.setNoise(base.getValue32(4));
								break;
							case SASCORE_ME_VOICE_TYPE_PCM:
								voice.setPCM(base.getValue32(0), base.getUnsignedValue16(4) + 1);
								voice.setLoopMode(base.getUnsignedValue16(6));
								break;
							case SASCORE_ME_VOICE_TYPE_ATRAC3:
								setSasCoreAtrac3Context(sasCore, i, base.getValue32(0));
								break;
							default:
								log.error(String.format("copySasCoreToME voice#%d: unknown voice type %d", i, voiceType));
								break;
						}
			        	voice.on();
		        	}

					voice.setPitch(base.getUnsignedValue16(10));
			        voice.setLeftVolume(base.getUnsignedValue16(12) << 3);
			        voice.setEffectLeftVolume(base.getUnsignedValue16(14) << 3);
			        voice.setRightVolume(base.getUnsignedValue16(16) << 3);
			        voice.setEffectRightVolume(base.getUnsignedValue16(18) << 3);
			        VoiceADSREnvelope envelope = voices[i].getEnvelope();
			        envelope.AttackRate = base.getValue32(24);
			        envelope.DecayRate = base.getValue32(28);
			        envelope.SustainRate = base.getValue32(32);
			        envelope.ReleaseRate = base.getValue32(36);
			        envelope.SustainLevel = base.getValue32(40);
			        envelope.AttackCurveType = base.getUnsignedValue8(44);
			        envelope.DecayCurveType = base.getUnsignedValue8(45);
			        envelope.SustainCurveType = base.getUnsignedValue8(46);
			        envelope.ReleaseCurveType = base.getUnsignedValue8(47);
					if (log.isTraceEnabled()) {
						log.trace(String.format("copySasCoreToME voice#%d: type=%d, paused=%d, curveState=%d: %s", i, voiceType, voicePaused, curveState, Utilities.getMemoryDump(base, SASCORE_VOICE_SIZE)));
					}
		        }
			}

	        base.add(SASCORE_VOICE_SIZE);
		}
	}

	/**
	 * On the PSP, the sascore voice processing is performed by the Media Engine (ME) processor.
	 * The ME is accepting commands sent using sceMeCore_driver_FA398D71().
	 * There are 2 commands related to sascore:
	 * - ME_CMD_SASCORE: called when executing __sceSasCore()
	 * - ME_CMD_SASCORE_WITH_MIX: called when executing __sceSasCoreWithMix()
	 * Both commands are receiving a copy of the sascore voice settings in memory and are
	 * copying back any response from the ME to the internal sascore structures.
	 * 
	 * This function is setting the values which would be returned by the ME.
	 * 
	 * @param sasCore
	 */
	public void copyMEToSasCore(int sasCore) {
		TPointer base = new TPointer(getMemory(), sasCore + SASCORE_ME_OFFSET);

		// The ME is returning the "EndFlag" 32-bit value
		int endFlag = __sceSasGetEndFlag(sasCore);
		base.setValue32(SASCORE_ME_PARAMS_OFFSET + 0, endFlag);
		if (log.isTraceEnabled()) {
			log.trace(String.format("copyMEToSasCore endFlag=0x%08X", endFlag));
		}

		// For each voice, the ME is updating the curveState and height fields
		for (int i = 0; i < voices.length; i++) {
			int voiceType = base.getUnsignedValue8(8) & 0x0F;
			if (voiceType != SASCORE_ME_VOICE_TYPE_NONE) {
				SoundVoice voice = voices[i];
				int height = voice.getEnvelope().height;
				int curveState = voice.isOn() ? voice.getEnvelope().curveState : CURVE_STATE_OFF;
				base.setValue32(52, height);
				base.setUnsignedValue8(51, curveState);

				if (log.isTraceEnabled()) {
					log.trace(String.format("copyMEToSasCore voice#%d: height=0x%08X, curveState=%d", i, height, curveState));
				}
			}

	        base.add(SASCORE_VOICE_SIZE);
		}
	}

	/**
     * Set the ADSR rates for a specific voice.
     *
     * @param sasCore     sasCore handle
     * @param voice       voice number, [0..31]
     * @param flag        Bitfield to indicate which of the following 4 parameters
     *                    has to be updated. Logical OR of the following values:
     *                        0x1: update attack rate
     *                        0x2: update decay rate
     *                        0x4: update sustain rate
     *                        0x8: update release rate
     * @param attack      Envelope's attack rate, [0..0x7FFFFFFF].
     * @param decay       Envelope's decay rate, [0..0x7FFFFFFF].
     * @param sustain     Envelope's sustain rate, [0..0x7FFFFFFF].
     * @param release     Envelope's release rate, [0..0x7FFFFFFF].
     * @return            0 if OK
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                    ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     */
    @HLEFunction(nid = 0x019B25EB, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetADSR(int sasCore, int voice, int flag, int attack, int decay, int sustain, int release) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        VoiceADSREnvelope envelope = voices[voice].getEnvelope();
        if ((flag & 0x1) != 0) envelope.AttackRate = attack;
        if ((flag & 0x2) != 0) envelope.DecayRate = decay;
        if ((flag & 0x4) != 0) envelope.SustainRate = sustain;
        if ((flag & 0x8) != 0) envelope.ReleaseRate = release;

        if (log.isDebugEnabled()) {
            log.debug(String.format("__sceSasSetADSR voice=0x%X: %s", voice, envelope.toString()));
        }

        return 0;
    }

    /**
     * Set the wave form effect delay and feedback parameters (unknown parameters).
     *
     * @param sasCore     sasCore handle
     * @param delay       (unknown) wave form effect delay
     * @param feedback    (unknown) wave form effect feedback
     * @return            0 if OK
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     * @return
     */
    @HLEFunction(nid = 0x267A6DD2, version = 150, checkInsideInterrupt = true)
    public int __sceSasRevParam(int sasCore, int delay, int feedback) {
        checkSasHandleGood(sasCore);

        waveformEffectDelay = delay;
        waveformEffectFeedback = feedback;
        
        return 0;
    }

    /**
     * Get the pause flag for all the voices.
     *
     * @param sasCore     sasCore handle
     * @return            bitfield with bit 0 for voice 0, bit 1 for voice 1...
     *                       bit=0, corresponding voice is not paused
     *                       bit=1, corresponding voice is paused
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0x2C8E6AB3, version = 150, checkInsideInterrupt = true)
    public int __sceSasGetPauseFlag(int sasCore) {
        checkSasHandleGood(sasCore);

        int pauseFlag = 0;
        for (int i = 0; i < voices.length; i++) {
            if (voices[i].isPaused()) {
                pauseFlag |= (1 << i);
            }
        }

        return pauseFlag;
    }

    /**
     * Set the wave form effect type (unknown parameter).
     *
     * @param sasCore     sasCore handle
     * @param type        unknown parameter
     * @return            wave form effect type
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0x33D4AB37, version = 150, checkInsideInterrupt = true)
    public int __sceSasRevType(int sasCore, int type) {
        checkSasHandleGood(sasCore);

        waveformEffectType = type;
        
        return 0;
    }
    
    /**
     * Initialize a new sasCore handle.
     *
     * @param sasCore     sasCore handle, must be a valid address
     *                    (Uid will be written at this address).
     * @param grain       number of samples processed by one call to __sceSasCore
     * @param maxVoices   number of voices (maximum 32)
     * @param outputMode  (unknown) 0 stereo
     *                              1 multichannel
     * @param sampleRate  the default sample rate (number of samples per second)
     *                    for all the voices
     * @return            0
     */
    @HLEFunction(nid = 0x42778A9F, version = 150)
    public int __sceSasInit(@CanBeNull TPointer sasCore, int grain, int maxVoices, int outputMode, int sampleRate) {
        checkSasAddressGood(sasCore.getAddress());

        if (grain < 0x40 || grain > 0x800 || (grain & 0x1F) != 0) {
        	return SceKernelErrors.ERROR_SAS_INVALID_GRAIN;
        }
        if (sampleRate != 44100) {
        	return SceKernelErrors.ERROR_SAS_INVALID_SAMPLE_RATE;
        }
        if (maxVoices <= 0 || maxVoices > PSP_SAS_VOICES_MAX) {
        	return SceKernelErrors.ERROR_SAS_INVALID_MAX_VOICES;
        }
        if (outputMode != PSP_SAS_OUTPUTMODE_STEREO && outputMode != PSP_SAS_OUTPUTMODE_MONO) {
        	return SceKernelErrors.ERROR_SAS_INVALID_OUTPUT_MODE;
        }

        if (sasCoreUid != -1) {
    		// Only one Sas core can be active at a time.
    		// If a previous Uid was allocated, release it.
    		SceUidManager.releaseUid(sasCoreUid, sasCodeUidPurpose);
    	}

        // Size of SasCore structure is 0xE20 bytes
        sasCore.clear(0xE20);

        sasCoreUid = SceUidManager.getNewUid(sasCodeUidPurpose);
    	sasCore.setValue32(0, sasCoreUid);

        grainSamples = grain;
        this.outputMode = outputMode;
        for (int i = 0; i < voices.length; i++) {
            voices[i].setSampleRate(sampleRate); // Set default sample rate
        }

        return 0;
    }

    /**
     * Set the volume for one voice.
     *
     * @param sasCore           sasCore handle
     * @param voice             voice number
     * @param leftVolume        Left channel volume, [0..0x1000].
     * @param rightVolume       Right channel volume, [0..0x1000].
     * @param effectLeftVolume  (unknown) Left effect channel volume, [0..0x1000].
     * @param effectRightVolume (unknown) Right effect channel volume, [0..0x1000].
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                          ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     */
    @HLEFunction(nid = 0x440CA7D8, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetVolume(int sasCore, int voice, @CheckArgument("checkVolume") int leftVolume, @CheckArgument("checkVolume") int rightVolume, @CheckArgument("checkVolume") int effectLeftVolumne, @CheckArgument("checkVolume") int effectRightVolume) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        voices[voice].setLeftVolume(leftVolume << 3);	// 0 - 0x8000
        voices[voice].setRightVolume(rightVolume << 3);	// 0 - 0x8000
        voices[voice].setEffectLeftVolume(effectLeftVolumne << 3);
        voices[voice].setEffectRightVolume(effectRightVolume << 3);

        return 0;
    }

    /**
     * Process the voices and generate the next samples.
     * Mix the resulting samples in an exiting buffer.
     *
     * @param sasCore           sasCore handle
     * @param sasInOut          address for the input and output buffer.
     *                          Samples are stored as 2 16-bit values
     *                          (left then right channel samples)
     * @param leftVolume        Left channel volume, [0..0x1000].
     * @param rightVolume       Right channel volume, [0..0x1000].
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0x50A14DFC, version = 150, checkInsideInterrupt = true)
    public int __sceSasCoreWithMix(int sasCore, int sasInOut, int leftVolume, int rightVolume) {
        checkSasHandleGood(sasCore);

    	long startTime = Emulator.getClock().microTime();
        mixer.synthesizeWithMix(sasInOut, grainSamples, leftVolume << 3, rightVolume << 3);
        delayThreadSasCore(startTime);

        return 0;
    }

    /**
     * Set the sustain level for one voice.
     *
     * @param sasCore           sasCore handle
     * @param voice             voice number [0..31]
     * @param level             sustain level [0..0x40000000]
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                          ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     */
    @HLEFunction(nid = 0x5F9529F6, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetSL(int sasCore, int voice, int level) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        voices[voice].getEnvelope().SustainLevel = level;

        return 0;
    }

    /**
     * Get the end flag for all the voices.
     *
     * @param sasCore     sasCore handle
     * @return            bitfield with bit 0 for voice 0, bit 1 for voice 1...
     *                       bit=0, corresponding voice is not ended
     *                       bit=1, corresponding voice is ended
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0x68A46B95, version = 150)
    public int __sceSasGetEndFlag(int sasCore) {
        checkSasHandleGood(sasCore);

        int endFlag = 0;
        for (int i = 0; i < voices.length; i++) {
            if (voices[i].isEnded()) {
                endFlag |= (1 << i);
            }
        }

        return endFlag;
    }

    /**
     * Get the current envelope height for one voice.
     *
     * @param sasCore     sasCore handle
     * @param voice       voice number [0..31]
     * @return            envelope height [0..0x40000000]
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                    ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     */
    @HLEFunction(nid = 0x74AE582A, version = 150, checkInsideInterrupt = true)
    public int __sceSasGetEnvelopeHeight(int sasCore, int voice) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        return voices[voice].getEnvelope().height;
    }

    /**
     * Set one voice on.
     *
     * @param sasCore           sasCore handle
     * @param voice             voice number [0..31]
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                          ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     *                          ERROR_SAS_VOICE_PAUSED if the voice was paused or already on
     */
    @HLEFunction(nid = 0x76F01ACA, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetKeyOn(int sasCore, int voice) {
        checkSasAndVoiceHandlesGood(sasCore, voice);
        checkVoiceNotPaused(voice, false);

        voices[voice].on();
    	
        return 0;
    }

    /**
     * Set or reset the pause parameter for the voices.
     *
     * @param sasCore           sasCore handle
     * @param voice_bit         a bitfield with bit 0 for voice 0, bit 1 for voice 1...
     *                          Only the bits with 1 are processed.
     * @param setPause          when 0: reset the pause flag for all the voices
     *                                  having a bit 1 in the voice_bit field
     *                          when non-0: set the pause flag for all the voices
     *                                  having a bit 1 in the voice_bit field
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0x787D04D5, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetPause(int sasCore, int voice_bit, boolean setPause) {
        checkSasHandleGood(sasCore);

    	// Update only the pause flag of the voices
    	// where the corresponding bit is set:
    	// set or reset the pause flag according to the "setPause" parameter.
        for (int i = 0; voice_bit != 0; i++, voice_bit >>>= 1) {
            if ((voice_bit & 1) != 0) {
            	voices[i].setPaused(setPause);
            }
        }
        
        return 0;
    }

    /**
     * Set the VAG waveform data for one voice.
     *
     * @param sasCore           sasCore handle
     * @param voice             voice number [0..31]
     * @param vagAddr           address of the VAG waveform data
     * @param size              size in bytes of the VAG waveform data
     * @param loopmode          0 ignore the VAG looping information
     *                          1 process the VAG looping information
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                          ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     *                          ERROR_SAS_INVALID_PARAMETER if an invalid size is provided
     */
    @HLEFunction(nid = 0x99944089, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetVoice(int sasCore, int voice, int vagAddr, int size, int loopmode) {
        if (size <= 0 || (size & 0xF) != 0) {
        	log.warn(String.format("__sceSasSetVoice invalid size 0x%08X", size));
        	return SceKernelErrors.ERROR_SAS_INVALID_ADPCM_SIZE;
        }

        checkSasAndVoiceHandlesGood(sasCore, voice);

    	voices[voice].setVAG(vagAddr, size);
        voices[voice].setLoopMode(loopmode);

        return 0;
    }

    /**
     * Set the ADSR curve types for a specific voice.
     *
     * @param sasCore     sasCore handle
     * @param voice       voice number, [0..31]
     * @param flag        Bitfield to indicate which of the following 4 parameters
     *                    has to be updated. Logical OR of the following values:
     *                        0x1: update attack curve type
     *                        0x2: update decay curve type
     *                        0x4: update sustain curve type
     *                        0x8: update release curve type
     * @param attack      Envelope's attack curve type, [0..5].
     * @param decay       Envelope's decay curve type, [0..5].
     * @param sustain     Envelope's sustain curve type, [0..5].
     * @param release     Envelope's release curve type, [0..5].
     * @return            0 if OK
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                    ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     *                    ERROR_SAS_INVALID_ADSR_CURVE_MODE if an invalid curve mode or curve mode combination is provided
     */
    @HLEFunction(nid = 0x9EC3676A, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetADSRmode(int sasCore, int voice, int flag, int attackType, int decayType, int sustainType, int releaseType) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        checkADSRmode(0, flag, attackType);
        checkADSRmode(1, flag, decayType);
        checkADSRmode(2, flag, sustainType);
        checkADSRmode(3, flag, releaseType);

        VoiceADSREnvelope envelope = voices[voice].getEnvelope();
        if ((flag & 0x1) != 0) envelope.AttackCurveType = attackType;
        if ((flag & 0x2) != 0) envelope.DecayCurveType = decayType;
        if ((flag & 0x4) != 0) envelope.SustainCurveType = sustainType;
        if ((flag & 0x8) != 0) envelope.ReleaseCurveType = releaseType;

        if (log.isDebugEnabled()) {
            log.debug(String.format("__sceSasSetADSRmode voice=0x%X: %s", voice, envelope.toString()));
        }

        return 0;
    }

    /**
     * Set one voice off.
     *
     * @param sasCore           sasCore handle
     * @param voice             voice number [0..31]
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                          ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     *                          ERROR_SAS_VOICE_PAUSED if the voice was paused or already off
     */
    @HLEFunction(nid = 0xA0CF2FA4, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetKeyOff(int sasCore, int voice) {
        checkSasAndVoiceHandlesGood(sasCore, voice);
        checkVoiceNotPaused(voice, true);

    	voices[voice].off();

    	return 0;
    }

    /**
     * (Unknown) Set a triangular waveform for one voice.
     *
     * @param sasCore           sasCore handle
     * @param voice             voice number [0..31]
     * @param unknown           unknown parameter
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                          ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     */
    @HLEUnimplemented
    @HLEFunction(nid = 0xA232CBE6, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetTriangularWave(int sasCore, int voice, int unknown) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        return 0;
    }

    /**
     * Process the voices and generate the next samples.
     *
     * @param sasCore           sasCore handle
     * @param sasOut            address for the output buffer.
     *                          Samples are stored as 2 16-bit values
     *                          (left then right channel samples)
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0xA3589D81, version = 150, checkInsideInterrupt = true)
    public int __sceSasCore(int sasCore, int sasOut) {
        checkSasHandleGood(sasCore);

    	long startTime = Emulator.getClock().microTime();
        mixer.synthesize(sasOut, grainSamples);
        delayThreadSasCore(startTime);

        return 0;
    }

    /**
     * Set the pitch of one voice.
     *
     * @param sasCore           sasCore handle
     * @param voice             voice number [0..31]
     * @param pitch             the pitch value, [1..0x4000]
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                          ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     */
    @HLEFunction(nid = 0xAD84D37F, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetPitch(int sasCore, int voice, int pitch) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        voices[voice].setPitch(pitch);

        return 0;
    }

    /**
     * (Unknown) Set a noise waveform for one voice.
     *
     * @param sasCore           sasCore handle
     * @param voice             voice number [0..31]
     * @param freq              unknown parameter
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                          ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     */
    @HLEFunction(nid = 0xB7660A23, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetNoise(int sasCore, int voice, int freq) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        voices[voice].setNoise(freq);

        return 0;
    }

    /**
     * Get the number of samples generated by one __sceSasCore call.
     *
     * @param sasCore     sasCore handle
     * @return            0 if OK
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     * @return
     */
    @HLEFunction(nid = 0xBD11B7C2, version = 150, checkInsideInterrupt = true)
    public int __sceSasGetGrain(int sasCore) {
        checkSasHandleGood(sasCore);
        
        return grainSamples;
    }

    private int getSimpleSustainLevel(int bitfield1) {
    	return ((bitfield1 & 0x000F) + 1) << 26;
    }

    private int getSimpleDecayRate(int bitfield1) {
    	int bitShift = (bitfield1 >> 4) & 0x000F;
    	if (bitShift == 0) {
    		return PSP_SAS_ENVELOPE_FREQ_MAX;
    	}
    	return 0x80000000 >>> bitShift;
    }

    private int getSimpleRate(int n) {
    	n &= 0x7F;
    	if (n == 0x7F) {
    		return 0;
    	}
    	int rate = ((7 - (n & 0x3)) << 26) >>> (n >> 2);
    	if (rate == 0) {
    		return 1;
    	}
    	return rate;
    }

    private int getSimpleExponentRate(int n) {
    	n &= 0x7F;
    	if (n == 0x7F) {
    		return 0;
    	}
    	int rate = ((7 - (n & 0x3)) << 24) >>> (n >> 2);
    	if (rate == 0) {
    		return 1;
    	}
    	return rate;
    }

    private int getSimpleAttackRate(int bitfield1) {
    	return getSimpleRate(bitfield1 >> 8);
    }

    private int getSimpleAttackCurveType(int bitfield1) {
    	return (bitfield1 & 0x8000) == 0 ? PSP_SAS_ADSR_CURVE_MODE_LINEAR_INCREASE : PSP_SAS_ADSR_CURVE_MODE_LINEAR_BENT;
    }

    private int getSimpleReleaseRate(int bitfield2) {
    	int n = bitfield2 & 0x001F;
    	if (n == 31) {
    		return 0;
    	}
    	if (getSimpleReleaseCurveType(bitfield2) == PSP_SAS_ADSR_CURVE_MODE_LINEAR_DECREASE) {
    		if (n == 30) {
    			return 0x40000000;
    		} else if (n == 29) {
    			return 1;
    		}
    		return 0x10000000 >> n;
    	}
    	if (n == 0) {
    		return PSP_SAS_ENVELOPE_FREQ_MAX;
    	}
    	return 0x80000000 >>> n;
    }

    private int getSimpleReleaseCurveType(int bitfield2) {
    	return (bitfield2 & 0x0020) == 0 ? PSP_SAS_ADSR_CURVE_MODE_LINEAR_DECREASE : PSP_SAS_ADSR_CURVE_MODE_EXPONENT_DECREASE;
    }

    private int getSimpleSustainRate(int bitfield2) {
    	if (getSimpleSustainCurveType(bitfield2) == PSP_SAS_ADSR_CURVE_MODE_EXPONENT_DECREASE) {
    		return getSimpleExponentRate(bitfield2 >> 6);
    	}
    	return getSimpleRate(bitfield2 >> 6);
    }

    private int getSimpleSustainCurveType(int bitfield2) {
    	switch (bitfield2 >> 13) {
    		case 0: return PSP_SAS_ADSR_CURVE_MODE_LINEAR_INCREASE;
    		case 2: return PSP_SAS_ADSR_CURVE_MODE_LINEAR_DECREASE;
    		case 4: return PSP_SAS_ADSR_CURVE_MODE_LINEAR_BENT;
    		case 6: return PSP_SAS_ADSR_CURVE_MODE_EXPONENT_DECREASE;
    	}

    	throw new SceKernelErrorException(ERROR_SAS_INVALID_ADSR_CURVE_MODE);
    }

    /**
     * Set the ADSR parameters for a specific voice with simplified parameters.
     * The Decay curve type is always exponential decrease.
     *
     * Simple Rate coding: bitfield [0..0x7F]
     *   0x7F: rate = 0
     *   Bits [0..1]: 0x0: base rate=0x1C000000
     *                0x1: base rate=0x18000000
     *                0x2: base rate=0x14000000
     *                0x3: base rate=0x10000000
     *   Bits [2..6]: number of bits to logically shift the base rate to the right
     *
     * @param sasCore     sasCore handle
     * @param voice       voice number, [0..31]
     * @param ADSREnv1    ADSR bitfield 1
     *                    Bits [0..3]: Sustain Level, coded as the bits [29..26]-1
     *                                 of the sustain level
     *                    Bits [4..7]: Decay Rate, coded as the number of bits to
     *                                 logically shift 0x80000000 to the right
     *                    Bits [8..14]: Attack Rate, coded as a Simple Rate
     *                    Bit  [15]: Attack curve type
     *                               (0=linear increase, 1=linear bent)
     * @param ADSREnv2    ADSR bitfield 2
     *                    Bits [0..4]: Release Rate
     *                                 0x1F: release rate = 0
     *                                 [0..0x1E]: n
     *                                    if release curve type is linear decrease
     *                                      release rate = 0x40000000 >>> (n+2)
     *                                    else
     *                                      release rate = 0x80000000 >>> n
     *                    Bit  [5]: Release curve type
     *                              (0=linear decrease, 1=exponential decrease)
     *                    Bits [6..12]: Sustain Rate, coded as a Simple Rate
     *                    Bits [13..15]: Sustain curve type
     *                                   (0=linear increase,
     *                                    2=linear decrease,
     *                                    4=linear bent,
     *                                    6=exponential decrease,
     *                                    other values are invalid)
     * @return            0 if OK
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                    ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     *                    ERROR_SAS_INVALID_ADSR_CURVE_MODE if an invalid sustain curve type is provided
     */
    @HLEFunction(nid = 0xCBCD4F79, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetSimpleADSR(int sasCore, int voice, int ADSREnv1, int ADSREnv2) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        // Only the low-order 16 bits are valid for both parameters.
        int env1Bitfield = (ADSREnv1 & 0xFFFF);
        int env2Bitfield = (ADSREnv2 & 0xFFFF);

        // The bitfields represent every value except for the decay curve shape,
        // which seems to be unchanged in simple mode.
        VoiceADSREnvelope envelope = voices[voice].getEnvelope();
        envelope.SustainLevel = getSimpleSustainLevel(env1Bitfield);
        envelope.DecayRate = getSimpleDecayRate(env1Bitfield);
        envelope.DecayCurveType = PSP_SAS_ADSR_CURVE_MODE_EXPONENT_DECREASE;
        envelope.AttackRate = getSimpleAttackRate(env1Bitfield);
        envelope.AttackCurveType = getSimpleAttackCurveType(env1Bitfield);

        envelope.ReleaseRate = getSimpleReleaseRate(env2Bitfield);
        envelope.ReleaseCurveType = getSimpleReleaseCurveType(env2Bitfield);
        envelope.SustainRate = getSimpleSustainRate(env2Bitfield);
        envelope.SustainCurveType = getSimpleSustainCurveType(env2Bitfield);

        if (log.isDebugEnabled()) {
            log.debug(String.format("__sceSasSetSimpleADSR voice=0x%X: %s", voice, envelope.toString()));
        }

        return 0;
    }

    /**
     * Set the number of samples generated by one __sceSasCore call.
     *
     * @param sasCore     sasCore handle
     * @param grain       number of samples
     * @return            0 if OK
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0xD1E0A01E, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetGrain(int sasCore, int grain) {
        checkSasHandleGood(sasCore);

        grainSamples = grain;

        return 0;
    }

    /**
     * Set the wave form effect volume (unknown parameters).
     *
     * @param sasCore     sasCore handle
     * @param leftVolume  unknown parameter
     * @param rightVolume unknown parameter
     * @return            wave form effect type
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0xD5A229C9, version = 150, checkInsideInterrupt = true)
    public int __sceSasRevEVOL(int sasCore, int leftVolume, int rightVolume) {
        checkSasHandleGood(sasCore);

        waveformEffectLeftVol = leftVolume;
        waveformEffectRightVol = rightVolume;

        return 0;
    }

    /**
     * (Unknown) Set a steep waveform for one voice.
     *
     * @param sasCore           sasCore handle
     * @param voice             voice number [0..31]
     * @param unknown           unknown parameter
     * @return 0                if OK
     *                          ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     *                          ERROR_SAS_INVALID_VOICE if an invalid voice number is provided
     */
    @HLEUnimplemented
    @HLEFunction(nid = 0xD5EBBBCD, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetSteepWave(int sasCore, int voice, int unknown) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        return 0;
    }

    /**
     * (Unknown) Get the output mode.
     *
     * @param sasCore     sasCore handle
     * @return            (unknown) 0 stereo
     *                              1 multichannel
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0xE175EF66, version = 150, checkInsideInterrupt = true)
    public int __sceSasGetOutputmode(int sasCore) {
        checkSasHandleGood(sasCore);

    	return getOutputMode();
    }

    /**
     * (Unknown) Set the output mode.
     *
     * @param sasCore     sasCore handle
     * @param outputMode  (unknown) 0 stereo
     *                              1 multichannel
     * @return 0          if OK
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0xE855BF76, version = 150, checkInsideInterrupt = true)
    public int __sceSasSetOutputmode(int sasCore, int outputMode) {
        checkSasHandleGood(sasCore);
        this.outputMode = outputMode;

        return 0;
    }

    /**
     * Set the wave form effect dry and wet status (unknown parameters).
     *
     * @param sasCore     sasCore handle
     * @param dry         unknown parameter
     * @param wet         unknown parameter
     * @return            wave form effect type
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0xF983B186, version = 150, checkInsideInterrupt = true)
    public int __sceSasRevVON(int sasCore, int dry, int wet) {
        checkSasHandleGood(sasCore);

        waveformEffectIsDryOn = (dry > 0);
        waveformEffectIsWetOn = (wet > 0);

        return 0;
    }

    /**
     * Get the current envelope height for all the voices.
     *
     * @param sasCore     sasCore handle
     * @param heightsAddr (int *) address where to return the envelope heights,
     *                    stored as 32 bit values [0..0x40000000].
     *                        heightsAddr[0] = envelope height of voice 0
     *                        heightsAddr[1] = envelope height of voice 1
     *                        ...
     * @return            0 if OK
     *                    ERROR_SAS_NOT_INIT if an invalid sasCore handle is provided
     */
    @HLEFunction(nid = 0x07F58C24, version = 150, checkInsideInterrupt = true)
    public int __sceSasGetAllEnvelopeHeights(int sasCore, @BufferInfo(lengthInfo=LengthInfo.fixedLength, length=PSP_SAS_VOICES_MAX*4, usage=Usage.out) TPointer32 heightsAddr) {
        checkSasHandleGood(sasCore);

		IMemoryWriter memoryWriter = MemoryWriter.getMemoryWriter(heightsAddr.getAddress(), voices.length * 4, 4);
		for (int i = 0; i < voices.length; i++) {
			int voiceHeight = voices[i].getEnvelope().height;
			memoryWriter.writeNext(voiceHeight);
			if (log.isTraceEnabled() && voiceHeight != 0) {
				log.trace(String.format("__sceSasGetAllEnvelopeHeights height voice #%d=0x%08X", i, voiceHeight));
			}
		}
		memoryWriter.flush();

		return 0;
    }  

    /** Identical to __sceSasSetVoice, but for raw PCM data (VAG/ADPCM is not allowed). */
    @HLEFunction(nid = 0xE1CD9561, version = 500, checkInsideInterrupt = true)
    public int __sceSasSetVoicePCM(int sasCore, int voice, TPointer pcmAddr, int size, int loopmode) {
        if (size <= 0 || size > 0x10000) {
        	log.warn(String.format("__sceSasSetVoicePCM invalid size 0x%08X", size));

        	return SceKernelErrors.ERROR_SAS_INVALID_SIZE;
        }

        checkSasAndVoiceHandlesGood(sasCore, voice);

        voices[voice].setPCM(pcmAddr.getAddress(), size);
        voices[voice].setLoopMode(loopmode);

        return 0;
    }

    @HLELogging(level="info")
	@HLEFunction(nid = 0x4AA9EAD6, version = 600, checkInsideInterrupt = true)
    public int __sceSasSetVoiceATRAC3(int sasCore, int voice, int atrac3Context) {
		// atrac3Context is the value returned by _sceAtracGetContextAddress

        checkSasAndVoiceHandlesGood(sasCore, voice);

        AtracID atracId = Modules.sceAtrac3plusModule.getAtracIdFromContext(atrac3Context);
        if (atracId != null) {
        	voices[voice].setAtracId(atracId);

        	// Store the atrac3Context address into the sasCore structure.
        	setSasCoreAtrac3Context(sasCore, voice, atrac3Context);
        }

        return 0;
    }

    @HLELogging(level="info")
    @HLEFunction(nid = 0x7497EA85, version = 600, checkInsideInterrupt = true)
    public int __sceSasConcatenateATRAC3(int sasCore, int voice, @CanBeNull TPointer atrac3DataAddr, int atrac3DataLength) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        int atrac3Context = getSasCoreAtrac3Context(sasCore, voice);
        AtracID atracID = Modules.sceAtrac3plusModule.getAtracIdFromContext(atrac3Context);
        if (atracID.getSecondBufferAddr() != -1) {
        	return SceKernelErrors.ERROR_SAS_CANNOT_CONCATENATE_ATRA3;
        }
        atracID.setSecondBuffer(atrac3DataAddr.getAddress(), atrac3DataLength);

        return 0;
    }

    @HLEFunction(nid = 0xF6107F00, version = 600, checkInsideInterrupt = true)
    public int __sceSasUnsetATRAC3(int sasCore, int voice) {
        checkSasAndVoiceHandlesGood(sasCore, voice);

        voices[voice].setAtracId(null);

        // Reset the atrac3Context address
        setSasCoreAtrac3Context(sasCore, voice, 0);

        return 0;
    }

    @HLEUnimplemented
    @HLEFunction(nid = 0xB0F9F98F, version = 150, moduleName = "sceSasCore_driver")
    public int sceSasCoreInit() {
        return 0;
    }

    @HLEUnimplemented
    @HLEFunction(nid = 0xE143A1EA, version = 150, moduleName = "sceSasCore_driver")
    public int sceSasCoreExit() {
        return 0;
    }
}
