/*
  Copyright 2014 by Xiaomeng Ye
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/
package ec.gp.lambda.individualsForTesting;

import java.io.Serializable;

import ec.gp.lambda.IndexNode;
import ec.gp.lambda.LambdaIndividual;
import ec.gp.GPNode;
import ec.gp.lambda.*;
public class ExampleForLFloat extends LambdaIndividual implements Serializable{
	private static final long serialVersionUID = 1;
	public ExampleForLFloat(){
		super();
		LambdaTree tree = new LambdaTree();
		//(L(P(1,L(P(1, L(1))))))
		tree.child = new LNode();
		GPNode iter = tree.child;
		iter.children[0] = new PNode();
		iter = iter.children[0];
		iter.children[0] = new IndexNode(1);
		iter.children[1] = new LNode();
		iter = iter.children[1];
		iter.children[0] = new PNode();
		iter = iter.children[0];
		iter.children[0] = new IndexNode(1);
		iter.children[1] = new LNode();
		iter = iter.children[1];
		iter.children[0] = new IndexNode(1);

		this.trees[0] = tree;
	}
}
