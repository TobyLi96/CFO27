package comp207p.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.ArrayList;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantPool;

import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;



public class ConstantFolder {
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	ArrayList<String> loops = new ArrayList<String>();

	public ConstantFolder(String classFilePath)	{
		try {
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void deleteInst(InstructionList instList, InstructionHandle handle) {
		try {
			instList.redirectBranches(handle, handle.getPrev());
			instList.delete(handle);
			instList.setPositions();
		} catch (Exception e) {
			//do nothing
		}
	}

	private boolean checkConstantVar(InstructionList instList, int storeIndex) {
		int count = 0;
		for (InstructionHandle handle : instList.getInstructionHandles()) {
			if (handle.getInstruction() instanceof StoreInstruction || handle.getInstruction() instanceof IINC) {
				int index = ((LocalVariableInstruction) handle.getInstruction()).getIndex();
				if (storeIndex == index) count++;
			}
			if (count > 1) return false;
		}
		return true;
	}

	private boolean inLoop(int position) {
		if (loops.isEmpty()) return false;
		for (int i = 0; i < loops.size(); i++) {
			if (position >= Integer.parseInt(loops.get(i).substring(0, loops.get(i).indexOf(','))) && position <= Integer.parseInt(loops.get(i).substring(loops.get(i).indexOf(',') + 1, loops.get(i).length()))) {
				return true;
			}
		}
		return false;
	}

	private int checkStoreInLoop(InstructionList instList, int loopStart, int storeIndex) {
		int loopEnd = 0;
		for (int i = 0; i < loops.size(); i++) {
			if (loopStart >= Integer.parseInt(loops.get(i).substring(0, loops.get(i).indexOf(','))) && loopStart <= Integer.parseInt(loops.get(i).substring(loops.get(i).indexOf(',') + 1, loops.get(i).length()))) {
				loopStart = Integer.parseInt(loops.get(i).substring(0, loops.get(i).indexOf(',')));
				loopEnd = Integer.parseInt(loops.get(i).substring(loops.get(i).indexOf(',') + 1, loops.get(i).length()));
				break;
			}
		}
		InstructionHandle handle = null;
		for (InstructionHandle temp : instList.getInstructionHandles()) {
			if (temp.getPosition() == loopStart) {
				handle = temp;
				break;
			} 
		}
		boolean keepOrigStore = false;
		while (handle.getPosition() != loopEnd) {
			if (handle.getInstruction() instanceof LoadInstruction || handle.getInstruction() instanceof IINC) {
				if (((LocalVariableInstruction) handle.getInstruction()).getIndex() == storeIndex) {
					keepOrigStore = true;
				}
			}
			if (handle.getInstruction() instanceof StoreInstruction || handle.getInstruction() instanceof IINC) {
				if (((LocalVariableInstruction) handle.getInstruction()).getIndex() == storeIndex) {
					if (keepOrigStore) {
						return 2; // store is in loop after load
					}
					else {
						return 1; // store is in loop before load
					}					
				}
			}
			handle = handle.getNext();	
		}
		return 0; // store is not in loop
	}

	private void convertLoadInst(InstructionList instList, InstructionHandle pushHandle, int storeIndex, Number pushVal) {
		InstructionHandle handle = pushHandle;
		int storeCount = 0;
		boolean keepOrigStore = false;
		boolean checkedLoop = false;
		
		while (handle != null) {
			findLoops(instList);
			if (inLoop(handle.getPosition())) {
				if (!checkedLoop) {
					if (checkStoreInLoop(instList, handle.getPosition(), storeIndex) == 2) {
						keepOrigStore = true;
						break;
					}
					else {
						checkedLoop = true;
					}
				}
			}
			else {
				if (checkedLoop) {
					checkedLoop = false;
				}
			}
			if (handle.getInstruction() instanceof StoreInstruction || handle.getInstruction() instanceof IINC) {
				int index = ((LocalVariableInstruction) handle.getInstruction()).getIndex();
				if (index == storeIndex) storeCount++;
				if (storeCount > 1) break;
				handle = handle.getNext();
			}
			else if (handle.getInstruction() instanceof LoadInstruction) {
				int loadIndex = ((LoadInstruction) handle.getInstruction()).getIndex();
				if (loadIndex == storeIndex) {
					if (pushHandle.getInstruction() instanceof BIPUSH) {
						instList.insert(handle, new BIPUSH((byte) ((int) pushVal)));
					}
					else if (pushHandle.getInstruction() instanceof SIPUSH) {
						instList.insert(handle, new SIPUSH((short) ((int) pushVal)));
					}
					else if (pushHandle.getInstruction() instanceof ICONST) {
						instList.insert(handle, new ICONST((int) pushVal));
					}
					else if (pushHandle.getInstruction() instanceof DCONST) {
						instList.insert(handle, new DCONST((double) pushVal));
					}
					else if (pushHandle.getInstruction() instanceof FCONST) {
						instList.insert(handle, new FCONST((float) pushVal));
					}
					else if (pushHandle.getInstruction() instanceof LCONST) {
						instList.insert(handle, new LCONST((long) pushVal));
					}
					else if (pushHandle.getInstruction() instanceof LDC) {
						instList.insert(handle, new LDC(((CPInstruction) pushHandle.getInstruction()).getIndex()));
					}
					else if (pushHandle.getInstruction() instanceof LDC2_W) {
						instList.insert(handle, new LDC2_W(((CPInstruction) pushHandle.getInstruction()).getIndex()));	
					}
					instList.setPositions();
					InstructionHandle temp = handle;
					handle = handle.getNext();
					deleteInst(instList, temp);
				}
				else {
					handle = handle.getNext();
				}
			}
			else {
				handle = handle.getNext();
			}
		}
		if (!keepOrigStore) {
			try {
				instList.redirectBranches(pushHandle, pushHandle.getNext().getNext());
				instList.delete(pushHandle.getNext());
				instList.delete(pushHandle);
				instList.setPositions();
			} catch(Exception e) {
				// do nothing
			}
		}
	}

	private Number[] getArithmeticVals(ConstantPoolGen cpgen, InstructionHandle handle) {
		int counter = 2;
		Number[] valueOnStack = new Number[2];
		for (counter = 2; counter != 0; counter--) {
			handle = handle.getPrev();
			if (handle.getInstruction() instanceof BIPUSH) {
				valueOnStack[counter-1] = ((BIPUSH) handle.getInstruction()).getValue();
			}
			else if (handle.getInstruction() instanceof SIPUSH) {
				valueOnStack[counter-1] = ((SIPUSH) handle.getInstruction()).getValue();
			}
			else if (handle.getInstruction() instanceof ICONST) {
				valueOnStack[counter-1] = ((ICONST) handle.getInstruction()).getValue();
			}
			else if (handle.getInstruction() instanceof DCONST) {
				valueOnStack[counter-1] = ((DCONST) handle.getInstruction()).getValue();
			}
			else if (handle.getInstruction() instanceof FCONST) {
				valueOnStack[counter-1] = ((FCONST) handle.getInstruction()).getValue();
			}
			else if (handle.getInstruction() instanceof LCONST) {
				valueOnStack[counter-1] = ((LCONST) handle.getInstruction()).getValue();
			}
			else if (handle.getInstruction() instanceof LDC) {
				valueOnStack[counter-1] = (Number)((LDC) handle.getInstruction()).getValue(cpgen);
			}
			else if (handle.getInstruction() instanceof LDC2_W) {
				valueOnStack[counter-1] = ((LDC2_W) handle.getInstruction()).getValue(cpgen);
			}
		}
		return valueOnStack;
	}
	
	private Number[] getArithmeticRes(ConstantPoolGen cpgen, InstructionHandle handle) {
		Number[] resultArray = new Number[2];
		if (handle.getInstruction() instanceof DADD) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((double)valueOnStack[0] + (double)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof FADD) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((float)valueOnStack[0] + (float)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof IADD) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] + (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LADD) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] + (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof DDIV) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((double)valueOnStack[0] / (double)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof FDIV) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((float)valueOnStack[0] / (float)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof IDIV) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] / (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LDIV) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] / (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof DMUL) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((double)valueOnStack[0] * (double)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof FMUL) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((float)valueOnStack[0] * (float)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof IMUL) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] * (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LMUL) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] * (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof DSUB) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((double)valueOnStack[0] - (double)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof FSUB) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((float)valueOnStack[0] - (float)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof ISUB) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] - (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LSUB) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] - (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof DREM) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((double)valueOnStack[0] % (double)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof FREM) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((float)valueOnStack[0] % (float)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof IREM) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] % (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LREM) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] % (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof DNEG) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = (double)(0 - (double) valueOnStack[1]);
			resultArray[1] = 1;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof FNEG) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = (float)(0 - (float) valueOnStack[1]);
			resultArray[1] = 1;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof INEG) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = (int)(0 - (int) valueOnStack[1]);
			resultArray[1] = 1;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LNEG) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = (long)(0 - (long) valueOnStack[1]);
			resultArray[1] = 1;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof IAND) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] & (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof IOR) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] | (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof ISHL) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] << (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof ISHR) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] >> (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof IUSHR) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] >>> (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof IXOR) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((int)valueOnStack[0] ^ (int)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LAND) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] & (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LOR) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] | (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LSHL) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] << (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LSHR) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] >> (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LUSHR) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] >>> (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		else if (handle.getInstruction() instanceof LXOR) {
			Number[] valueOnStack = getArithmeticVals(cpgen, handle);
			if (valueOnStack[0] == null || valueOnStack[1] == null) {
				return null;
			}
			resultArray[0] = ((long)valueOnStack[0] ^ (long)valueOnStack[1]);
			resultArray[1] = 2;
			return resultArray;
		}
		return null;
	}

	private Number getConversionVals(ConstantPoolGen cpgen, InstructionHandle handle) {	
		handle = handle.getPrev();
		if (handle.getInstruction() instanceof BIPUSH) {
			return ((BIPUSH) handle.getInstruction()).getValue();
		}
		else if (handle.getInstruction() instanceof SIPUSH) {
			return ((SIPUSH) handle.getInstruction()).getValue();
		}
		else if (handle.getInstruction() instanceof ICONST) {
			return ((ICONST) handle.getInstruction()).getValue();
		}
		else if (handle.getInstruction() instanceof DCONST) {
			return ((DCONST) handle.getInstruction()).getValue();
		}
		else if (handle.getInstruction() instanceof FCONST) {
			return ((FCONST) handle.getInstruction()).getValue();
		}
		else if (handle.getInstruction() instanceof LCONST) {
			return ((LCONST) handle.getInstruction()).getValue();
		}
		else if (handle.getInstruction() instanceof LDC) {
			return (Number)((LDC) handle.getInstruction()).getValue(cpgen);
		}
		else if (handle.getInstruction() instanceof LDC2_W) {
			return ((LDC2_W) handle.getInstruction()).getValue(cpgen);
		}
		return null;
	}

	private Number getConversionRes(ConstantPoolGen cpgen, InstructionHandle handle){
		Number prevValue = getConversionVals(cpgen, handle);
		if(prevValue == null) {
			return null;
		}
		else if (handle.getInstruction() instanceof D2F) {
			return (float) ((double) prevValue);
		} 
		else if (handle.getInstruction() instanceof D2I) {
			return (int) ((double) prevValue);
		} 
		else if (handle.getInstruction() instanceof D2L) {
			return (long) ((double) prevValue);
		} 
		else if (handle.getInstruction() instanceof F2D) {
			return (double) ((float) prevValue);
		} 
		else if (handle.getInstruction() instanceof F2I) {
			return (int) ((float) prevValue);
		} 
		else if (handle.getInstruction() instanceof F2L) {
			return (long) ((float) prevValue);
		} 
		else if (handle.getInstruction() instanceof I2B) {
			return (byte) ((int) prevValue);
		} 
		else if (handle.getInstruction() instanceof I2D) {
			return (double) ((int) prevValue);
		} 
		else if (handle.getInstruction() instanceof I2F) {
			return (float) ((int) prevValue);
		} 
		else if (handle.getInstruction() instanceof I2L) {
			return (long) ((int) prevValue);
		} 
		else if (handle.getInstruction() instanceof I2S) {
			return (short) ((int) prevValue);
		} 
		else if (handle.getInstruction() instanceof L2D) {
			return (double) ((long) prevValue);
		} 
		else if (handle.getInstruction() instanceof L2F) {
			return (float) ((long) prevValue);
		} 
		else if (handle.getInstruction() instanceof L2I) {
			return (int) ((long) prevValue);
		} 
		else if (handle.getInstruction() instanceof I2D) {
			return (double) ((int) prevValue);
		}
		return null;
	}
	
	private Number getPrevVal(InstructionList instList, InstructionHandle handle, ConstantPoolGen cpgen, int storeIndex) {
		while (handle != null) {
			handle = handle.getPrev();
			if (handle.getInstruction() instanceof StoreInstruction) {
				int index = ((StoreInstruction) handle.getInstruction()).getIndex();
				if (index == storeIndex) {
					if (handle.getPrev().getInstruction() instanceof BIPUSH) {
						return ((BIPUSH) handle.getPrev().getInstruction()).getValue();
					}
					else if (handle.getPrev().getInstruction() instanceof SIPUSH) {
						return ((SIPUSH) handle.getPrev().getInstruction()).getValue();
					}
					else if (handle.getPrev().getInstruction() instanceof LDC) {
						return (Number) ((LDC) handle.getPrev().getInstruction()).getValue(cpgen);
					}
					else {
						return null;
					}
				}
			}
		}
		return null;
	}

	private void findLoops(InstructionList instList) {
		loops.clear();
		for (InstructionHandle handle : instList.getInstructionHandles()) {
			if (handle.getInstruction() instanceof GOTO) {
				InstructionHandle targetHandle = ((GOTO) handle.getInstruction()).getTarget();
				if (handle.getPosition() > targetHandle.getPosition()) {
					// if (targetHandle.getInstruction() instanceof IINC) {
					// 	int index = ((LocalVariableInstruction) targetHandle.getInstruction()).getIndex();
					// 	int inc = ((IINC) targetHandle.getInstruction()).getIncrement();
					// 	instList.insert(handle, new IINC(index, inc));
					// 	instList.setPositions();
					// 	((BranchInstruction) handle.getInstruction()).updateTarget(targetHandle, targetHandle.getNext());
					// 	instList.setPositions();
					// }
					targetHandle = ((GOTO) handle.getInstruction()).getTarget();
					loops.add(Integer.toString(targetHandle.getPosition()) + "," + Integer.toString(handle.getPosition()));
				}
				else {
					if (targetHandle.getInstruction() instanceof IINC) {
						int index = ((LocalVariableInstruction) targetHandle.getInstruction()).getIndex();
						int inc = ((IINC) targetHandle.getInstruction()).getIncrement();
						instList.insert(targetHandle, new BIPUSH((byte) inc));
						InstructionHandle temp = targetHandle.getPrev();
						instList.insert(targetHandle, new ILOAD(index));
						instList.insert(targetHandle, new IADD());
						instList.insert(targetHandle, new ISTORE(index));
						((BranchInstruction) handle.getInstruction()).updateTarget(targetHandle, temp);
						try {
							instList.redirectBranches(targetHandle, temp);
							instList.delete(targetHandle);
							instList.setPositions();
						} catch(Exception e) {
							// do nothing
						}
					}
				}
			}
		}
	}	

	private void optimizeMethod(ClassGen cgen, ConstantPoolGen cpgen, Method method) {
		// Get the Code of the method, which is a collection of bytecode instructions
		Code methodCode = method.getCode();

		// Now get the actualy bytecode data in byte array, 
		// and use it to initialise an InstructionList
		InstructionList instList = new InstructionList(methodCode.getCode());

		// Initialise a method generator with the original method as the baseline	
		MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(), null, method.getName(), cgen.getClassName(), instList, cpgen);
		// InstructionHandle is a wrapper for actual Instructions
		findLoops(instList);
		// Turns IINC instructions into a series of push/local variable/constant pool instruction
		for (InstructionHandle handle : instList.getInstructionHandles()) {
			findLoops(instList);
			if (handle.getInstruction() instanceof IINC) {
				if (inLoop(handle.getPosition())) {
					break;
				}
				int index = ((LocalVariableInstruction) handle.getInstruction()).getIndex();
				Number prevVal = getPrevVal(instList, handle, cpgen, index);
				int inc = ((IINC) handle.getInstruction()).getIncrement();
				if (prevVal != null) {
					InstructionHandle temp = null;
					if ((int) prevVal > 32767 || (int) prevVal < -32768) {
						int cpIndex = cpgen.addInteger(((int) prevVal) + inc);
            instList.insert(handle, new LDC(cpIndex));
            instList.setPositions();
          } 
          else if ((int) prevVal < -128 || (int) prevVal > 127) {
            instList.insert(handle, new SIPUSH((short) ((int) prevVal + inc)));
            instList.setPositions();
          } 
          else {
            instList.insert(handle, new BIPUSH((byte) ((int) prevVal + inc)));
            instList.setPositions();
          }
          temp = handle.getPrev();
          instList.insert(handle, new ISTORE(index));
          try {
          	instList.redirectBranches(handle, temp);
          	instList.delete(handle);
          	instList.setPositions();
          } catch(Exception e) {
          	// do nothing
          }
				}
				else {
					instList.insert(handle, new BIPUSH((byte) inc));
					InstructionHandle temp = handle.getPrev();
					instList.insert(handle, new ILOAD(index));
					instList.insert(handle, new IADD());
					instList.insert(handle, new ISTORE(index));
					try {
						instList.redirectBranches(handle, temp);
						instList.delete(handle);
						instList.setPositions();
					} catch(Exception e) {
						// do nothing
					}
				}
			}
		}

		for (InstructionHandle handle : instList.getInstructionHandles())	{
			findLoops(instList);
			if (handle.getInstruction() instanceof ArithmeticInstruction) {
				Number[] result = getArithmeticRes(cpgen, handle);
				if (result != null) {
					// for (int counter = (int) result[1]; counter != 0; counter--) {
					// 	deleteInst(instList, handle.getPrev());
					// }
					int cpIndex = 0;
					if (result[0] instanceof Integer) {
						cpIndex = cpgen.addInteger((int) result[0]);
						instList.insert(handle, new LDC(cpIndex));
						instList.setPositions();
					}
					else if (result[0] instanceof Float) {
						cpIndex = cpgen.addFloat((float) result[0]);
						instList.insert(handle, new LDC(cpIndex));
						instList.setPositions();
					}
					else if (result[0] instanceof Double) {
						cpIndex = cpgen.addDouble((double) result[0]);
						instList.insert(handle, new LDC2_W(cpIndex));
						instList.setPositions();
					}
					else if (result[0] instanceof Long) {
						cpIndex = cpgen.addLong((long) result[0]);
						instList.insert(handle, new LDC2_W(cpIndex));
						instList.setPositions();
					}
					try {
						if ((int) result[1] == 2) {
							instList.redirectBranches(handle.getPrev().getPrev().getPrev(), handle.getPrev());
						}
						else {
							instList.redirectBranches(handle.getPrev().getPrev(), handle.getPrev());
						}
						for (int counter = (int) result[1]; counter != 0; counter--) {
							deleteInst(instList, handle.getPrev().getPrev());
						}
						instList.setPositions();
					} catch(Exception e) {
						// do nothing
					}
					deleteInst(instList, handle);
				}
			}
			else if (handle.getInstruction() instanceof ConversionInstruction) {
				Number convertedNumber = getConversionRes(cpgen, handle);
				if (convertedNumber != null) {
					// boolean target = checkTarget(instList, handle.getPrev());
					// deleteInst(instList, handle.getPrev());
					int cpIndex = 0;
					if (convertedNumber instanceof Integer) {
						cpIndex = cpgen.addInteger((int) convertedNumber);
						instList.insert(handle, new LDC(cpIndex));
						instList.setPositions();
					}
					else if (convertedNumber instanceof Float) {
						cpIndex = cpgen.addFloat((float) convertedNumber);
						instList.insert(handle, new LDC(cpIndex));
						instList.setPositions();
					}
					else if (convertedNumber instanceof Double) {
						cpIndex = cpgen.addDouble((double) convertedNumber);
						instList.insert(handle, new LDC2_W(cpIndex));
						instList.setPositions();
					}
					else if (convertedNumber instanceof Long) {
						cpIndex = cpgen.addLong((long) convertedNumber);
						instList.insert(handle, new LDC2_W(cpIndex));
						instList.setPositions();
					}
					try {
						instList.redirectBranches(handle.getPrev().getPrev(), handle.getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.setPositions();
					} catch(Exception e) {
						// do nothing
					}
					deleteInst(instList, handle);
				}
			}
			else if (handle.getInstruction() instanceof LCMP) {
				Number[] valueOnStack = getArithmeticVals(cpgen, handle);
				int value = 0;
				if (valueOnStack[0] != null && valueOnStack[0] != null) {
					// deleteInst(instList, handle.getPrev());
					// deleteInst(instList, handle.getPrev());
					if ((long) valueOnStack[0] == (long) valueOnStack[1]) {
						value = 0;
					} 
					else if ((long) valueOnStack[0] > (long) valueOnStack[1]) {
						value = 1;
					} 
					else {
						value = -1;
					}
					instList.insert(handle, new ICONST(value));
					instList.setPositions();
					try {
						instList.redirectBranches(handle.getPrev().getPrev().getPrev(), handle.getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.setPositions();
					} catch(Exception e) {
						// do nothing
					}
					deleteInst(instList, handle);
				}
			}
			else if (handle.getInstruction() instanceof DCMPG) {
				Number[] valueOnStack = getArithmeticVals(cpgen, handle);
				int value = 0;
				if (valueOnStack[0] != null && valueOnStack[0] != null) {
					// deleteInst(instList, handle.getPrev());
					// deleteInst(instList, handle.getPrev());
					if ((double) valueOnStack[0] == (double) valueOnStack[1]) {
						value = 0;
					} 
					else if ((double) valueOnStack[0] > (double) valueOnStack[1]) {
						value = 1;
					} 
					else {
						value = -1;
					}
					instList.insert(handle, new ICONST(value));
					instList.setPositions();
					try {
						instList.redirectBranches(handle.getPrev().getPrev().getPrev(), handle.getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.setPositions();
					} catch(Exception e) {
						// do nothing
					}
					deleteInst(instList, handle);
				}
			}
			else if (handle.getInstruction() instanceof DCMPL) {
				Number[] valueOnStack = getArithmeticVals(cpgen, handle);
				int value = 0;
				if (valueOnStack[0] != null && valueOnStack[0] != null) {
					// deleteInst(instList, handle.getPrev());
					// deleteInst(instList, handle.getPrev());
					if ((double) valueOnStack[0] == (double) valueOnStack[1]) {
						value = 0;
					} 
					else if ((double) valueOnStack[0] < (double) valueOnStack[1]) {
						value = 1;
					} 
					else {
						value = -1;
					}
					instList.insert(handle, new ICONST(value));
					instList.setPositions();
					try {
						instList.redirectBranches(handle.getPrev().getPrev().getPrev(), handle.getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.setPositions();
					} catch(Exception e) {
						// do nothing
					}
					deleteInst(instList, handle);
				}
			}
			else if (handle.getInstruction() instanceof FCMPG) {
				Number[] valueOnStack = getArithmeticVals(cpgen, handle);
				int value = 0;
				if (valueOnStack[0] != null && valueOnStack[0] != null) {
					// deleteInst(instList, handle.getPrev());
					// deleteInst(instList, handle.getPrev());
					if ((float) valueOnStack[0] == (float) valueOnStack[1]) {
						value = 0;
					} 
					else if ((float) valueOnStack[0] > (float) valueOnStack[1]) {
						value = 1;
					} 
					else {
						value = -1;
					}
					instList.insert(handle, new ICONST(value));
					try {
						instList.redirectBranches(handle.getPrev().getPrev().getPrev(), handle.getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.setPositions();
					} catch(Exception e) {
						// do nothing
					}
					deleteInst(instList, handle);
				}
			}
			else if (handle.getInstruction() instanceof FCMPL) {
				Number[] valueOnStack = getArithmeticVals(cpgen, handle);
				int value = 0;
				if (valueOnStack[0] != null && valueOnStack[0] != null) {
					// deleteInst(instList, handle.getPrev());
					// deleteInst(instList, handle.getPrev());
					if ((float) valueOnStack[0] == (float) valueOnStack[1]) {
						value = 0;
					} 
					else if ((float) valueOnStack[0] < (float) valueOnStack[1]) {
						value = 1;
					} 
					else {
						value = -1;
					}
					instList.insert(handle, new ICONST(value));
					try {
						instList.redirectBranches(handle.getPrev().getPrev().getPrev(), handle.getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.delete(handle.getPrev().getPrev());
						instList.setPositions();
					} catch(Exception e) {
						// do nothing
					}
					deleteInst(instList, handle);
				}
			}
			else if (handle.getInstruction() instanceof StoreInstruction) {
				int index = ((StoreInstruction) handle.getInstruction()).getIndex();
				// if (checkConstantVar(instList, index)) {
				if (handle.getPrev().getInstruction() instanceof BIPUSH) {
					Number pushVal = ((BIPUSH) handle.getPrev().getInstruction()).getValue();
					convertLoadInst(instList, handle.getPrev(), index, pushVal);
				}
				else if (handle.getPrev().getInstruction() instanceof SIPUSH) {
					Number pushVal = ((SIPUSH) handle.getPrev().getInstruction()).getValue();
					convertLoadInst(instList, handle.getPrev(), index, pushVal);
				}
				else if (handle.getPrev().getInstruction() instanceof ICONST) {
					Number pushVal = ((ICONST) handle.getPrev().getInstruction()).getValue();
					convertLoadInst(instList, handle.getPrev(), index, pushVal);
				}
				else if (handle.getPrev().getInstruction() instanceof DCONST) {
					Number pushVal = ((DCONST) handle.getPrev().getInstruction()).getValue();
					convertLoadInst(instList, handle.getPrev(), index, pushVal);
				}
				else if (handle.getPrev().getInstruction() instanceof FCONST) {
					Number pushVal = ((FCONST) handle.getPrev().getInstruction()).getValue();
					convertLoadInst(instList, handle.getPrev(), index, pushVal);
				}
				else if (handle.getPrev().getInstruction() instanceof LCONST) {
					Number pushVal = ((LCONST) handle.getPrev().getInstruction()).getValue();
					convertLoadInst(instList, handle.getPrev(), index, pushVal);
				}
				else if (handle.getPrev().getInstruction() instanceof LDC) {
					Number pushVal = (Number) ((LDC) handle.getPrev().getInstruction()).getValue(cpgen);
					convertLoadInst(instList, handle.getPrev(), index, pushVal);
				}
				else if (handle.getPrev().getInstruction() instanceof LDC2_W) {
					Number pushVal = ((LDC2_W) handle.getPrev().getInstruction()).getValue(cpgen);
					convertLoadInst(instList, handle.getPrev(), index, pushVal);
				}
				// }
			}
		}

		// setPositions(true) checks whether jump handles 
		// are all within the current method
		instList.setPositions(true);

		// set max stack/local
		methodGen.setMaxStack();
		methodGen.setMaxLocals();

		// generate the new method with replaced iconst
		Method newMethod = methodGen.getMethod();
		// replace the method in the original class
		cgen.replaceMethod(method, newMethod);
	}

	public void optimize() {
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();
		cgen.setMajor(50);
		// Implement your optimization here
		Method[] methods = cgen.getMethods();
		for (Method m: methods) {
				optimizeMethod(cgen, cpgen, m);
		}
				
		this.optimized = cgen.getJavaClass();
	}

	
	public void write(String optimisedFilePath)	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}