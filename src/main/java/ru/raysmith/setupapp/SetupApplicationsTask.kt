package ru.raysmith.setupapp

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class SetupApplicationsTask : DefaultTask() {

    @get:Input
    abstract val envs: SetProperty<String>

    @get:Input
    abstract val sourceSets: SetProperty<String>

    @get:Input
    abstract val env: Property<String>

    @get:Optional
    @get:Input
    abstract val prod: Property<Boolean>

    // Директории ресурсов зависимых модулей (src/<ss>/resources)
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceRoots: ConfigurableFileCollection

    // Выходы задач webpack (опционально)
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val webpackFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    protected abstract val fs: FileSystemOperations

    @TaskAction
    fun run() {
        logger.info("[SetupApplicationsTask] Start for env=${env.get()}")
        logger.info("[SetupApplicationsTask] sourceSets: ${sourceSets.get()}")
        logger.info("[SetupApplicationsTask] resourceRoots: ${resourceRoots.files.map { it.path }}")
        logger.info("[SetupApplicationsTask] webpackFiles: ${webpackFiles.files.map { it.path }}")

        val outDir = outputDir.get().asFile
        outDir.mkdirs()
        fs.delete { delete(outDir) }

        val currentEnv = env.get()
        val otherEnvs = envs.get().filter { it != currentEnv }.toSet()

        // Копируем ресурсы из перечисленных корней
        resourceRoots.files
            .filter(File::exists)
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val rel = file.relativeTo(root).invariantSeparatorsPath
                        val segments = rel.split('/')

                        // Пропускаем чужие окружения из корня resources
                        if (segments.isNotEmpty() && segments[0] in otherEnvs) return@forEach

                        // Для текущего окружения "dev" или "prod" убираем первый сегмент
                        val relOut = if (segments.isNotEmpty() && segments[0] == currentEnv) {
                            segments.drop(1).joinToString("/")
                        } else {
                            rel
                        }

                        val dest = outputDir.get().file(relOut).asFile
                        if (!dest.exists()) {
                            logger.info("Add ${file.path} -> ${dest.parentFile.path}")
                            fs.copy {
                                from(file)
                                into(dest.parentFile)
                                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                            }
                        } else {
                            logger.info("[SetupApplicationsTask] Skip ${file.path}: already exists")
                        }
                    }
            }

        // Копируем выходные файлы webpack (если есть)
        webpackFiles.files
            .filter(File::exists)
            .forEach { wf ->
                val dest = outputDir.get().file(wf.name).asFile
                if (!dest.exists()) {
                    logger.info("Add ${wf.path} -> ${dest.parentFile.path} (webpack)")
                    fs.copy {
                        from(wf)
                        into(dest.parentFile)
                        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    }
                } else {
                    logger.info("[SetupApplicationsTask] Skip ${wf.path}: already exists")
                }
            }
    }
}