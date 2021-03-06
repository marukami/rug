import {Project} from "../model/Core"
import {Parameter, RugOperation} from "./RugOperation"


/**
 * Top level interface for all project generators
 */
interface ProjectGenerator extends RugOperation{
    populate(emptyProject: Project, projectName: string, args: Object)
}

/**
 * The commonest case. We want to customize a new project
 */
abstract class CustomizingProjectGenerator implements ProjectGenerator {
    abstract name: string
    abstract description: string
    populate(emptyProject: Project, projectName: string, ...params: string[]) {
        emptyProject.copyEditorBackingFilesPreservingPath("")
        this.customize(emptyProject, projectName, params)
    }

   abstract customize(project: Project, projectName: string, params: string[]): void
}

export {ProjectGenerator}
export {CustomizingProjectGenerator}
