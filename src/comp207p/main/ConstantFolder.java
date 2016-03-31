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
						Number lastStackPush = ((BIPUSH) handle.getPrev().getInstruction()).getValue();
						System.out.println(handle.getPrev().getInstruction());
						System.out.println(lastStackPush);
					}
					else if (handle.getPrev().getInstruction() instanceof SIPUSH) {
						Number lastStackPush = ((SIPUSH) handle.getPrev().getInstruction()).getValue();
						System.out.println(handle.getPrev().getInstruction());
						System.out.println(lastStackPush);
					}
					else if (handle.getPrev().getInstruction() instanceof ICONST) {
						Number lastStackPush = ((ICONST) handle.getPrev().getInstruction()).getValue();
						System.out.println(handle.getPrev().getInstruction());
						System.out.println(lastStackPush);
					}
					else if (handle.getPrev().getInstruction() instanceof DCONST) {
						Number lastStackPush = ((DCONST) handle.getPrev().getInstruction()).getValue();
						System.out.println(handle.getPrev().getInstruction());
						System.out.println(lastStackPush);
					}
					else if (handle.getPrev().getInstruction() instanceof FCONST) {
						Number lastStackPush = ((FCONST) handle.getPrev().getInstruction()).getValue();
						System.out.println(handle.getPrev().getInstruction());
						System.out.println(lastStackPush);
					}
					else if (handle.getPrev().getInstruction() instanceof LCONST) {
						Number lastStackPush = ((LCONST) handle.getPrev().getInstruction()).getValue();
						System.out.println(handle.getPrev().getInstruction());
						System.out.println(lastStackPush);
					}
					else if (handle.getPrev().getInstruction() instanceof LDC) {
						Number lastStackPush = (Number) ((LDC) handle.getPrev().getInstruction()).getValue(cpgen);
						System.out.println(handle.getPrev().getInstruction());
						System.out.println(lastStackPush);
					}
					else if (handle.getPrev().getInstruction() instanceof LDC2_W) {
						Number lastStackPush = ((LDC2_W) handle.getPrev().getInstruction()).getValue(cpgen);
						System.out.println(handle.getPrev().getInstruction());
						System.out.println(lastStackPush);
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