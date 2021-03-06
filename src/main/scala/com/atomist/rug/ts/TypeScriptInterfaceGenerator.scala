package com.atomist.rug.ts

import java.io.PrintWriter

import com.atomist.param.Parameter
import com.atomist.project.common.InvalidParametersException
import com.atomist.project.common.support.ProjectOperationParameterSupport
import com.atomist.project.edit._
import com.atomist.project.generate.ProjectGenerator
import com.atomist.project.{ProjectOperationArguments, SimpleProjectOperationArguments}
import com.atomist.rug.kind.DefaultTypeRegistry
import com.atomist.rug.spi._
import com.atomist.source.{ArtifactSource, FileArtifact, SimpleFileBasedArtifactSource, StringFileArtifact}
import com.atomist.util.Utils
import com.atomist.util.lang.TypeScriptGenerationHelper

import scala.collection.mutable.ListBuffer

object TypeScriptInterfaceGenerator extends App {

  val target = if (args.length < 1) "target/classes/user-model/model/Core.ts" else args.head
  val generator = new TypeScriptInterfaceGenerator

  val output = generator.generate(SimpleProjectOperationArguments("", Map(generator.OutputPathParam -> "Core.ts")))
  Utils.withCloseable(new PrintWriter(target))(_.write(output.allFiles.head.content))
  println(s"Written to $target")
}

/**
  * Generate types for documents
  *
  * @param typeRegistry Registry of known Rug Types.
  */
class TypeScriptInterfaceGenerator(
                                    typeRegistry: TypeRegistry = DefaultTypeRegistry,
                                    config: InterfaceGenerationConfig = InterfaceGenerationConfig()
                                  ) extends ProjectGenerator
  with ProjectEditor with ProjectOperationParameterSupport {

  val DefaultTemplateName = "ts.vm"

  val DefaultFilename = "model/Core.ts"

  val OutputPathParam = "output_path"

  val helper = new TypeScriptGenerationHelper()

  addParameter(Parameter(OutputPathParam, ".*").
    setRequired(false).
    setDisplayName("Path for created doc").
    setDefaultValue(DefaultFilename))

  @throws[InvalidParametersException](classOf[InvalidParametersException])
  override def generate(poa: ProjectOperationArguments): ArtifactSource = {
    val createdFile = emitInterfaces(poa)
    println(s"The content of ${createdFile.path} is\n${createdFile.content}")
    new SimpleFileBasedArtifactSource("Rug user model", createdFile)
  }

  private def shouldEmit(top: TypeOperation) =
    !(top.parameters.exists(_.parameterType.contains("FunctionInvocationContext")) || "eval".equals(top.name))

  private def emitDocComment(t: Typed): String = {
    s"""
       |/*
       | * ${t.description}
       | */""".stripMargin
  }

  private def emitDocComment(top: TypeOperation): String = {
    (for (p <- top.parameters)
      yield s"$indent//${p.name}: ${helper.javaTypeToTypeScriptType(p.parameterType)}")
      .mkString("\n")
  }

  private def emitParameter(t: Typed, top: TypeOperation, p: TypeParameter): String = {
    if (p.name.startsWith("arg"))
      System.err.println(s"WARNING: Parameter [${p.name}] on operation ${t.name}.${top.name} has no name annotation")

    s"${p.name}: ${helper.javaTypeToTypeScriptType(p.parameterType)}"
  }

  // Generate a string for the output for this type
  private def generateTyped(t: Typed): String = {
    val output = new StringBuilder("")
    output ++= emitDocComment(t)
    output ++= s"\ninterface ${t.name} extends TreeNode {${config.separator}"
    t.typeInformation match {
      case s: StaticTypeInformation =>
        for {
          op <- s.operations
          if shouldEmit(op)
        } {
          val comment = emitDocComment(op)
          val params =
            for (p <- op.parameters)
              yield emitParameter(t, op, p)

          output ++= s"$comment\n$indent${op.name}(${params.mkString(", ")}): ${helper.javaTypeToTypeScriptType(op.returnType)}"
          output ++= config.separator
        }
    }
    output ++= s"}${indent.dropRight(1)} // interface ${t.name}"
    output.toString
  }

  val typeSort: (Typed, Typed) => Boolean = (a, b) => a.name <= b.name

  private def emitInterfaces(poa: ProjectOperationArguments): FileArtifact = {
    val alreadyGenerated = ListBuffer.empty[Typed]

    val output: StringBuilder = new StringBuilder(config.licenseHeader)
    output ++= config.separator
    output ++= config.imports
    output ++= config.separator

    def generate(t: Typed): Unit = {
      output ++= generateTyped(t)
      output ++= config.separator
      alreadyGenerated.append(t)
    }

    val allTypes = typeRegistry.types.sortWith(typeSort)
    val unpublishedTypes = findUnpublishedTypes(allTypes)

    for {
      t <- allTypes
      if !alreadyGenerated.contains(t)
    } {

      println(s"Going to generate interface for type $t")
      generate(t)
    }

    output ++= "\n"
    for {t <- typeRegistry.types.sortWith(typeSort)} {
      output ++= s"export { ${t.name} }\n"
    }

    StringFileArtifact(
      poa.stringParamValue(OutputPathParam),
      output.toString())
  }

  // Find all the types that aren't published types but define methods
  private def findUnpublishedTypes(publishedTypes: Seq[Typed]): Seq[String] = {
    val allOperations: Seq[TypeOperation] = publishedTypes.map(_.typeInformation).flatMap {
      case st: StaticTypeInformation => st.operations
    }
    val types = allOperations.map(_.definedOn.getSimpleName).toSet
    println(s"types=${types.size}: ${types.mkString(",")}")

    val publishedTypeNames = publishedTypes.map(_.name).toSet
    println(s"publishedTypeNames=${publishedTypeNames.size}: ${publishedTypeNames.mkString(",")}")

    val unpublishedTypes = types -- publishedTypeNames
    val sortedUnpublishedTypes = unpublishedTypes.toSeq.sorted
    println(s"unpublishedTypes=${sortedUnpublishedTypes.size}: ${sortedUnpublishedTypes.mkString(",")}")
    sortedUnpublishedTypes
  //  Nil
  }

  private val indent = "    "

  override def modify(as: ArtifactSource, poa: ProjectOperationArguments): ModificationAttempt = {
    val createdFile = emitInterfaces(poa)
    val r = as + createdFile
    SuccessfulModification(r, impacts, "OK")
  }

  override def impacts: Set[Impact] = Set(ReadmeImpact)

  override def applicability(as: ArtifactSource): Applicability = Applicability.OK

  override def description: String = "Generate core Rug type info"

  override def name: String = "TypedDoc"

}

case class InterfaceGenerationConfig(
                                      indent: String = "    ",
                                      separator: String = "\n\n"
                                    )
  extends TypeScriptGenerationConfig {

  val imports: String =
    """
      |import {TreeNode} from '../tree/PathExpression'
      |import {ProjectContext} from '../operations/ProjectEditor' """.stripMargin

  val licenseHeader: String =
    """
      |/*
      | * Copyright 2015-2016 Atomist Inc.
      | *
      | * Licensed under the Apache License, Version 2.0 (the "License");
      | * you may not use this file except in compliance with the License.
      | * You may obtain a copy of the License at
      | *
      | *      http://www.apache.org/licenses/LICENSE-2.0
      | *
      | * Unless required by applicable law or agreed to in writing, software
      | * distributed under the License is distributed on an "AS IS" BASIS,
      | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      | * See the License for the specific language governing permissions and
      | * limitations under the License.
      | */""".stripMargin
}
