package comp207p.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

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
			if (handle.getInstruction() instanceof StoreInstruction) {
				int index = ((StoreInstruction) handle.getInstruction()).getIndex();
				if (storeIndex == index) count++;
			}	
			if (count > 1) return false;
		}
		return true;
	}

	private void convertLoadInst(InstructionList instList, InstructionHandle pushHandle, int storeIndex, Number pushVal) {
		InstructionHandle handle = pushHandle;
		while (handle != null) {
			if (handle.getInstruction() instanceof LoadInstruction) {
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
		deleteInst(instList, pushHandle.getNext());
		deleteInst(instList, pushHandle);
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
	
	private Number getArithmeticRes(ConstantPoolGen cpgen, InstructionHandle handle) {
		Number[] valueOnStack = getArithmeticVals(cpgen, handle);
		if (valueOnStack[0] == null || valueOnStack[1] == null) {
			return null;
		}
		else if (handle.getInstruction() instanceof DADD) {
			return ((double)valueOnStack[0] + (double)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof FADD) {
			return ((float)valueOnStack[0] + (float)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof IADD) {
			return ((int)valueOnStack[0] + (int)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof LADD) {
			return ((long)valueOnStack[0] + (long)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof DDIV) {
			return ((double)valueOnStack[0] / (double)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof FDIV) {
			return ((float)valueOnStack[0] / (float)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof IDIV) {
			return ((int)valueOnStack[0] / (int)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof LDIV) {
			return ((long)valueOnStack[0] / (long)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof DMUL) {
			return ((double)valueOnStack[0] * (double)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof FMUL) {
			return ((float)valueOnStack[0] * (float)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof IMUL) {
			return ((int)valueOnStack[0] * (int)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof LMUL) {
			return ((long)valueOnStack[0] * (long)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof DSUB) {
			return ((double)valueOnStack[0] - (double)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof FSUB) {
			return ((float)valueOnStack[0] - (float)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof ISUB) {
			return ((int)valueOnStack[0] - (int)valueOnStack[1]);
		}
		else if (handle.getInstruction() instanceof LSUB) {
			return ((long)valueOnStack[0] - (long)valueOnStack[1]);
		}
		return null;
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
		for (InstructionHandle handle : instList.getInstructionHandles())	{
			if (handle.getInstruction() instanceof ArithmeticInstruction) {
				Number result = getArithmeticRes(cpgen, handle);
				if (result != null) {
					for (InstructionHandle print : instList.getInstructionHandles()) {
          	 System.out.println(print.getInstruction());
          }
          System.out.println("");
					deleteInst(instList, handle.getPrev());
					deleteInst(instList, handle.getPrev());
					int cpIndex = 0;
          if (result instanceof Integer) {
            cpIndex = cpgen.addInteger((int) result);
            instList.insert(handle, new LDC(cpIndex));
            instList.setPositions();
          }
          else if (result instanceof Float) {
            cpIndex = cpgen.addFloat((float) result);
            instList.insert(handle, new LDC(cpIndex));
            instList.setPositions();
          }
          else if (result instanceof Double) {
            cpIndex = cpgen.addDouble((double) result);
            instList.insert(handle, new LDC2_W(cpIndex));
            instList.setPositions();
          }
          else if (result instanceof Long) {
            cpIndex = cpgen.addLong((Long) result);
            instList.insert(handle, new LDC2_W(cpIndex));
            instList.setPositions();
          }
          deleteInst(instList, handle);
        }
			}
			else if (handle.getInstruction() instanceof StoreInstruction) {
				int index = ((StoreInstruction) handle.getInstruction()).getIndex();
				if (checkConstantVar(instList, index)) {
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
				}
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