package ethanjones.cubes.modplugin

import com.android.dx.command.dexer.Main
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

class CubesModPlugin implements Plugin<Project>{

    public static final String CLIENT_CLASS = "ethanjones.cubes.core.platform.desktop.ClientLauncher";
    public static final String SERVER_CLASS = "ethanjones.cubes.core.platform.desktop.ServerLauncher";

    @Override
    void apply(Project project) {
        if (!project.plugins.hasPlugin(JavaPlugin.class)) throw new GradleException("'java' plugin must be applied")

        def runClientSourceSet = project.sourceSets.create('cubesRunClient')
        def runClientCompileConfiguration = project.configurations.getByName(runClientSourceSet.compileConfigurationName)

        def runServerSourceSet = project.sourceSets.create('cubesRunServer')
        def runServerCompileConfiguration = project.configurations.getByName(runServerSourceSet.compileConfigurationName)

        project.extensions.create("cubes", CubesModPluginExtension)

        project.task('modDex', dependsOn: project.tasks.getByName('jar'), description: "Creates .dex to run on android", group: "cubes") {
            doLast {
                new File(project.buildDir, '/libs/').mkdirs()

                Main.Arguments arguments = new Main.Arguments()
                arguments.parse("--output=${project.buildDir}/libs/mod.dex", "${project.buildDir}/libs/mod.jar")
                int result = Main.run(arguments)
                if (result != 0) throw new GradleException("Failed to convert jar to dex [" + result + "]")
            }
        }

        project.task('modProperties', description: "Creates mod properties file", group: "cubes") {
            doLast {
                new File(project.buildDir, '/libs/').mkdirs()
                def props = new Properties()
                props.setProperty('modClass', project.cubes.modClass)
                props.setProperty('modName', project.cubes.modName)
                props.setProperty('modVersion', project.cubes.modVersion)
                PrintWriter printWriter = new File(project.buildDir, '/libs/mod.properties').newPrintWriter();
                props.store(printWriter, null)
                printWriter.close()
            }
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

        project.task('runClient', description: "Runs Cubes Client", group: "cubes", dependsOn: [project.tasks.getByName("cm")]) {
            doLast {
                new File(project.buildDir.absolutePath + "/run/client").mkdirs()
                project.javaexec{
                    args = ["--mod", project.tasks.getByName('cm').archivePath, *project.cubes.runClientArguments]
                    classpath = project.files{ runClientCompileConfiguration.files }
                    main = CLIENT_CLASS
                    maxHeapSize = project.cubes.runClientHeapSize
                    workingDir = project.buildDir.absolutePath + "/run/client"
                }
            }
        }

        project.task('runServer', description: "Runs Cubes Server", group: "cubes", dependsOn: [project.tasks.getByName("cm")]) {
            doLast {
                new File(project.buildDir.absolutePath + "/run/server").mkdirs()
                project.javaexec{
                    args = ["--mod", project.tasks.getByName('cm').archivePath, *project.cubes.runServerArguments]
                    classpath = project.files{ runServerCompileConfiguration.files }
                    main = SERVER_CLASS
                    maxHeapSize = project.cubes.runServerHeapSize
                    workingDir = project.buildDir.absolutePath + "/run/server"
                }
            }
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

            def clientDep = project.dependencies.add(runClientCompileConfiguration.name, "ethanjones.cubes:client:$version")
            runClientCompileConfiguration.dependencies.add(clientDep)

            def serverDep = project.dependencies.add(runServerCompileConfiguration.name, "ethanjones.cubes:server:$version")
            runServerCompileConfiguration.dependencies.add(serverDep)

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

    static void addMavenRepo(Project project, final String url) {
        project.getRepositories().maven(new Action<MavenArtifactRepository>() {
                @Override
                void execute(MavenArtifactRepository repo) {
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

    def String runClientHeapSize = '2G'
    def List runClientArguments = []
    def String runServerHeapSize = '2G'
    def List runServerArguments = []

    def boolean buildAndroid = false
    def boolean buildDesktop = true
}
