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
					try {
						InstructionHandle temp = handle;
						handle = handle.getNext();
						instList.redirectBranches(temp, temp.getPrev());
						instList.delete(temp);
						instList.setPositions();
					} catch (Exception e) {
						// do nothing
					}
				}
				else {
					handle = handle.getNext();
				}
			}
			else {
				handle = handle.getNext();
			}
		}
		try {
			instList.redirectBranches(pushHandle.getNext(), pushHandle);
			instList.delete(pushHandle.getNext());
			instList.setPositions(true);
		} catch (Exception e) {
			// do nothing
		}
		try {
			instList.redirectBranches(pushHandle, pushHandle.getPrev());
			instList.delete(pushHandle);
			instList.setPositions(true);
		} catch (Exception e) {
			// do nothing
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
		for (InstructionHandle handle : instList.getInstructionHandles())	{
			if (handle.getInstruction() instanceof StoreInstruction) {
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