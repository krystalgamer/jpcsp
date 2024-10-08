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
package jpcsp.Debugger;

import static jpcsp.Allegrex.GprState.NUMBER_REGISTERS;
import jpcsp.Memory;
import jpcsp.Allegrex.Common;
import jpcsp.Allegrex.CpuState;
import jpcsp.Allegrex.Decoder;
import jpcsp.HLE.Modules;

public class StepFrame {

    // Optimize for speed and memory, just store the raw details and calculate
    // the formatted message the first time getMessage it called.
    private int pc;
    private int[] gpr = new int[NUMBER_REGISTERS];

    private int opcode;
    private String asm;

    private int threadID;
    private String threadName;

    private boolean dirty;
    private String message;

    public StepFrame() {
        dirty = false;
        message = "";
    }

    public void make(CpuState cpu) {
        pc = cpu.pc;
        for (int i = 0; i < NUMBER_REGISTERS; i++) {
        	gpr[i] = cpu.getRegister(i);
        }
        threadID = Modules.ThreadManForUserModule.getCurrentThreadID();
        threadName = Modules.ThreadManForUserModule.getThreadName(threadID);

        Memory mem = MemoryViewer.getMemory();
        if (MemoryViewer.isAddressGood(cpu.pc)) {
            opcode = mem.read32(cpu.pc);
            Common.Instruction insn = Decoder.instruction(opcode);
            asm = insn.disasm(cpu.pc, opcode);
        } else {
            opcode = 0;
            asm = "?";
        }

        dirty = true;
    }

    private String getThreadInfo() {
        // Thread ID - 0x04600843
        // Th Name   - user_main
        return String.format("Thread ID - 0x%08X\n", threadID)
            + "Th Name   - " + threadName + "\n";
    }

    private String getRegistersInfo() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NUMBER_REGISTERS; i += 4) {
            sb.append(String.format("%s:0x%08X %s:0x%08X %s:0x%08X %s:0x%08X\n",
                Common.gprNames[i + 0].substring(1), gpr[i + 0],
                Common.gprNames[i + 1].substring(1), gpr[i + 1],
                Common.gprNames[i + 2].substring(1), gpr[i + 2],
                Common.gprNames[i + 3].substring(1), gpr[i + 3]));
        }

        return sb.toString();
    }

    private void makeMessage() {
        String address = String.format("0x%08X", pc);
        String rawdata = String.format("0x%08X", opcode);

        message = getThreadInfo()
            + getRegistersInfo()
            + address
            + ": " + rawdata
            + " - " + asm;
    }

    public String getMessage() {
        if (dirty) {
            dirty = false;
            makeMessage();
        }
        return message;
    }

    public boolean isJAL() {
        return (asm.indexOf("jal") != -1);
    }

    public boolean isJRRA() {
        return (asm.indexOf("jr") != -1) && (asm.indexOf("$ra") != -1);
    }
}