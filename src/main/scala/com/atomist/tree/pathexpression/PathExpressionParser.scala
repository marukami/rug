package com.atomist.tree.pathexpression

import java.util.Objects

import com.atomist.source.StringFileArtifact
import com.atomist.tree.pathexpression.PathExpressionEngine._
import com.atomist.tree.content.text.TreeNodeOperations._
import com.atomist.tree.{ContainerTreeNode, TreeNode}
import com.atomist.util.scalaparsing.CommonTypesParser

/**
  * Scala parser combinator for path expressions
  */
trait PathExpressionParser extends CommonTypesParser {

  private def nodeName: Parser[String] = identifierRefString(Set(), PathProperty)

  private def nodeType: Parser[String] = identifierRefString(Set(), javaPackage)

  private def child: Parser[AxisSpecifier] = "/" ^^
    (s => Child)

  private def descendantOrSelf: Parser[AxisSpecifier] = "//" ^^
    (s => DescendantOrSelf)

  private def childTypeJump: Parser[ChildTypeJump] = "->" ~> nodeType ^^ {
    case p => ChildTypeJump(p)
  }

  private def nodeTypeTest: Parser[OfType] = ("*" | nodeName) ~ ":" ~ nodeType ^^ {
    case wildcard ~ _ ~ p => OfType(p, wildcard)
  }

  private def test(extracted: Any, value: Any, n: TreeNode): Boolean = {
    value.equals(extracted)
  }

  private def propertyTest: Parser[Predicate] = opt("@") ~ nodeName ~ EqualsToken ~ singleQuotedString ^^ {
    case at ~ prop ~ op ~ literal =>
      val f: TreeNode => Boolean = (at, prop) match {
        case (None, "name") =>
          n => test(n.nodeName, literal, n)
        case (None, "type") =>
          n => test(n.nodeType, literal, n)
        case (Some(at), propName) =>
          n => {
            n match {
              case ctn: ContainerTreeNode =>
                val extracted = ctn(prop)
                if (extracted.size == 1)
                  test(extracted.head, literal, n)
                else
                  false
              case _ => false
            }
          }
        case _ => throw new IllegalArgumentException(s"Cannot access property [$prop] with @=$at")
      }
      Predicate(s"$prop=$literal", (tn,_) => f(tn) )
  }

  private def nullLiteral: Parser[Object] = "null" ^^ (_ => null)

  private def integer: Parser[Integer] = decimalNumber ^^ (s => s.toInt)

  private def literal: Parser[Any] = nullLiteral | singleQuotedString | integer

  private def methodInvocationTest: Parser[Predicate] = "." ~> nodeName ~ args ~ EqualsToken ~ literal ^^ {
    case methodName ~ args ~ op ~ literal =>
      Predicate(s".$methodName", (n,among) => {
        val invoked = invokeMethod[Any](n, methodName, args)
        Objects.equals(literal, invoked)
      })
  }

  private def arg: Parser[String] = singleQuotedString

  private def args: Parser[Seq[String]] = "(" ~> repsep(arg, ",") <~ ")"

  private def booleanMethodInvocation: Parser[Predicate] = "." ~> nodeName ~ args ^^ {
    case methodName ~ args =>
      Predicate(s".$methodName", (n, among) => invokeMethod[Boolean](n, methodName, args))
  }

  private def index: Parser[Predicate] = integer ^^ {
    case n => new IndexPredicate(s"[$index]", n)
  }

  private def truePredicate: Parser[Predicate] = "true" ^^ (_ => TruePredicate)

  private def falsePredicate: Parser[Predicate] = "false" ^^ (_ => FalsePredicate)

  private def predicateTerm: Parser[Predicate] = methodInvocationTest | propertyTest | booleanMethodInvocation |
    truePredicate | falsePredicate |
    index

  private def negatedPredicate: Parser[Predicate] = "not" ~> "(" ~> predicateExpression <~ ")" ^^ {
    case pred => new NegationOf(pred)
  }

  private def logicalOp: Parser[String] = "and" | "or"

  private def predicateAnd: Parser[Predicate] = predicateTerm ~ logicalOp ~ predicateExpression ^^ {
    case a ~ "and" ~ b => a and b
    case a ~ "or" ~ b => a or b
  }

  private def predicateExpression: Parser[Predicate] = predicateAnd | negatedPredicate | predicateTerm

  private def predicate: Parser[Predicate] = PredicateOpen ~> predicateExpression <~ PredicateClose

  private def nodeNameTest: Parser[NodeTest] = nodeName ^^
    (s => NamedNodeTest(s))

  private def allNodes: Parser[NodeTest] = "*" ^^ (_ => All)

  private def nodeTest: Parser[NodeTest] = nodeTypeTest | nodeNameTest | allNodes

  private def self: Parser[AxisSpecifier] = "." ^^ (_ => Self)

  private def axis: Parser[AxisSpecifier] = self | descendantOrSelf | child | childTypeJump

  private def combine(preds: Seq[Predicate]): Option[Predicate] = preds match {
    case Nil => None
    case pred::Nil => Some(pred)
    case preds => Some(preds.head and combine(preds.tail).get)
  }

  private def locationStep: Parser[LocationStep] = axis ~ opt(nodeTest) ~ rep(predicate) ^^ {
    case a ~ Some(t) ~ preds => LocationStep(a, t, combine(preds))
    case a ~ None ~ preds => LocationStep(a, All, combine(preds))
  }

  def pathExpression: Parser[PathExpression] = opt(nodeTest) ~ rep(locationStep) ^^ {
    case None ~ steps => PathExpression(steps)
    case Some(nt) ~ steps =>
      val firstStep = LocationStep(Child, nt, None)
      PathExpression(firstStep +: steps)
  }

  def parsePathExpression(expr: String): PathExpression = {
    try {
      parseTo(StringFileArtifact("<input>", expr), phrase(pathExpression))
    } catch {
      case e: IllegalArgumentException =>
        throw new IllegalArgumentException(s"Path expression '$expr' is invalid: [${e.getMessage}]", e)
    }
  }
}

/**
  * Default implementation of PathExpressionParser. Import this
  * class for default conversion from Strings to path expressions. 
  */
object PathExpressionParser extends PathExpressionParser {

  implicit def parseString(expr: String): PathExpression = PathExpressionParser.parsePathExpression(expr)

}