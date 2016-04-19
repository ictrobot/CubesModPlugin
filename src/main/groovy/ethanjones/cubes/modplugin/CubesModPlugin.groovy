package ethanjones.cubes.modplugin

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

class CubesModPlugin implements Plugin<Project>{

    @Override
    void apply(Project project) {
        if (!project.plugins.hasPlugin(JavaPlugin.class)) throw new GradleException("'java' plugin must be applied")

        project.extensions.create("cubes", CubesModPluginExtension)

        project.task('modDex', dependsOn: project.tasks.getByName('jar'), description: "Creates .dex to run on android", group: "cubes") << {
            new File(project.buildDir, '/libs/').mkdirs()

            String androidSDKDir = project.cubes.androidSDKDir
            String androidBuildToolsVersion = getBuildToolsVersion(project)

            if (!new File("${androidSDKDir}/build-tools/${androidBuildToolsVersion}/").exists()) {
                throw new GradleException("You need build-tools ${androidBuildToolsVersion}")
            }
            String cmdExt = Os.isFamily(Os.FAMILY_WINDOWS) ? '.bat' : ''
            project.exec {
                commandLine "${androidSDKDir}/build-tools/${androidBuildToolsVersion}/dx${cmdExt}", '--dex',
                        "--output=${project.buildDir}/libs/mod.dex",
                        "--verbose",
                        "${project.buildDir}/libs/mod.jar"
            }
        }

        project.task('modProperties', description: "Creates mod properties file", group: "cubes") << {
            new File(project.buildDir, '/libs/').mkdirs()
            def props = new Properties()
            props.setProperty('modClass', project.cubes.modClass)
            props.setProperty('modName', project.cubes.modName)
            props.setProperty('modVersion', project.cubes.modVersion)
            PrintWriter printWriter = new File(project.buildDir, '/libs/mod.properties').newPrintWriter();
            props.store(printWriter, null)
            printWriter.close()
        }

        project.task('cm', type:Zip, dependsOn: [project.tasks.getByName('jar'), project.tasks.getByName('modDex'), project.tasks.getByName('modProperties')], description: "Builds Cubes cm file", group: "cubes") {
            destinationDir = new File(project.buildDir, '/libs/')
            // archiveName = project.cubes.modName + '.cm'      in afterEvaluate
            from(new File(project.buildDir, '/libs/mod.jar'))
            from(new File(project.buildDir, '/libs/mod.dex'))
            from(new File(project.buildDir, '/libs/mod.properties'))
            into('assets') {
                from(new File(project.cubes.assetsFolder))
            }

            outputs.upToDateWhen { false }
        }

        addMavenCentral(project)
        addMavenRepo(project, 'https://oss.sonatype.org/content/repositories/snapshots/')
        addMavenRepo(project, 'https://oss.sonatype.org/content/repositories/releases/')
        addMavenRepo(project, "http://ethanjones.me/maven/snapshots")
        addMavenRepo(project, "http://ethanjones.me/maven/releases")

        project.afterEvaluate {
            def version = project.cubes.cubesVersion
            def dep = project.dependencies.add("compile", "ethanjones.cubes:core:$version")
            project.configurations.compile.dependencies.add(dep)

            project.tasks.getByName("cm").archiveName = project.cubes.modName + '.cm'
        }

        project.tasks.withType(JavaCompile) {
            sourceCompatibility = '1.7'
            targetCompatibility = '1.7'
        }

        project.tasks.getByName('jar').archiveName = 'mod.jar'
    }

    static String getBuildToolsVersion(Project project) {
        File dir = new File(project.buildDir, "cubes")
        dir.mkdirs()
        File jar = project.configurations.compile.fileCollection{dep -> dep.group == 'ethanjones.cubes' && dep.name == 'core'}.first()
        FileTree tree = project.zipTree(jar)
        project.copy {
            from(tree) {
                includes ["core.properties"]
            }
            into dir
        }
        Properties properties = new Properties()
        FileInputStream fileInputStream = new FileInputStream(new File(dir, "core.properties"))
        properties.load(fileInputStream)
        fileInputStream.close()
        return properties["ANDROID_BUILD_TOOLS"]
    }

    static void addMavenRepo(Project project, final String url) {
        project.getRepositories().maven(new Action<MavenArtifactRepository>() {
                @Override
                public void execute(MavenArtifactRepository repo) {
                    repo.setUrl(url);
                }
        });
    }

    static void addMavenCentral(Project project) {
        project.getRepositories().add(project.getRepositories().mavenCentral())
    }
}

class CubesModPluginExtension {
    def String cubesVersion = ''
    def String modVersion = ''
    def String modClass = ''
    def String modName = ''
    def String assetsFolder = 'assets/'
    def String androidSDKDir = System.getenv("ANDROID_HOME")
}