import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.h2.Driver
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.*
import org.jooq.meta.jaxb.Target
import java.sql.Connection

class DaoCodeGenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.tasks.register("dao-code-gen") {
            doLast {
                val conn = Driver().connect("jdbc:h2:mem:test", null)
                conn.createStatement().use { stmt ->
                    stmt.execute("drop all OBJECTS")
                    stmt.execute("set schema PUBLIC")
                }

                runLiquibase(project, conn)

                val jdbc = Jdbc()
                    .withDriver("org.h2.Driver")
                    .withUrl("jdbc:h2:mem:test")

                val database = Database()
                    .withExcludes("DATABASECHANGELOG|DATABASECHANGELOGLOCK")
                    .withInputSchema("PUBLIC")
                    .withForcedTypes(listForcedTypes(project))

                val generate = Generate()
                    .withPojos(true)
                    .withDaos(true)

                val target = Target()
                    .withPackageName("io.elephantchess.db.dao.codegen")
                    .withDirectory("${project.projectDir}/webapp-dao/build/jooq")

                val generator = Generator()
                    .withDatabase(database)
                    .withGenerate(generate)
                    .withTarget(target)

                GenerationTool.generate(
                    Configuration()
                        .withJdbc(jdbc)
                        .withGenerator(generator),
                )

                conn.close()
            }
        }
    }
}

private fun runLiquibase(project: Project, conn: Connection) {
    val resourceAccessor = DirectoryResourceAccessor(
        project.layout.projectDirectory.dir("webapp-dao-migration/src/main/resources").asFile,
    )
    val db = H2Database().apply {
        setConnection(JdbcConnection(conn))
    }

    val liquibase = Liquibase("liquibase-changelog-generation.xml", resourceAccessor, db)
    liquibase.update(Contexts())
    conn.commit()
}

private fun listForcedTypes(project: Project): List<ForcedType> {
    val forcedTypeMap = linkedMapOf<String, String>()
    project.file("jooq-type-mappings.txt").forEachLine { line ->
        val parts = line.split(",")
        val columnName = parts[0].trim()
        val typeName = parts[1].trim()
        forcedTypeMap[columnName] = typeName
    }

    val forcedTypes = forcedTypeMap
        .map { (key, value) ->
            ForcedType()
                .withIncludeExpression(Regex.escape(key))
                .withUserType(value)
                .withEnumConverter(true)
        }
        .toMutableList()

    forcedTypes += ForcedType()
        .withIncludeTypes("(?i:TIMESTAMP\\s+WITH\\s+TIME\\s+ZONE|TIMESTAMP\\([0-9]+\\)\\s+WITH\\s+TIME\\s+ZONE|TIMESTAMPTZ)")
        .withUserType("kotlin.time.Instant")
        .withConverter("io.elephantchess.db.codegen.OffsetDateTimeInstantConverter")

    forcedTypes += ForcedType()
        .withIncludeTypes("(?i:TIMESTAMP(?:\\([0-9]+\\))?(?:\\s+WITHOUT\\s+TIME\\s+ZONE)?|DATETIME(?:\\([0-9]+\\))?)")
        .withUserType("kotlin.time.Instant")
        .withConverter("io.elephantchess.db.codegen.LocalDateTimeInstantConverter")

    return forcedTypes.filterNotNull().toList()
}
