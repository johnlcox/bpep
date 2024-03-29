package no.bekk.boss.bpep.generator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class BuilderGenerator implements Generator {

	public void generate(ICompilationUnit cu, boolean createBuilderConstructor,
			boolean formatSource, List<IField> fields, boolean addValidation) {
		try {
			IBuffer buffer = cu.getBuffer();
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);

			pw.println();
			pw.println("public static class Builder {");

			IType clazz = cu.getTypes()[0];

			int pos = clazz.getSourceRange().getOffset()
					+ clazz.getSourceRange().getLength() - 1;

			createFieldDeclarations(pw, fields);

			createBuilderMethods(pw, fields);
			if (createBuilderConstructor) {
				createPrivateBuilderConstructor(pw, clazz, fields);
				pw.println("}");
			} else {
				createClassBuilderConstructor(pw, clazz, fields);
				pw.println("}");
				createClassConstructor(pw, clazz, fields, addValidation);
			}

			if (formatSource) {
				buffer.replace(pos, 0, sw.toString());
				String builderSource = buffer.getContents();

				TextEdit text = ToolFactory.createCodeFormatter(null).format(
						CodeFormatter.K_COMPILATION_UNIT, builderSource, 0,
						builderSource.length(), 0, "\n");
				// text is null if source cannot be formatted
				if (text != null) {
					Document simpleDocument = new Document(builderSource);
					text.apply(simpleDocument);
					buffer.setContents(simpleDocument.get());
				}
			} else {
				buffer.replace(pos, 0, sw.toString());
			}
			
			if (addValidation) {
				addValidationImports(cu);
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		} catch (MalformedTreeException e) {
			e.printStackTrace();
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	private void createClassConstructor(PrintWriter pw, IType clazz,
			List<IField> fields, boolean addValidation)
			throws JavaModelException {
		String clazzName = clazz.getElementName();
		pw.println("private " + clazzName + "(Builder builder){");
		for (IField field : fields) {
			pw.println("this." + getName(field) + "=builder." + getName(field)
					+ ";");
		}

		if (addValidation) {
			addClassValidation(pw, clazzName);
		}

		pw.println("}");
	}

	private void createClassBuilderConstructor(PrintWriter pw, IType clazz,
			List<IField> fields) {
		String clazzName = clazz.getElementName();
		pw.println("public " + clazzName + " build(){");
		pw.println("return new " + clazzName + "(this);\n}");
	}

	private void createPrivateBuilderConstructor(PrintWriter pw, IType clazz,
			List<IField> fields) {
		String clazzName = clazz.getElementName();
		String clazzVariable = clazzName.substring(0, 1).toLowerCase()
				+ clazzName.substring(1);
		pw.println("public " + clazzName + " build(){");
		pw.println(clazzName + " " + clazzVariable + "=new " + clazzName
				+ "();");
		for (IField field : fields) {
			String name = getName(field);
			pw.println(clazzVariable + "." + name + "=" + name + ";");
		}
		pw.println("return " + clazzVariable + ";\n}");
	}

	private void addClassValidation(PrintWriter pw, String clazzName) {
		pw.println();
		pw.println("ValidatorFactory factory = Validation.buildDefaultValidatorFactory();");
		pw.println("Set<ConstraintViolation<" + clazzName
				+ ">> violations = factory.getValidator().validate(this);");
		pw.println("if (violations.size() > 0) {");
		pw.println("        ConstraintViolation<" + clazzName
				+ "> firstViolation = violations.iterator().next();");
		pw.println("        throw new IllegalArgumentException(firstViolation.getPropertyPath() + \" \" + firstViolation.getMessage());");
		pw.println("}");
	}
	
	private void addValidationImports(ICompilationUnit compilationUnit) throws JavaModelException {
		compilationUnit.createImport("java.util.Set", null, null);
		compilationUnit.createImport("javax.validation.ConstraintViolation", null, null);
		compilationUnit.createImport("javax.validation.Validation", null, null);
		compilationUnit.createImport("javax.validation.ValidatorFactory", null, null);
	}

	private void createBuilderMethods(PrintWriter pw, List<IField> fields)
			throws JavaModelException {
		for (IField field : fields) {
			String name = getName(field);
			String type = getType(field);
			pw.println("public Builder " + name + "(" + type + " " + name
					+ ") {");
			pw.println("this." + name + "=" + name + ";");
			pw.println("return this;\n}");
		}
	}

	private void createFieldDeclarations(PrintWriter pw, List<IField> fields)
			throws JavaModelException {
		for (IField field : fields) {
			pw.println("private " + getType(field) + " " + getName(field) + ";");
		}
	}

	public String getName(IField field) {
		return field.getElementName();
	}

	public String getType(IField field) {
		try {
			return Signature.toString(field.getTypeSignature());
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return null;
	}

	public List<IField> findAllFIelds(ICompilationUnit compilationUnit) {
		List<IField> fields = new ArrayList<IField>();
		try {
			IType clazz = compilationUnit.getTypes()[0];

			for (IField field : clazz.getFields()) {
				int flags = field.getFlags();
				boolean notStatic = !Flags.isStatic(flags);
				if (notStatic) {
					fields.add(field);
				}
			}

		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return fields;
	}

}
