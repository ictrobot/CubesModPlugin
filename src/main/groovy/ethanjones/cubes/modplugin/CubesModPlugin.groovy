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
                throw new GradleException("Android sdk ${androidSDKDir} does not have build-tools ${androidBuildToolsVersion}")
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

        project.task('cm', type:Zip, description: "Builds Cubes cm file", group: "cubes") {
            destinationDir = new File(project.buildDir, '/libs/')
            // archiveName = project.cubes.modName + '.cm'      in afterEvaluate
            if (project.cubes.buildDesktop) {
                from(new File(project.buildDir, '/libs/mod.jar'))
            }
            if (project.cubes.buildAndroid) {
                from(new File(project.buildDir, '/libs/mod.dex'))
            }
            from(new File(project.buildDir, '/libs/mod.properties'))
            into('assets') {
                from(new File(project.cubes.assetsFolder))
            }
            into('json') {
                from(new File(project.cubes.jsonFolder))
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

            int[] versionArray = version.tokenize('.- ')[0..<3]*.toInteger()
            if (versionArray.length != 3) throw new GradleException('Invalid cubes version')

            if (project.cubes.buildAndroid && !project.cubes.forceAndroid && project.cubes.buildDesktop && !(versionArray[0] <= 0 && versionArray[1] <= 0 && versionArray[2] < 5)) {
                project.cubes.buildAndroid = false
            }

            def dep = project.dependencies.add("compile", "ethanjones.cubes:core:$version")
            project.configurations.compile.dependencies.add(dep)

            def cm = project.tasks.getByName("cm")
            cm.archiveName = project.cubes.modName + '.cm'

            cm.dependsOn.add(project.tasks.getByName('modProperties'))
            if (project.cubes.buildAndroid) cm.dependsOn.add(project.tasks.getByName('modDex'))
            if (project.cubes.buildDesktop) cm.dependsOn.add(project.tasks.getByName('jar'))
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
    def String jsonFolder = 'json/'
    def String androidSDKDir = System.getenv("ANDROID_HOME")
    def boolean buildAndroid = true
    def boolean forceAndroid = false
    def boolean buildDesktop = true
}
