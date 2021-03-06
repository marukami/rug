package com.atomist.project.archive

import com.atomist.project.generate.{EditorInvokingProjectGenerator, ProjectGenerator}
import com.atomist.project.review.ProjectReviewer
import com.atomist.project.{Executor, ProjectOperation}
import com.atomist.rug.kind.DefaultTypeRegistry
import com.atomist.rug.runtime._
import com.atomist.rug.runtime.js.{JavaScriptInvokingProjectEditor, JavaScriptOperationFinder}
import com.atomist.rug.runtime.rugdsl.{DefaultEvaluator, Evaluator, RugDrivenProjectEditor}
import com.atomist.rug.spi.TypeRegistry
import com.atomist.rug.{DefaultRugPipeline, EmptyRugFunctionRegistry, Import}
import com.atomist.source.{ArtifactSource, FileArtifact}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.Seq

/**
  * Reads an archive and extracts Atomist project operations.
  */
class ProjectOperationArchiveReader(
                                     atomistConfig: AtomistConfig = DefaultAtomistConfig,
                                     evaluator: Evaluator = new DefaultEvaluator(new EmptyRugFunctionRegistry),
                                     typeRegistry: TypeRegistry = DefaultTypeRegistry
                                   )
  extends LazyLogging {

  val oldInterpreterPipeline = new DefaultRugPipeline(typeRegistry, evaluator, atomistConfig)
  //val newPipeline = new CompilerChainPipeline(Seq(new RugTranspiler()))

  def findImports(startingProject: ArtifactSource): Seq[Import] = {
    oldInterpreterPipeline.parseRugFiles(startingProject).foldLeft(Nil: Seq[Import]) { (acc, rugProgram) => acc ++ rugProgram.imports }
  }

  // We skip any file with declare var atomist as we can't satisfy it here
  private val hasDeclareVarAtomist: FileArtifact => Boolean = f =>
    f.name.endsWith(".ts") && "declare[\\s]+var[\\s]+atomist".r.findAllMatchIn(f.content).nonEmpty

  def findOperations(startingProject: ArtifactSource,
                     namespace: Option[String],
                     otherOperations: Seq[ProjectOperation],
                     shouldSuppress: FileArtifact => Boolean = hasDeclareVarAtomist): Operations = {
    val fromTs = JavaScriptOperationFinder.fromTypeScriptArchive(startingProject)
    val fromOldPipeline = oldInterpreterPipeline.create(startingProject, namespace, otherOperations ++ fromTs)

    val operations = fromOldPipeline ++ fromTs

    val editors = operations collect {
      // TODO returning an editor that really is a generator is confusing
      case red: RugDrivenProjectEditor => red

        // TODO these can't be generators yet.
        // This is a hack to avoid breaking tests
      case ed: JavaScriptInvokingProjectEditor => ed
    }

    val generators = operations collect {
      case g: ProjectGenerator => g
      case red: RugDrivenProjectEditor if red.program.publishedName.isDefined =>
        // TODO want to pull up published name so it's not Rug only
        val project: ArtifactSource = removeAtomistTemplateContent(startingProject)
        // TODO remove blanks in the generator names; we need to have a proper solution for this
        val name = red.program.publishedName.get
        logger.info(s"Creating new generator with name $name")
        new EditorInvokingProjectGenerator(name, red, project)
    }

    val reviewers = operations collect {
      case r: ProjectReviewer => r
    }

    val executors = operations collect {
      case ed: Executor => ed
    }

    Operations(generators, editors, reviewers, executors)
  }

  def removeAtomistTemplateContent(startingProject: ArtifactSource): ArtifactSource = {
    startingProject.filter(d => !d.path.equals(atomistConfig.atomistRoot), f => true)
  }
}
