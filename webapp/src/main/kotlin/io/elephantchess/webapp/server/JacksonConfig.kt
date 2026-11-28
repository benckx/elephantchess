package io.elephantchess.webapp.server

import tools.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS
import tools.jackson.databind.cfg.EnumFeature.READ_ENUMS_USING_TO_STRING
import tools.jackson.databind.cfg.EnumFeature.WRITE_ENUMS_USING_TO_STRING
import tools.jackson.databind.json.JsonMapper

/**
 * Jackson 3 reads and writes enums using `toString()` by default, which breaks enums
 * overriding it (e.g. `Engine`). The frontend expects enum names, as with Jackson 2.
 */
fun JsonMapper.Builder.configureEnums(): JsonMapper.Builder =
    configure(ACCEPT_CASE_INSENSITIVE_ENUMS, true)
        .configure(READ_ENUMS_USING_TO_STRING, false)
        .configure(WRITE_ENUMS_USING_TO_STRING, false)
