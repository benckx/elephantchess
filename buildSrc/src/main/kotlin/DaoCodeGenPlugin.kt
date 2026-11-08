import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.h2.Driver
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import java.sql.Connection

class DaoCodeGenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("dao-code-gen") {
            doLast {
                val conn: Connection = Driver().connect("jdbc:h2:mem:test", null)

                conn.createStatement().use { stmt ->
                    stmt.execute("drop all OBJECTS")
                    stmt.execute("set schema PUBLIC")
                }

                val resourceAccessor = DirectoryResourceAccessor(
                    project.layout.projectDirectory.dir("webapp-dao-migration/src/main/resources").asFile,
                )
                val db = H2Database().apply {
                    setConnection(JdbcConnection(conn))
                }

                val liquibase = Liquibase("liquibase-changelog-generation.xml", resourceAccessor, db)
                liquibase.update(Contexts())
                conn.commit()

                val jdbc = Jdbc()
                    .withDriver("org.h2.Driver")
                    .withUrl("jdbc:h2:mem:test")

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
                            .withIncludeExpression(key)
                            .withUserType(value)
                            .withEnumConverter(true)
                    }
                    .toMutableList()

                forcedTypes += ForcedType()
                    .withIncludeTypes("(?i:TIMESTAMP\\s+WITH\\s+TIME\\s+ZONE|TIMESTAMP\\([0-9]+\\)\\s+WITH\\s+TIME\\s+ZONE|TIMESTAMPTZ)")
                    .withUserType("kotlin.time.Instant")
                    .withConverter("io.elephantchess.db.codegen.OffsetDateTimeInstantConverter")

                val database = Database()
                    .withExcludes("DATABASECHANGELOG|DATABASECHANGELOGLOCK")
                    .withInputSchema("PUBLIC")
                    .withForcedTypes(forcedTypes)

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
