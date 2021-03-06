
/**
   Returned when a PathExpression is evaluated
*/
interface Match<R,N> {

  root(): R 

  matches(): Array<N> 
}


class MatchImpl<R,N> implements Match<R,N> {

  private _root: R;
  private _matches: Array<N>;

  constructor(root: R, matches: Array<N>) {
    this._root = root
    this._matches = matches;
  }

  root(): R { return this._root; }
  matches(): Array<N> { return this._matches;}
}

class PathExpression<R,N> {

  constructor(public expression: string) {}

}

/**
 * All tree nodes offer these basic operations.
 */
interface TreeNode {

  nodeName(): String

  nodeType(): String

  value(): String

  update(newValue: String)

}

/*
  What we use to execute tree expressions
*/
interface PathExpressionEngine {

  evaluate<R extends TreeNode,N extends TreeNode>(root: R, expr: PathExpression<R,N>): Match<R,N>

  /**
  * Execute the given function on the nodes returned by the given path expression
  */
  with<N extends TreeNode>(root: TreeNode, expr: String,
            f: (n: N) => void): void


/**
 * Return a single match. Throw an exception otherwise.
 */
  scalar<R extends TreeNode,N extends TreeNode>(root: R, expr: PathExpression<R,N>): N

 /**
 * Cast the present node to the given type
 */
  as<N extends TreeNode>(root, name: string): N

  // Find the children of the current node of this time
  children<N extends TreeNode>(root, name: string): Array<N>
}

export {Match}
export {PathExpression}
export {PathExpressionEngine}
export {TreeNode}
