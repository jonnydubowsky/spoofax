package org.strategoxt.imp.runtime.stratego.adapter;

import org.spoofax.interpreter.terms.IStrategoString;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermPrinter;
import org.strategoxt.imp.runtime.parser.ast.StringAstNode;

public class WrappedAstNodeString extends WrappedAstNode implements IStrategoString {

	private final StringAstNode wrappee;
	
	protected WrappedAstNodeString(WrappedAstNodeFactory factory, StringAstNode node) {
		super(factory, node);
		this.wrappee = node;
	}

	public String stringValue() {
		return wrappee.getValue();
	}
	
	public IStrategoTerm[] getArguments() {
		return getAllSubterms();
	}

    public void prettyPrint(ITermPrinter pp) {
    	pp.print("\"");
    	pp.print(stringValue().replace("\\", "\\\\").replace("\"", "\\\""));
    	pp.print("\"");
    	printAnnotations(pp);
    }

	public int getTermType() {
		return IStrategoTerm.STRING;
	}

	@Override
	protected boolean doSlowMatch(IStrategoTerm second, int commonStorageType) {
		return second.getTermType() == IStrategoTerm.STRING
			&& ((IStrategoString) second).stringValue().equals(stringValue())
			&& second.getAnnotations().equals(getAnnotations());
	}
	
	@Override
	public int hashFunction() {
		return stringValue().hashCode();
	}
}
